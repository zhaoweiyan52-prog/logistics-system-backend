param(
    [Parameter(ValueFromRemainingArguments=$true)]
    [string[]]$MavenArgs
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projectRoot
try {
    $localMaven = Join-Path $projectRoot '.tools/apache-maven-3.9.11/bin/mvn.cmd'
    if (Test-Path -LiteralPath $localMaven) { $maven = $localMaven }
    elseif (Get-Command mvn.cmd -ErrorAction SilentlyContinue) { $maven = (Get-Command mvn.cmd).Source }
    else { throw '未找到 Maven。请安装 Maven 3.6.3+ 并加入 PATH，或阅读 README.md。' }
    if (!$env:JAVA_HOME -and (Test-Path -LiteralPath 'D:/jdk/bin/javac.exe')) {
        $env:JAVA_HOME = 'D:/jdk'
    }
    if (!$MavenArgs) { $MavenArgs = @('test') }
    & $maven '-B' '-ntp' "-Dmaven.repo.local=$projectRoot/.m2/repository" @MavenArgs
    $result = $LASTEXITCODE
    if ($result -ne 0) { throw "Maven 执行失败，退出码 $result" }
} finally { Pop-Location }
