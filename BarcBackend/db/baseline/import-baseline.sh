#!/usr/bin/env bash
#
# 在目标数据库导入「结构 + Flyway 迁移历史」基线，用于初始化新环境。
#
# 用途：
#   配合 export-baseline.ps1 生成的两个文件，把某个已迁移到最新版本的环境
#   复制成一个干净的新库（只有表结构与迁移历史，没有业务数据）。
#
# 安全约束：
#   1、脚本只创建数据库并导入结构，不删除、不覆盖任何已有数据；
#   2、若目标库已存在且非空，脚本会直接拒绝执行（避免误覆盖生产数据）；
#   3、密码通过交互输入并写入权限 600 的临时配置文件，用完立即删除，
#      不会出现在命令历史或进程列表中。
#
# 用法：
#   ./import-baseline.sh <01-schema.sql> <02-flyway-history.sql> [库名] [用户名] [主机] [端口]
#
# 示例：
#   ./import-baseline.sh ./01-schema.sql ./02-flyway-history.sql ba-prod barc_user 127.0.0.1 3306

set -euo pipefail

# ---------- 0. 行尾自愈 ----------
# 从 Windows 复制或在编辑器里粘贴的脚本可能带 CRLF 行尾，
# 这会让 bash 报 "bad interpreter"，或让变量值末尾多出 \r 而引发难以定位的错误。
# 这里检测并就地修正，随后用 bash 重新执行自身。
if [ -f "$0" ]; then
    self_bytes=$(wc -c < "$0" | tr -d ' ')
    stripped_bytes=$(tr -d '\r' < "$0" | wc -c | tr -d ' ')
    if [ "$self_bytes" != "$stripped_bytes" ]; then
        echo "[提示] 检测到 Windows CRLF 行尾，正在自动修正本脚本..."
        if command -v sed >/dev/null 2>&1; then
            sed -i 's/\r$//' "$0"
            echo "[提示] 修正完成，重新执行。"
            exec bash "$0" "$@"
        fi
        echo "错误：脚本含 CRLF 行尾且系统没有 sed，请先执行：" >&2
        echo "  tr -d '\\r' < \"$0\" > import-baseline-fixed.sh && bash import-baseline-fixed.sh $*" >&2
        exit 1
    fi
fi

SCHEMA_FILE="${1:-}"
HISTORY_FILE="${2:-}"
DB_NAME="${3:-ba-prod}"
DB_USER="${4:-}"
DB_HOST="${5:-127.0.0.1}"
DB_PORT="${6:-3306}"

# ---------- 参数校验 ----------
if [[ -z "$SCHEMA_FILE" || -z "$HISTORY_FILE" ]]; then
    echo "用法: $0 <01-schema.sql> <02-flyway-history.sql> [库名] [用户名] [主机] [端口]" >&2
    exit 1
fi
for f in "$SCHEMA_FILE" "$HISTORY_FILE"; do
    if [[ ! -s "$f" ]]; then
        echo "错误：文件不存在或为空 -> $f" >&2
        exit 1
    fi
done
if ! command -v mysql >/dev/null 2>&1; then
    echo "错误：未找到 mysql 客户端，请先安装或加入 PATH。" >&2
    exit 1
fi

# 未显式提供用户名时交互询问，避免把账号写死在脚本里。
if [[ -z "$DB_USER" ]]; then
    read -rp "生产库用户名: " DB_USER
fi

# ---------- 收集密码到临时配置文件 ----------
# 使用 --defaults-extra-file 而不是命令行传密码，避免密码泄漏到 ps / history。
umask 077
CNF_FILE="$(mktemp)"
cleanup() { rm -f "$CNF_FILE"; }
trap cleanup EXIT

read -rsp "生产库密码: " DB_PASSWORD
echo
{
    echo "[client]"
    echo "user=${DB_USER}"
    echo "password=${DB_PASSWORD}"
} > "$CNF_FILE"
unset DB_PASSWORD

MYSQL=(mysql --defaults-extra-file="$CNF_FILE" "--host=$DB_HOST" "--port=$DB_PORT" --default-character-set=utf8mb4)

echo "目标: ${DB_HOST}:${DB_PORT}  库: ${DB_NAME}  用户: ${DB_USER}"

# ---------- 1. 目标库必须不存在或为空 ----------
echo "[1/4] 检查目标库状态..."
EXISTS="$("${MYSQL[@]}" --batch --skip-column-names -e \
    "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='${DB_NAME}';")"

if [[ "$EXISTS" != "0" ]]; then
    TABLE_COUNT="$("${MYSQL[@]}" --batch --skip-column-names -e \
        "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='${DB_NAME}';")"
    if [[ "$TABLE_COUNT" != "0" ]]; then
        echo "错误：目标库 ${DB_NAME} 已存在且包含 ${TABLE_COUNT} 张表。" >&2
        echo "本脚本只用于初始化空库。若确需重建，请先人工备份并清空该库。" >&2
        exit 1
    fi
    echo "      目标库已存在但为空，将继续导入。"
else
    echo "      目标库不存在，开始创建。"
    "${MYSQL[@]}" -e \
        "CREATE DATABASE \`${DB_NAME}\` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
fi

# ---------- 2. 导入表结构 ----------
echo "[2/4] 导入表结构（$SCHEMA_FILE）..."
"${MYSQL[@]}" "$DB_NAME" < "$SCHEMA_FILE"

# ---------- 3. 导入 Flyway 迁移历史 ----------
echo "[3/4] 导入 Flyway 迁移历史（$HISTORY_FILE）..."
"${MYSQL[@]}" "$DB_NAME" < "$HISTORY_FILE"

# ---------- 4. 校验 ----------
echo "[4/4] 校验导入结果..."
TABLE_COUNT="$("${MYSQL[@]}" --batch --skip-column-names -e \
    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='${DB_NAME}';")"
MAX_VERSION="$("${MYSQL[@]}" --batch --skip-column-names "$DB_NAME" -e \
    "SELECT COALESCE(MAX(CAST(version AS UNSIGNED)), -1) FROM flyway_schema_history WHERE success = 1;")"
FAILED_COUNT="$("${MYSQL[@]}" --batch --skip-column-names "$DB_NAME" -e \
    "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0;")"

echo
echo "========== 导入完成 =========="
echo "表数量        : ${TABLE_COUNT}"
echo "Flyway 最高版本: ${MAX_VERSION}"
echo "失败迁移记录  : ${FAILED_COUNT}"
echo

if [[ "$FAILED_COUNT" != "0" ]]; then
    echo "警告：迁移历史中存在失败记录，应用启动时 Flyway 校验会报错。" >&2
    exit 1
fi

cat <<'EOF'
下一步：
1、用目标环境的 application-prod.yaml 指向该库（默认 127.0.0.1:3306）；
2、启动后端，日志应出现「Current version of schema ...: <版本>」且没有「Migrating」动作；
3、确认无误后再对外提供服务。
EOF
