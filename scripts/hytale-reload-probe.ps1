param(
    [Parameter(Mandatory=$true)][string]$RuntimeDir
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$plugin = @(Get-ChildItem -LiteralPath (Join-Path $RuntimeDir 'mods') -Filter 'hytale-civ-*.jar' -File)
if ($plugin.Count -ne 1) { throw "Expected exactly one Civ plugin JAR, found $($plugin.Count)" }
$jarPath = $plugin[0].FullName
$stdoutPath = Join-Path $RuntimeDir 'reload.stdout.log'
$stderrPath = Join-Path $RuntimeDir 'reload.stderr.log'
$stdoutStream = [System.IO.FileStream]::new($stdoutPath, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write, [System.IO.FileShare]::ReadWrite)
$stderrStream = [System.IO.FileStream]::new($stderrPath, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write, [System.IO.FileShare]::ReadWrite)
$process = $null
$stdoutTask = $null
$stderrTask = $null

function Wait-ForEvidence([string]$Evidence, [int]$Seconds) {
    $until = [DateTime]::UtcNow.AddSeconds($Seconds)
    while ([DateTime]::UtcNow -lt $until) {
        if ($process.HasExited) { throw "Server exited before $Evidence" }
        if ((Test-Path $stdoutPath) -and ([string](Get-Content -LiteralPath $stdoutPath -Raw)) -like ('*' + $Evidence + '*')) { return }
        if ((Test-Path $stderrPath) -and ([string](Get-Content -LiteralPath $stderrPath -Raw)) -like ('*' + $Evidence + '*')) { return }
        Start-Sleep -Milliseconds 500
    }
    throw "Timed out waiting for runtime evidence: $Evidence"
}
function Send-Console([string]$Command) {
    if ($process.HasExited) { throw "Server exited before command $Command" }
    $process.StandardInput.WriteLine($Command)
    $process.StandardInput.Flush()
}
function Replace-CompiledMarker([string]$Path) {
    $entryName = 'dev/civilizations/plugin/CivReloadProbeCommand.class'
    $archive = [System.IO.Compression.ZipFile]::Open($Path, [System.IO.Compression.ZipArchiveMode]::Update)
    try {
        $entry = $archive.GetEntry($entryName)
        if ($null -eq $entry) { throw 'Compiled reload marker class not found in plugin JAR' }
        $stream = $entry.Open()
        try {
            $memory = [System.IO.MemoryStream]::new()
            $stream.CopyTo($memory)
            $bytes = $memory.ToArray()
            $memory.Dispose()
        } finally { $stream.Dispose() }
        $before = [System.Text.Encoding]::ASCII.GetBytes('CIV_RELOAD_MARKER_A')
        $after = [System.Text.Encoding]::ASCII.GetBytes('CIV_RELOAD_MARKER_B')
        $positions = @()
        for ($i=0; $i -le $bytes.Length-$before.Length; $i++) {
            $match = $true
            for ($j=0; $j -lt $before.Length; $j++) {
                if ($bytes[$i+$j] -ne $before[$j]) { $match=$false; break }
            }
            if ($match) { $positions += $i }
        }
        if ($positions.Count -ne 1) { throw "Expected exactly one compiled A marker, found $($positions.Count)" }
        [Array]::Copy($after, 0, $bytes, $positions[0], $after.Length)
        $entry.Delete()
        $replacement = $archive.CreateEntry($entryName)
        $out = $replacement.Open()
        try { $out.Write($bytes, 0, $bytes.Length) } finally { $out.Dispose() }
    } finally { $archive.Dispose() }
}

try {
    $psi = [System.Diagnostics.ProcessStartInfo]::new()
    $psi.FileName = 'java'
    $psi.WorkingDirectory = $RuntimeDir
    $psi.UseShellExecute = $false
    $psi.RedirectStandardInput = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    # Windows PowerShell 5.1 uses .NET Framework, where ProcessStartInfo.ArgumentList is unavailable.
    $psi.Arguments = '-Dcivilizations.runtimeProbe=true -jar "' + $env:HYTALE_SERVER_JAR +
        '" --assets "' + $env:HYTALE_ASSETS_PATH +
        '" --auth-mode offline --disable-sentry --boot-command civreloadmarker'
    $process = [System.Diagnostics.Process]::Start($psi)
    $stdoutTask = $process.StandardOutput.BaseStream.CopyToAsync($stdoutStream)
    $stderrTask = $process.StandardError.BaseStream.CopyToAsync($stderrStream)
    Wait-ForEvidence 'CIV_RELOAD_MARKER_A' 60
    Write-Host 'HCIV_RELOAD_INITIAL_A'
    Replace-CompiledMarker $jarPath
    Write-Host 'HCIV_RELOAD_DISK_UPDATED_B'
    Send-Console 'plugin reload Civilizations:HytaleCiv'
    Start-Sleep -Seconds 4
    Send-Console 'civreloadmarker'
    Wait-ForEvidence 'CIV_RELOAD_MARKER_B' 25
    Write-Host 'HCIV_RELOAD_NEW_CODE_B'
    for ($i=1; $i -le 2; $i++) {
        Send-Console 'plugin reload Civilizations:HytaleCiv'
        Start-Sleep -Seconds 4
        Send-Console 'civreloadmarker'
        Start-Sleep -Seconds 2
        if ($process.HasExited) { throw "Server exited after repeat reload $i" }
        Write-Host "HCIV_RELOAD_REPEATED_$i"
    }
    Write-Host 'HCIV_RELOAD_RUNTIME_PASS'
} catch {
    Write-Host ('HCIV_RELOAD_ERROR ' + $_.Exception.ToString())
    Write-Host ('HCIV_RELOAD_STACK ' + $_.ScriptStackTrace)
    throw
} finally {
    if ($null -ne $process) {
        if (-not $process.HasExited) {
            try { Send-Console 'stop' } catch {}
            if (-not $process.WaitForExit(10000)) {
                & taskkill.exe /PID $process.Id /T /F 2>&1 | Out-Host
                $process.WaitForExit(5000) | Out-Null
            }
        }
        if ($null -ne $stdoutTask) { try { $stdoutTask.Wait(3000) | Out-Null } catch {} }
        if ($null -ne $stderrTask) { try { $stderrTask.Wait(3000) | Out-Null } catch {} }
        $process.Dispose()
    }
    $stdoutStream.Dispose()
    $stderrStream.Dispose()
    Write-Host '----- Hytale reload stdout -----'
    if (Test-Path $stdoutPath) { Get-Content -LiteralPath $stdoutPath | Out-Host }
    Write-Host '----- Hytale reload stderr -----'
    if (Test-Path $stderrPath) { Get-Content -LiteralPath $stderrPath | Out-Host }
}
