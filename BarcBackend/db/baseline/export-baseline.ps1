<#
.SYNOPSIS
    从现有数据库导出「结构 + Flyway 迁移历史」，生成可用于初始化新环境的基线文件。

.DESCRIPTION
    用途：为新环境（如新的生产库）生成一份与当前 Flyway 版本完全一致的初始化基线。

    为什么需要它：
    1、当前 Flyway 迁移链不能从空库独立建库（V1 依赖已弃用的复数表 schools/students）；
    2、schema.sql 与真实结构不等价，既超前（含 V6/V9 的列）又缺失（缺 V1/V3 的表），
       直接导入后再跑迁移会因重复加列而失败；
    3、所以唯一可靠的结构来源是「一个已经迁移到最新版本的真实库」。

    安全约束：
    1、本脚本只做只读导出，不会修改源库；
    2、导出内容通过 --no-data 排除全部业务数据，只保留表结构与迁移历史，
       因此不会把开发数据带入生产；
    3、脚本不接收也不保存数据库密码，密码由 mysqldump 交互提示输入。

.PARAMETER Server
    源库主机。默认取自现有开发环境。

.PARAMETER Port
    源库端口。

.PARAMETER User
    源库账号（只需 SELECT 权限；--single-transaction 无需锁表权限）。

.PARAMETER Database
    源库名。

.PARAMETER OutputDir
    基线文件输出目录。默认为本脚本同级的 out 目录。

.EXAMPLE
    .\export-baseline.ps1
    使用默认参数，从开发库导出基线。

.EXAMPLE
    .\export-baseline.ps1 -OutputDir D:\barc-baseline
    指定输出目录。
#>
[CmdletBinding()]
param(
    [string]$Server = 'miaoyu.asia',
    [int]$Port = 51001,
    [string]$User = 'ba',
    [string]$Database = 'ba',
    [string]$OutputDir = (Join-Path $PSScriptRoot 'out')
)

$ErrorActionPreference = 'Stop'

# ============ 1. 前置检查 ============
Write-Host '[1/5] 检查 mysqldump 是否可用...' -ForegroundColor Cyan
$mysqldump = Get-Command mysqldump -ErrorAction SilentlyContinue
if (-not $mysqldump) {
    # 刻意不使用 here-string：PowerShell 5.1 无法解析 LF 行尾文件中的 here-string（CRLF 才行）。
    $notFoundMessage = @(
        '未找到 mysqldump。请任选一种方式：',
        '1、把 MySQL 8 的 bin 目录加入 PATH（例如 C:\Program Files\MySQL\MySQL Server 8.0\bin）；',
        '2、或直接在服务器上执行本导出（服务器已装 MySQL，通常自带 mysqldump）。'
    ) -join [Environment]::NewLine
    Write-Error $notFoundMessage
    exit 1
}
Write-Host "      mysqldump: $($mysqldump.Source)"

# ============ 2. 准备输出目录 ============
Write-Host '[2/5] 准备输出目录...' -ForegroundColor Cyan
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}
$schemaFile = Join-Path $OutputDir '01-schema.sql'
$historyFile = Join-Path $OutputDir '02-flyway-history.sql'
Write-Host "      输出目录: $OutputDir"

# mysqldump 的连接参数（不含密码；--password 省略值时会交互提示）
$commonArgs = @(
    "--host=$Server",
    "--port=$Port",
    "--user=$User",
    '--password',
    '--default-character-set=utf8mb4',
    '--single-transaction',
    '--set-gtid-purged=OFF'
)

Write-Host ''
Write-Host "即将从 $Server`:$Port/$Database 只读导出（不会修改源库）。" -ForegroundColor Yellow
Write-Host '稍后 mysqldump 会提示输入密码。' -ForegroundColor Yellow
Write-Host ''

# ============ 3. 导出表结构（不含业务数据） ============
Write-Host '[3/5] 导出表结构（--no-data，不含任何业务数据）...' -ForegroundColor Cyan
# --result-file 由 mysqldump 直接写文件，避免 PowerShell 重定向产生 BOM/UTF-16 编码问题。
# --skip-add-drop-table 让导入端在目标库非空时直接失败，而不是静默删表。
& $mysqldump @commonArgs --no-data --skip-add-drop-table "--result-file=$schemaFile" $Database
if ($LASTEXITCODE -ne 0) { Write-Error "导出结构失败（mysqldump 退出码 $LASTEXITCODE）。"; exit 1 }

# ============ 4. 导出 Flyway 迁移历史（这张表必须带数据） ============
Write-Host '[4/5] 导出 Flyway 迁移历史数据...' -ForegroundColor Cyan
# --no-create-info：表结构已在上一步导出，这里只要记录行。
& $mysqldump @commonArgs --no-create-info --skip-add-drop-table "--result-file=$historyFile" $Database flyway_schema_history
if ($LASTEXITCODE -ne 0) {
    $historyFailedMessage = @(
        '导出 flyway_schema_history 失败。',
        '请确认源库已启用并执行过 Flyway（该表应存在于库中）。'
    ) -join [Environment]::NewLine
    Write-Error $historyFailedMessage
    exit 1
}

# ============ 5. 校验导出结果 ============
Write-Host '[5/5] 校验导出结果...' -ForegroundColor Cyan

$schemaInfo = Get-Item $schemaFile
$historyInfo = Get-Item $historyFile
if ($schemaInfo.Length -le 0) { Write-Error '结构文件为空，导出异常。'; exit 1 }
if ($historyInfo.Length -le 0) { Write-Error '迁移历史文件为空，导出异常。'; exit 1 }

$schemaText = Get-Content $schemaFile -Raw
$tableCount = ([regex]::Matches($schemaText, 'CREATE TABLE')).Count
$hasHistoryTable = $schemaText -match 'CREATE TABLE\s+`?flyway_schema_history`?'
$historyText = Get-Content $historyFile -Raw
$historyRowCount = ([regex]::Matches($historyText, 'INSERT INTO')).Count

Write-Host ''
Write-Host '========== 导出完成 ==========' -ForegroundColor Green
Write-Host "结构文件  : $schemaFile"
Write-Host "            $([math]::Round($schemaInfo.Length / 1KB, 1)) KB，含 $tableCount 条 CREATE TABLE"
Write-Host "历史文件  : $historyFile"
Write-Host "            $([math]::Round($historyInfo.Length / 1KB, 1)) KB，含 $historyRowCount 条 INSERT（迁移记录）"
Write-Host ''

# 结构性校验：两个文件必须同时有效，缺一不可。
if (-not $hasHistoryTable) {
    Write-Warning '结构文件中未发现 flyway_schema_history 表定义，请人工确认导出是否完整。'
}
if ($historyRowCount -lt 1) {
    Write-Warning '迁移历史文件中没有 INSERT 记录，目标库将缺少 Flyway 版本信息。'
}

Write-Host '下一步：' -ForegroundColor Yellow
Write-Host '1、把 01-schema.sql 与 02-flyway-history.sql 传到数据库所在服务器；'
Write-Host '2、在服务器执行 import-baseline.sh 完成导入；'
Write-Host '3、启动后端，日志应出现 Current version of schema ...: <最新版本> 且无 Migrating 动作。'
Write-Host ''
Write-Host '注意：这两个文件不含业务数据，但包含迁移历史，请勿提交到公开仓库。' -ForegroundColor Yellow
