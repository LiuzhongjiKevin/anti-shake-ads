param([string]$OutputName = 'PhoneLogCollector.exe')
$ErrorActionPreference = 'Stop'
$code = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'CaptureWindow.cs') -Raw -Encoding UTF8
$output = Join-Path $PSScriptRoot $OutputName
$temporaryOutput = Join-Path $PSScriptRoot ('PhoneLogCollector-build-' + [Guid]::NewGuid().ToString('N') + '.exe')
Add-Type -TypeDefinition $code -ReferencedAssemblies 'System.dll','System.Core.dll','System.Drawing.dll','System.Windows.Forms.dll','System.Web.Extensions.dll' -OutputAssembly $temporaryOutput -OutputType WindowsApplication
Move-Item -LiteralPath $temporaryOutput -Destination $output -Force
Get-Item -LiteralPath $output | Select-Object FullName,Length
