$ErrorActionPreference = 'Stop'
$toolsDir = Join-Path $PSScriptRoot 'tools'
$adbPath = Join-Path $toolsDir 'platform-tools\adb.exe'
if (Test-Path -LiteralPath $adbPath) {
    & $adbPath version
    exit $LASTEXITCODE
}
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
New-Item -ItemType Directory -Path $toolsDir -Force | Out-Null
$archivePath = Join-Path $toolsDir 'platform-tools-windows.zip'
Invoke-WebRequest -UseBasicParsing -Uri 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile $archivePath
Expand-Archive -LiteralPath $archivePath -DestinationPath $toolsDir -Force
if (-not (Test-Path -LiteralPath $adbPath)) { throw 'ADB was not found after extraction.' }
& $adbPath version
if ($LASTEXITCODE -ne 0) { throw 'ADB could not start.' }
