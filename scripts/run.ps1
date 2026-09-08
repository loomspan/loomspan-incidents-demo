$ErrorActionPreference = 'Stop'
$relayProject = Split-Path -Parent $PSScriptRoot
$relayJar = Join-Path $relayProject 'target\relay-0.1.0-SNAPSHOT.jar'
if (-not (Test-Path -LiteralPath $relayJar)) { throw 'Build first with .\mvnw.cmd package.' }
$relayJava = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
Push-Location $relayProject
try { & $relayJava -jar $relayJar } finally { Pop-Location }
