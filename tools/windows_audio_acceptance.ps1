param(
    [Parameter(Mandatory = $true)][string]$AppImage,
    [Parameter(Mandatory = $true)][string]$EvidenceDirectory
)
$ErrorActionPreference = 'Stop'
if (-not $IsWindows -and $env:OS -ne 'Windows_NT') { throw 'This acceptance runner requires physical Windows 10/11.' }
$root = (Resolve-Path $AppImage).Path
New-Item -ItemType Directory -Force $EvidenceDirectory | Out-Null
$evidence = (Resolve-Path $EvidenceDirectory).Path
$launcher = Join-Path $root 'MeloX-Desktop.exe'
if (-not (Test-Path $launcher)) { throw "Missing packaged launcher: $launcher" }
python (Join-Path $PSScriptRoot 'verify_decoder_bundle.py') --artifact $root --run --output (Join-Path $evidence 'packaged-decoder.json')
if ($LASTEXITCODE -ne 0) { throw 'Packaged native decoder verification failed.' }
Get-CimInstance Win32_SoundDevice | Select-Object Name, Status, Manufacturer | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $evidence 'devices.json') -Encoding utf8
$process = Start-Process -FilePath $launcher -PassThru
$checks = @(
    'Local WAV/MP3/FLAC/AAC produce continuous audible output',
    'Authorized provider HTTP stream plays and exposes real errors on invalid authorization',
    'Pause/resume preserves position; seek=0 and seek=end work',
    'Previous/next and quality changes preserve user pause intent',
    'Volume and mute change actual speaker output',
    'Offline downloaded track and failed-track recovery work',
    'AutoMix produces two audible tracks in overlap; transport cancels or hands off correctly',
    'Exit/relaunch restores queue, selected item and position paused',
    'Output unavailable/device switch and sleep/resume recover or report genuine failure',
    'Long-running playback has no audible gaps, decoder/thread leak or stale callback'
)
$results = @()
foreach ($check in $checks) {
    Write-Host "`nVerify in the running packaged app: $check"
    $answer = Read-Host 'Observed result [PASS / FAIL / BLOCKED / NOT_RUN]'
    if ($answer -notin @('PASS', 'FAIL', 'BLOCKED', 'NOT_RUN')) { $answer = 'NOT_RUN' }
    $note = Read-Host 'Observation (exclude account credentials and private music content)'
    $results += [ordered]@{ check = $check; result = $answer; observation = $note; recorded_utc = (Get-Date).ToUniversalTime().ToString('o') }
}
$acceptance = 'INCOMPLETE'
if (($results | Where-Object { $_.result -ne 'PASS' }).Count -eq 0) { $acceptance = 'USER_CONFIRMED_PASS' }
[ordered]@{
    environment = 'physical Windows user-operated packaged app';
    platform = [System.Environment]::OSVersion.VersionString;
    launcher_sha256 = (Get-FileHash $launcher -Algorithm SHA256).Hash.ToLower();
    launch_pid = $process.Id;
    results = $results;
    acceptance = $acceptance;
    evidence_note = 'User observations are distinct from unit tests, FFmpeg PCM and CI. Store loopback recordings locally; publish only aggregate synthetic-audio metrics.'
} | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $evidence 'windows-hardware-acceptance.json') -Encoding utf8
Write-Host "Evidence saved to $evidence"
