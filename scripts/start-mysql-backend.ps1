param(
    [string]$CredentialPath = 'F:\MYSQL\Client\logistics-app.credential.xml',
    [string]$JavaHome = 'D:\jdk',
    [int]$Port = 8080,
    [switch]$Background
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $projectRoot 'target/logistics-backend-0.0.1-SNAPSHOT.jar'
$java = Join-Path $JavaHome 'bin/java.exe'
if (!(Test-Path -LiteralPath $java)) { throw 'Java executable not found. Set -JavaHome to your JDK directory.' }
if (!(Test-Path -LiteralPath $jar)) { throw 'Backend jar not found. Run scripts/mvn.ps1 package first.' }
if (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue) { throw "Port $Port is occupied. Inspect it before starting another backend." }
if (Test-Path -LiteralPath $CredentialPath) {
    # Export-Clixml credentials on Windows are encrypted for the original Windows user (DPAPI).
    $credential = Import-Clixml -LiteralPath $CredentialPath
    if ($credential -isnot [Management.Automation.PSCredential]) { throw 'Invalid credential file.' }
} else {
    $secret = Read-Host 'Enter local logistics_app MySQL password (hidden)' -AsSecureString
    $credential = [Management.Automation.PSCredential]::new('logistics_app', $secret)
}
$saved = @{}
foreach ($name in @('DB_URL','DB_USERNAME','DB_PASSWORD','SERVER_PORT','SPRING_PROFILES_ACTIVE')) {
    $saved[$name] = [Environment]::GetEnvironmentVariable($name,'Process')
}
try {
    $env:DB_URL = 'jdbc:mysql://127.0.0.1:3306/logistics?useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true'
    $env:DB_USERNAME = $credential.UserName
    $env:DB_PASSWORD = $credential.GetNetworkCredential().Password
    $env:SERVER_PORT = [string]$Port
    $env:SPRING_PROFILES_ACTIVE = 'default'
    if ($Background) {
        $process = Start-Process -FilePath $java -ArgumentList @('-jar',('"'+$jar+'"'),'--server.address=127.0.0.1') `
            -WorkingDirectory $projectRoot -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput (Join-Path $projectRoot 'target/backend-mysql.log') `
            -RedirectStandardError (Join-Path $projectRoot 'target/backend-mysql-error.log')
        [pscustomobject]@{processId=$process.Id;port=$Port;jar=$jar} | ConvertTo-Json |
            Set-Content -LiteralPath (Join-Path $projectRoot 'target/backend-mysql-process.json') -Encoding utf8
        Write-Output "Backend launched: PID=$($process.Id), URL=http://localhost:$Port/logistics-admin.html"
    } else {
        & $java -jar $jar '--server.address=127.0.0.1'
        if ($LASTEXITCODE -ne 0) { throw "Backend exited with code $LASTEXITCODE" }
    }
} finally {
    foreach ($name in $saved.Keys) { [Environment]::SetEnvironmentVariable($name,$saved[$name],'Process') }
    $credential = $null
    if ($secret) { $secret.Dispose() }
}
