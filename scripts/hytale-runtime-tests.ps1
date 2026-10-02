param(
    [Parameter(Mandatory = $true)]
    [string[]] $Scenarios
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Get-RegisteredScenarios {
    $registryPath = Join-Path $PSScriptRoot 'hytale-runtime-scenarios.json'
    if (-not (Test-Path -LiteralPath $registryPath -PathType Leaf)) {
        throw "Runtime scenario registry was not found: $registryPath"
    }

    $registry = Get-Content -LiteralPath $registryPath -Raw | ConvertFrom-Json
    if ($registry.version -ne 1) {
        throw "Unsupported runtime scenario registry version: $($registry.version)"
    }

    $registered = @($registry.scenarios)
    if ($registered.Count -eq 0) {
        throw 'Runtime scenario registry is empty.'
    }

    $seen = @{}
    foreach ($scenario in $registered) {
        if ($scenario -notmatch '^[a-z][a-z0-9]{0,31}$' -or $scenario -eq 'all') {
            throw "Invalid runtime scenario name in registry: $scenario"
        }
        if ($seen.ContainsKey($scenario)) {
            throw "Duplicate runtime scenario in registry: $scenario"
        }
        $seen[$scenario] = $true
    }

    return $registered
}

function Remove-Runtime {
    param(
        [Parameter(Mandatory = $true)]
        [string] $RuntimeDir
    )

    if ([string]::IsNullOrWhiteSpace($RuntimeDir) -or -not (Test-Path -LiteralPath $RuntimeDir)) {
        return
    }

    $cleanupCommand = 'rd /s /q "\\?\' + $RuntimeDir + '"'
    $cleanup = Start-Process -FilePath 'cmd.exe' `
        -ArgumentList @('/d', '/c', $cleanupCommand) `
        -WindowStyle Hidden `
        -PassThru

    if (-not $cleanup.WaitForExit(10000)) {
        Stop-Process -Id $cleanup.Id -Force -ErrorAction SilentlyContinue
        Write-Warning "Runtime cleanup exceeded 10 seconds: $RuntimeDir"
    } elseif ($cleanup.ExitCode -ne 0) {
        Write-Warning "Runtime cleanup exited with code $($cleanup.ExitCode): $RuntimeDir"
    } else {
        Write-Host "Runtime cleaned: $RuntimeDir"
    }
}

function Prepare-Runtime {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Scenario
    )

    $runId = if ([string]::IsNullOrWhiteSpace($env:GITHUB_RUN_ID)) { 'local' } else { $env:GITHUB_RUN_ID }
    $attempt = if ([string]::IsNullOrWhiteSpace($env:GITHUB_RUN_ATTEMPT)) { '1' } else { $env:GITHUB_RUN_ATTEMPT }
    $runtimeRoot = if ([string]::IsNullOrWhiteSpace($env:RUNNER_TEMP)) { [System.IO.Path]::GetTempPath() } else { $env:RUNNER_TEMP }
    $runtimeDir = Join-Path $runtimeRoot ("hciv-$runId-$attempt-$Scenario")

    if (Test-Path -LiteralPath $runtimeDir) {
        Remove-Runtime -RuntimeDir $runtimeDir
    }

    $modsDir = Join-Path $runtimeDir 'mods'
    New-Item -ItemType Directory -Path $modsDir -Force | Out-Null

    $pluginJar = Get-ChildItem -Path 'build\libs' -Filter 'hytale-civ-*.jar' |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -eq $pluginJar) {
        throw 'Built Civ plugin JAR was not found in build\libs.'
    }

    Copy-Item -LiteralPath $pluginJar.FullName -Destination $modsDir -Force
    Copy-Item -LiteralPath 'asset-pack' -Destination (Join-Path $modsDir 'hytale-civ-assets') -Recurse -Force

    Write-Host "Prepared isolated Hytale runtime for scenario '$Scenario': $runtimeDir"
    return $runtimeDir
}

function Run-WoodcutterScenario {
    param(
        [Parameter(Mandatory = $true)]
        [string] $RuntimeDir
    )

    $stdoutLog = Join-Path $RuntimeDir 'server.stdout.log'
    $stderrLog = Join-Path $RuntimeDir 'server.stderr.log'

    $process = Start-Process -FilePath 'java' `
        -ArgumentList @(
            '-Dcivilizations.runtimeProbe=true',
            '-jar',
            $env:HYTALE_SERVER_JAR,
            '--assets', $env:HYTALE_ASSETS_PATH,
            '--auth-mode', 'offline',
            '--disable-sentry',
            '--boot-command', 'civwoodcutterprobe'
        ) `
        -WorkingDirectory $RuntimeDir `
        -RedirectStandardOutput $stdoutLog `
        -RedirectStandardError $stderrLog `
        -Wait `
        -PassThru

    $stdout = if (Test-Path -LiteralPath $stdoutLog) { Get-Content -LiteralPath $stdoutLog -Raw } else { '' }
    $stderr = if (Test-Path -LiteralPath $stderrLog) { Get-Content -LiteralPath $stderrLog -Raw } else { '' }
    $combined = $stdout + [Environment]::NewLine + $stderr

    Write-Host '----- Hytale woodcutter output -----'
    Write-Host $combined
    Write-Host '----- end Hytale woodcutter output -----'
    Write-Host "Hytale server exit code: $($process.ExitCode)"

    if ($combined.Contains('client.disconnection.shutdownReason.missingAssets.failedToLoad')) {
        throw 'Hytale still reports missing assets while using --assets with the local Assets.zip.'
    }
    if ($combined.Contains('CIV_WOODCUTTER_RUNTIME_FAIL')) {
        throw 'The Civ woodcutter runtime scenario reported failure.'
    }
    if ($combined.Contains("chunk isn't currently loaded")) {
        throw 'The Civ woodcutter runtime scenario touched an unloaded chunk.'
    }

    $requiredEvidence = @(
        'Loaded pack: Hytale:Hytale from Assets.zip',
        'Loaded pack: Civilizations:HytaleCivAssets from hytale-civ-assets',
        'Enabled plugin Civilizations:HytaleCiv',
        'Universe ready!',
        'Hytale Server Booted!',
        'Console executed command: civwoodcutterprobe',
        'CIV_WOODCUTTER_RUNTIME_STARTED',
        'CIV_WOODCUTTER_FLAT_WORLD_READY',
        'CIV_WOODCUTTER_FIXTURE_READY',
        'state=target-assigned',
        'state=chopping',
        'state=fell-success',
        'CIV_WOODCUTTER_TREE_FELLED',
        'CIV_WOODCUTTER_REENGAGED',
        'CIV_WOODCUTTER_RUNTIME_PASS',
        'Shut down plugin Civilizations:HytaleCiv',
        'Shutdown completed!'
    )

    foreach ($evidence in $requiredEvidence) {
        if (-not $combined.Contains($evidence)) {
            throw "Expected Hytale runtime evidence was not found: $evidence"
        }
    }

    if ($process.ExitCode -ne 0) {
        throw "Hytale server exited with code $($process.ExitCode)."
    }

    Write-Host 'Real Hytale woodcutter scenario passed.'
}

if ([string]::IsNullOrWhiteSpace($env:HYTALE_SERVER_JAR) -or -not (Test-Path -LiteralPath $env:HYTALE_SERVER_JAR -PathType Leaf)) {
    throw 'HYTALE_SERVER_JAR is missing or invalid.'
}
if ([string]::IsNullOrWhiteSpace($env:HYTALE_ASSETS_PATH) -or -not (Test-Path -LiteralPath $env:HYTALE_ASSETS_PATH -PathType Leaf)) {
    throw 'HYTALE_ASSETS_PATH is missing or invalid.'
}

$registeredScenarios = @(Get-RegisteredScenarios)
$seenRequested = @{}
foreach ($scenario in $Scenarios) {
    if ($scenario -notmatch '^[a-z][a-z0-9]{0,31}$') {
        throw "Invalid requested runtime scenario name: $scenario"
    }
    if ($seenRequested.ContainsKey($scenario)) {
        throw "Duplicate requested runtime scenario: $scenario"
    }
    if ($registeredScenarios -notcontains $scenario) {
        throw "Requested runtime scenario is not registered: $scenario"
    }
    $seenRequested[$scenario] = $true
}

foreach ($scenario in $Scenarios) {
    Write-Host "===== BEGIN Hytale runtime scenario: $scenario ====="
    $runtimeDir = Prepare-Runtime -Scenario $scenario

    try {
        switch ($scenario) {
            'woodcutter' {
                Run-WoodcutterScenario -RuntimeDir $runtimeDir
            }
            default {
                throw "No runtime implementation exists for registered scenario: $scenario"
            }
        }
    } finally {
        Remove-Runtime -RuntimeDir $runtimeDir
    }

    Write-Host "===== END Hytale runtime scenario: $scenario ====="
}
