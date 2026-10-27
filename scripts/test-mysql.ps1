param([string]$MySqlHome='F:\MYSQL\Server')
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$mysqld=Join-Path $MySqlHome 'bin/mysqld.exe'
$mysql=Join-Path $MySqlHome 'bin/mysql.exe'
if(!(Test-Path -LiteralPath $mysqld) -or !(Test-Path -LiteralPath $mysql)) {throw '未找到 MySQL 程序，请用 -MySqlHome 指定安装目录。'}
# 全新、隔离的测试实例；从不使用本机服务的数据目录，也不删除测试数据。
$runRoot=Join-Path $projectRoot ('target/mysql-test-'+[Guid]::NewGuid().ToString('N'))
$dataRoot=Join-Path $runRoot 'data'
New-Item -ItemType Directory -Path $dataRoot -Force | Out-Null
if((Get-ChildItem -LiteralPath $dataRoot -Force | Measure-Object).Count -ne 0) {throw '拒绝初始化非空目录。'}
$listener=New-Object System.Net.Sockets.TcpListener([Net.IPAddress]::Loopback,0)
$listener.Start()
$testPort=$listener.LocalEndpoint.Port
$listener.Stop()
$saved=@{}
foreach($key in @('TEST_DB_URL','TEST_DB_USERNAME','TEST_DB_PASSWORD','TEST_DB_DRIVER')) {$saved[$key]=[Environment]::GetEnvironmentVariable($key,'Process')}
$process=$null
try {
    & $mysqld --no-defaults --initialize-insecure "--basedir=$MySqlHome" "--datadir=$dataRoot" --console *> (Join-Path $runRoot 'initialize.log')
    if($LASTEXITCODE -ne 0) {throw "测试实例初始化失败，查看 $runRoot/initialize.log"}
    $process=Start-Process -FilePath $mysqld -WindowStyle Hidden -PassThru -ArgumentList @(
        '--no-defaults',"--basedir=`"$MySqlHome`"","--datadir=`"$dataRoot`"",'--bind-address=127.0.0.1',"--port=$testPort",'--mysqlx=0',
        '--character-set-server=utf8mb4','--collation-server=utf8mb4_bin','--default-time-zone=+00:00',"--log-error=`"$(Join-Path $runRoot 'mysql.log')`"")
    $ready=$false
    for($i=0;$i -lt 100;$i++) {
        if($process.HasExited) {throw "测试实例未能启动，查看 $runRoot/mysql.log"}
        $probe=New-Object Net.Sockets.TcpClient
        try {$probe.Connect('127.0.0.1',$testPort);$ready=$true;break} catch {Start-Sleep -Milliseconds 200} finally {$probe.Dispose()}
    }
    if(!$ready){throw '测试 MySQL 启动超时。'}
    & $mysql --no-defaults --host=127.0.0.1 "--port=$testPort" --user=root --execute='CREATE DATABASE logistics_integration CHARACTER SET utf8mb4 COLLATE utf8mb4_bin'
    if($LASTEXITCODE -ne 0) {throw '无法建立隔离测试库。'}
    $env:TEST_DB_URL="jdbc:mysql://127.0.0.1:$testPort/logistics_integration?useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&allowPublicKeyRetrieval=true&sslMode=DISABLED"
    $env:TEST_DB_USERNAME='root';$env:TEST_DB_PASSWORD='';$env:TEST_DB_DRIVER='com.mysql.cj.jdbc.Driver'
    & (Join-Path $PSScriptRoot 'mvn.ps1') test
    $clientArgs=@('--no-defaults','--host=127.0.0.1',"--port=$testPort",'--user=root','--default-character-set=utf8mb4','--database=logistics_integration','--batch','--skip-column-names')
    $before=& $mysql @clientArgs --execute='SELECT COUNT(*) FROM logistics_orders'
    $migration=(Join-Path $projectRoot 'sql/03-full-features.sql').Replace('\','/')
    for($i=0;$i -lt 2;$i++) {
        & $mysql @clientArgs "--execute=SOURCE $migration"
        if($LASTEXITCODE -ne 0) {throw '增量升级 SQL 执行失败。'}
    }
    $after=& $mysql @clientArgs --execute='SELECT COUNT(*) FROM logistics_orders'
    $duplicates=& $mysql @clientArgs --execute="SELECT COUNT(*) FROM (SELECT order_no FROM logistics_tasks WHERE status='todo' GROUP BY order_no HAVING COUNT(*)>1) d"
    if($before -ne $after -or [int]$duplicates -ne 0) {throw '重复升级改变了订单数量或生成了重复待办。'}
    Write-Host '增量 SQL 重复执行两次验证通过：原订单数量不变、无重复待办。'
    Copy-Item -LiteralPath (Join-Path $projectRoot 'target/surefire-reports') -Destination (Join-Path $runRoot 'reports') -Recurse
    Write-Host "真实 MySQL 测试通过；测试目录保留在 $runRoot"
} finally {
    foreach($key in $saved.Keys) {[Environment]::SetEnvironmentVariable($key,$saved[$key],'Process')}
    if($process -and !$process.HasExited) {
        & $mysql --no-defaults --host=127.0.0.1 "--port=$testPort" --user=root --execute=SHUTDOWN 2>$null
        if(!$process.WaitForExit(10000)) {Stop-Process -Id $process.Id}
    }
}
