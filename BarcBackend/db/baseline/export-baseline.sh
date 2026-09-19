#!/usr/bin/env bash
#
# 从源库（开发库）导出「表结构 + Flyway 迁移历史」，生成初始化新环境用的基线文件。
#
# 适用场景：
#   在装有 MySQL 客户端的 Linux 服务器上执行，直接对远程开发库做只读导出，
#   然后配合 import-baseline.sh 在同一台机器上完成新库初始化，无需中转文件。
#
# 安全约束：
#   1、只做只读导出，不会修改源库；
#   2、通过 --no-data 排除全部业务数据，只保留表结构与迁移历史；
#   3、密码通过交互输入并写入权限 600 的临时配置文件，用完立即删除，
#      不会出现在命令历史或进程列表中。
#
# 用法：
#   ./export-baseline.sh <源主机> <端口> <账号> <源库名> [输出目录]
#
# 示例：
#   ./export-baseline.sh miaoyu.asia 51001 ba ba ./out

set -euo pipefail

# ---------- 0. 行尾自愈 ----------
# 从 Windows 复制或在编辑器里粘贴的脚本可能带 CRLF 行尾，
# 这会让 bash 报 "bad interpreter" 或让变量值末尾多出 \r 而引发难以定位的错误。
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
        echo "  tr -d '\\r' < \"$0\" > export-baseline-fixed.sh && bash export-baseline-fixed.sh $*" >&2
        exit 1
    fi
fi

SOURCE_HOST="${1:-}"
SOURCE_PORT="${2:-}"
SOURCE_USER="${3:-}"
SOURCE_DB="${4:-}"
OUTPUT_DIR="${5:-./out}"

# ---------- 1. 参数与前置检查 ----------
if [[ -z "$SOURCE_HOST" || -z "$SOURCE_PORT" || -z "$SOURCE_USER" || -z "$SOURCE_DB" ]]; then
    echo "用法: $0 <源主机> <端口> <账号> <源库名> [输出目录]" >&2
    exit 1
fi
if ! command -v mysqldump >/dev/null 2>&1; then
    echo "错误：未找到 mysqldump。请安装 MySQL 客户端，或改用 Windows 版 export-baseline.ps1。" >&2
    exit 1
fi
if ! command -v mysql >/dev/null 2>&1; then
    echo "错误：未找到 mysql 客户端。" >&2
    exit 1
fi

mkdir -p "$OUTPUT_DIR"
SCHEMA_FILE="$OUTPUT_DIR/01-schema.sql"
HISTORY_FILE="$OUTPUT_DIR/02-flyway-history.sql"

# ---------- 2. 收集密码到临时配置文件 ----------
# 使用 --defaults-extra-file 而不是命令行传密码，避免密码泄漏到 ps / history。
umask 077
CNF_FILE="$(mktemp)"
cleanup() { rm -f "$CNF_FILE"; }
trap cleanup EXIT

read -rsp "源库(${SOURCE_USER}@${SOURCE_HOST})密码: " SOURCE_PASSWORD
echo
{
    echo "[client]"
    echo "user=${SOURCE_USER}"
    echo "password=${SOURCE_PASSWORD}"
} > "$CNF_FILE"
unset SOURCE_PASSWORD

DUMP_ARGS=(
    --defaults-extra-file="$CNF_FILE"
    "--host=$SOURCE_HOST"
    "--port=$SOURCE_PORT"
    --default-character-set=utf8mb4
    --single-transaction
    --set-gtid-purged=OFF
)

echo "源库: ${SOURCE_HOST}:${SOURCE_PORT}/${SOURCE_DB}"

# ---------- 3. 导出表结构（不含业务数据） ----------
echo "[1/3] 导出表结构（--no-data，不含任何业务数据）..."
# --skip-add-drop-table：不带 DROP TABLE，导入端在目标库非空时会直接失败而非静默删表。
mysqldump "${DUMP_ARGS[@]}" --no-data --skip-add-drop-table "$SOURCE_DB" > "$SCHEMA_FILE"

# ---------- 4. 导出 Flyway 迁移历史（这张表必须带数据） ----------
echo "[2/3] 导出 Flyway 迁移历史数据..."
# --no-create-info：表结构上一步已导出，这里只要记录行。
mysqldump "${DUMP_ARGS[@]}" --no-create-info --skip-add-drop-table "$SOURCE_DB" flyway_schema_history > "$HISTORY_FILE"

# ---------- 5. 校验导出结果 ----------
echo "[3/3] 校验导出结果..."
if [[ ! -s "$SCHEMA_FILE" ]]; then
    echo "错误：结构文件为空，导出异常。" >&2
    exit 1
fi
if [[ ! -s "$HISTORY_FILE" ]]; then
    echo "错误：迁移历史文件为空，导出异常。" >&2
    exit 1
fi
# 这两份文件只含表结构，但迁移历史属于内部信息，收紧权限。
chmod 600 "$SCHEMA_FILE" "$HISTORY_FILE"

TABLE_COUNT="$(grep -c 'CREATE TABLE' "$SCHEMA_FILE" || true)"
HISTORY_ROWS="$(grep -c 'INSERT INTO' "$HISTORY_FILE" || true)"
HAS_HISTORY_TABLE='否'
if grep -q 'CREATE TABLE `flyway_schema_history`' "$SCHEMA_FILE"; then
    HAS_HISTORY_TABLE='是'
fi

echo
echo "========== 导出完成 =========="
echo "结构文件    : $SCHEMA_FILE"
echo "              $(du -h "$SCHEMA_FILE" | cut -f1)，含 ${TABLE_COUNT} 条 CREATE TABLE"
echo "历史文件    : $HISTORY_FILE"
echo "              $(du -h "$HISTORY_FILE" | cut -f1)，含 ${HISTORY_ROWS} 条 INSERT（迁移记录）"
echo "含迁移历史表: $HAS_HISTORY_TABLE"
echo

if [[ "$HAS_HISTORY_TABLE" != '是' ]]; then
    echo "警告：结构文件中未发现 flyway_schema_history 表定义，请人工确认导出是否完整。" >&2
fi
if [[ "$HISTORY_ROWS" -lt 1 ]]; then
    echo "警告：迁移历史文件中没有记录，目标库将缺少 Flyway 版本信息。" >&2
fi

cat <<EOF

下一步（在同一台机器上执行）：
  ./import-baseline.sh $SCHEMA_FILE $HISTORY_FILE <新库名> <新库用户名> 127.0.0.1 3306

注意：这两个文件不含业务数据，但包含迁移历史，请勿提交到公开仓库，用完后及时删除。
EOF
