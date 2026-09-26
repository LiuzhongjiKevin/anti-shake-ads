$ErrorActionPreference = 'Stop'
$captureRoot = Split-Path -Parent $PSScriptRoot
$exeFile = Join-Path $captureRoot 'PhoneLogCollector.exe'
Add-Type -AssemblyName System.Windows.Forms,System.Drawing,System.Web.Extensions
[void][Reflection.Assembly]::LoadFrom($exeFile)
$flags = [Reflection.BindingFlags]'Instance,NonPublic'
$ui = New-Object CaptureWindow
function Field([string]$name) { $ui.GetType().GetField($name, $flags).GetValue($ui) }
function Invoke-UiMethod([string]$name, [object[]]$arguments = @()) { $ui.GetType().GetMethod($name, $flags).Invoke($ui, $arguments) }
function Assert-True([bool]$condition, [string]$message) { if (-not $condition) { throw $message } }
function Pump-Until([scriptblock]$condition, [int]$limitSeconds = 20) {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    while (-not (& $condition)) {
        [Windows.Forms.Application]::DoEvents()
        if ($watch.Elapsed.TotalSeconds -gt $limitSeconds) { throw 'Window component integration test timed out' }
        Start-Sleep -Milliseconds 50
    }
}
$indexPath = Join-Path $captureRoot 'local-only\desktop-latest.json'
$oldIndex = if (Test-Path -LiteralPath $indexPath) { [IO.File]::ReadAllBytes($indexPath) } else { $null }
try {
    $adbFile = Join-Path $captureRoot 'tools\platform-tools\adb.exe'
    $serial = (& $adbFile -d get-serialno).Trim()
    Assert-True ($LASTEXITCODE -eq 0 -and $serial -ne 'unknown') 'USB test handset required'
    $phoneType = $ui.GetType().GetNestedType('Phone', [Reflection.BindingFlags]'NonPublic')
    $phone = [Activator]::CreateInstance($phoneType, $true)
    $phoneType.GetField('Serial').SetValue($phone, $serial)
    $phoneType.GetField('State').SetValue($phone, 'device')
    $phoneType.GetField('Model').SetValue($phone, 'USB validation device')
    [void](Field 'devices').Items.Add($phone)
    (Field 'devices').SelectedIndex = 0
    (Field 'apps').Text = '采集窗口自检，不是广告效果测试'
    (Field 'notes').Text = '第一轮自检说明'
    (Field 'seconds').Value = 90
    (Field 'fullMode').Checked = $false
    Invoke-UiMethod 'StartCapture'
    Pump-Until { (Field 'announced') -eq $true }
    Invoke-UiMethod 'SendMark' @('广告出现')
    Invoke-UiMethod 'RequestStop'
    Pump-Until { $null -eq (Field 'collector') }
    $firstFolder = Field 'folder'
    $events = @(Get-Content -Encoding UTF8 -LiteralPath (Join-Path $firstFolder 'events.jsonl') | ForEach-Object { $_ | ConvertFrom-Json })
    Assert-True (@($events | Where-Object type -eq '广告出现').Count -eq 1) 'The event button must persist exactly one marker'
    Assert-True (@($events | Where-Object type -eq 'stop').Count -eq 1) 'The stop action must be recorded once, not duplicated by polling'
    $meta = Get-Content -Encoding UTF8 -LiteralPath (Join-Path $firstFolder 'round.json') -Raw | ConvertFrom-Json
    Assert-True ($meta.actualElapsedMs -lt 15000 -and $meta.remainingCollectorChildren -eq 0) 'Early stop must close collection processes promptly'
    Assert-True ((Get-Item -LiteralPath (Join-Path $firstFolder 'logcat.txt')).Length -gt 0) 'Real phone log bytes must be written'
    Assert-True (@(Get-ChildItem -LiteralPath (Join-Path $firstFolder 'screenshots') -File).Count -eq 0) 'Desktop must never silently enable screenshots'
    (Field 'seconds').Value = 5
    Invoke-UiMethod 'StartCapture'
    Pump-Until { $null -eq (Field 'collector') }
    $secondFolder = Field 'folder'
    Assert-True ($secondFolder -ne $firstFolder) 'Every round needs a distinct directory'
    $secondNotes = Get-Content -Encoding UTF8 -LiteralPath (Join-Path $secondFolder 'manual-notes.json') -Raw | ConvertFrom-Json
    Assert-True ($secondNotes.observation -eq '') 'Unchanged previous-round observations must not be silently copied to a new round'
    $secondMeta = Get-Content -Encoding UTF8 -LiteralPath (Join-Path $secondFolder 'round.json') -Raw | ConvertFrom-Json
    Assert-True ($secondMeta.actualElapsedMs -ge 4900 -and $secondMeta.actualElapsedMs -lt 10000) 'Configured time limit must stop the round'
    (Field 'notes').Text = '第二轮结束后补充的经过'
    Invoke-UiMethod 'SaveNotes'
    Assert-True ((Get-Content -Encoding UTF8 -LiteralPath (Join-Path $secondFolder 'manual-notes.json') -Raw | ConvertFrom-Json).observation -eq '第二轮结束后补充的经过') 'Post-test notes must be editable and saved'
    $request = Invoke-UiMethod 'AnalysisRequest'
    Assert-True ($request.Contains($secondFolder)) 'Handoff must point at the actual round, not an unrelated latest directory'
    Write-Output ('PASS: event, early stop, actual logs, no screenshots, fresh round notes, automatic stop, notes update, analysis handoff. Folders: ' + $firstFolder + ' ; ' + $secondFolder)
} finally {
    if ($null -ne (Field 'collector')) { Invoke-UiMethod 'RequestStop'; Pump-Until { $null -eq (Field 'collector') } }
    (Field 'timer').Stop()
    $ui.Dispose()
    if ($null -ne $oldIndex) { [IO.File]::WriteAllBytes($indexPath, $oldIndex) }
    elseif (Test-Path -LiteralPath $indexPath) { [IO.File]::Delete($indexPath) }
}
