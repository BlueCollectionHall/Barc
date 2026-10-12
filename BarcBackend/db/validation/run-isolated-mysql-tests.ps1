param([string[]]$MavenArguments = @('clean', 'package'))

# 仅创建本次专属的 tmpfs 容器；不接受外部库地址，也不挂载任何已有数据目录。
$ErrorActionPreference = 'Stop'
$testContainerName = 'barc-api-audit-' + [Guid]::NewGuid().ToString('N').Substring(0, 12)
$testEnvPath = Join-Path ([System.IO.Path]::GetTempPath()) ($testContainerName + '.env')
$testPassword = [Guid]::NewGuid().ToString('N')
$testContainerId = $null
$originalLocation = Get-Location
try {
    [IO.File]::WriteAllText($testEnvPath, "MYSQL_ROOT_PASSWORD=$testPassword`nMYSQL_ROOT_HOST=%`n", [Text.UTF8Encoding]::new($false))
    $testContainerId = (& docker run --detach --name $testContainerName --label barc.audit.isolated=true --env-file $testEnvPath --tmpfs /var/lib/mysql:rw --publish '127.0.0.1::3306' mysql:8.4 --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci).Trim()
    if ($LASTEXITCODE -ne 0 -or $testContainerId -notmatch '^[a-f0-9]{64}$') { throw '隔离 MySQL 创建失败' }
    $deadline = [DateTime]::UtcNow.AddSeconds(60)
    do {
        $ready = & docker exec $testContainerId mysqladmin ping --silent 2>$null
        if ($LASTEXITCODE -eq 0) { break }
        Start-Sleep -Seconds 1
    } while ([DateTime]::UtcNow -lt $deadline)
    if ($LASTEXITCODE -ne 0) { throw '隔离 MySQL 启动超时' }
    $testPort = (& docker port $testContainerId 3306/tcp).Trim()
    if ($testPort -notmatch '^127\.0\.0\.1:(\d+)$') { throw '隔离端口不是本机回环地址' }
    $env:BARC_TEST_MYSQL_URL = "jdbc:mysql://127.0.0.1:$($Matches[1])/"
    $env:BARC_TEST_MYSQL_PASSWORD = $testPassword
    $env:BARC_TEST_MYSQL_CONTAINER = $testContainerName
    Set-Location (Join-Path $PSScriptRoot '../..')
    & .\mvnw.cmd @MavenArguments
    if ($LASTEXITCODE -ne 0) { throw "隔离 MySQL 验证失败，Maven 退出码 $LASTEXITCODE" }
} finally {
    Set-Location $originalLocation
    Remove-Item Env:BARC_TEST_MYSQL_URL, Env:BARC_TEST_MYSQL_PASSWORD, Env:BARC_TEST_MYSQL_CONTAINER -ErrorAction SilentlyContinue
    if ($testContainerId -match '^[a-f0-9]{64}$') {
        # 只删除本脚本创建且名称、标签仍一致的容器，不按名称模糊匹配其他项目。
        $testMetadata = & docker inspect $testContainerId | ConvertFrom-Json
        if ($testMetadata.Name -eq "/$testContainerName" -and $testMetadata.Config.Labels.'barc.audit.isolated' -eq 'true') {
            & docker rm --force $testContainerId | Out-Null
        }
    }
    if (Test-Path -LiteralPath $testEnvPath) { Remove-Item -LiteralPath $testEnvPath }
}
