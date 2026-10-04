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

function Read-CombinedOutput {
    param(
        [Parameter(Mandatory = $true)] [string] $StdoutLog,
        [Parameter(Mandatory = $true)] [string] $StderrLog
    )
    $stdout = if (Test-Path -LiteralPath $StdoutLog) { Get-Content -LiteralPath $StdoutLog -Raw } else { '' }
    $stderr = if (Test-Path -LiteralPath $StderrLog) { Get-Content -LiteralPath $StderrLog -Raw } else { '' }
    return $stdout + [Environment]::NewLine + $stderr
}

function Assert-Evidence {
    param(
        [Parameter(Mandatory = $true)] [string] $Combined,
        [Parameter(Mandatory = $true)] [string[]] $RequiredEvidence
    )
    foreach ($evidence in $RequiredEvidence) {
        if (-not $Combined.Contains($evidence)) {
            throw "Expected Hytale runtime evidence was not found: $evidence"
        }
    }
}

function Run-WoodcutterScenario {
    param([Parameter(Mandatory = $true)] [string] $RuntimeDir)

    $stdoutLog = Join-Path $RuntimeDir 'server.stdout.log'
    $stderrLog = Join-Path $RuntimeDir 'server.stderr.log'
    $process = Start-Process -FilePath 'java' `
        -ArgumentList @(
            '-Dcivilizations.runtimeProbe=true',
            '-jar', $env:HYTALE_SERVER_JAR,
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

    $combined = Read-CombinedOutput -StdoutLog $stdoutLog -StderrLog $stderrLog
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

    Assert-Evidence -Combined $combined -RequiredEvidence @(
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

    if ($process.ExitCode -ne 0) {
        throw "Hytale server exited with code $($process.ExitCode)."
    }
    Write-Host 'Real Hytale woodcutter scenario passed.'
}

function Run-PersistenceScenario {
    param([Parameter(Mandatory = $true)] [string] $RuntimeDir)

    $prepareStdoutLog = Join-Path $RuntimeDir 'persistence.prepare.stdout.log'
    $prepareStderrLog = Join-Path $RuntimeDir 'persistence.prepare.stderr.log'
    $prepare = Start-Process -FilePath 'java' `
        -ArgumentList @(
            '-Dcivilizations.runtimeProbe=true',
            '-Dcivilizations.persistenceProbeStage=prepare',
            '-jar', $env:HYTALE_SERVER_JAR,
            '--assets', $env:HYTALE_ASSETS_PATH,
            '--auth-mode', 'offline',
            '--disable-sentry',
            '--boot-command', 'civpersistenceprobe'
        ) `
        -WorkingDirectory $RuntimeDir `
        -RedirectStandardOutput $prepareStdoutLog `
        -RedirectStandardError $prepareStderrLog `
        -Wait `
        -PassThru

    $prepareCombined = Read-CombinedOutput -StdoutLog $prepareStdoutLog -StderrLog $prepareStderrLog
    Write-Host '----- Hytale persistence prepare output -----'
    Write-Host $prepareCombined
    Write-Host '----- end Hytale persistence prepare output -----'
    Write-Host "Persistence prepare exit code: $($prepare.ExitCode)"

    if ($prepareCombined.Contains('CIV_PERSISTENCE_PROBE_FAIL')) {
        throw 'The persistence prepare stage reported failure.'
    }
    Assert-Evidence -Combined $prepareCombined -RequiredEvidence @(
        'CIV_PERSISTENCE_PREPARED uuid=',
        'CIV_PERSISTENCE_WORKPLACE_INDEXED_PREPARE',
        'CIV_PERSISTENCE_PREPARE_PASS',
        'Shutdown completed!'
    )
    if ($prepare.ExitCode -ne 0) {
        throw "Persistence prepare server exited with code $($prepare.ExitCode)."
    }

    $uuidMatch = [regex]::Match(
        $prepareCombined,
        'CIV_PERSISTENCE_PREPARED uuid=([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})'
    )
    if (-not $uuidMatch.Success) {
        throw 'Could not extract the prepared Civ inhabitant UUID from server output.'
    }
    $entityUuid = $uuidMatch.Groups[1].Value
    Write-Host "Prepared persistent Civ inhabitant UUID: $entityUuid"

    $restoreStdoutLog = Join-Path $RuntimeDir 'persistence.restore.stdout.log'
    $restoreStderrLog = Join-Path $RuntimeDir 'persistence.restore.stderr.log'
    $restore = Start-Process -FilePath 'java' `
        -ArgumentList @(
            '-Dcivilizations.runtimeProbe=true',
            '-Dcivilizations.persistenceProbeStage=restore',
            "-Dcivilizations.persistenceProbeEntityUuid=$entityUuid",
            '-jar', $env:HYTALE_SERVER_JAR,
            '--assets', $env:HYTALE_ASSETS_PATH,
            '--auth-mode', 'offline',
            '--disable-sentry',
            '--boot-command', 'civpersistenceprobe'
        ) `
        -WorkingDirectory $RuntimeDir `
        -RedirectStandardOutput $restoreStdoutLog `
        -RedirectStandardError $restoreStderrLog `
        -Wait `
        -PassThru

    $restoreCombined = Read-CombinedOutput -StdoutLog $restoreStdoutLog -StderrLog $restoreStderrLog
    Write-Host '----- Hytale persistence restore output -----'
    Write-Host $restoreCombined
    Write-Host '----- end Hytale persistence restore output -----'
    Write-Host "Persistence restore exit code: $($restore.ExitCode)"

    if ($restoreCombined.Contains('CIV_PERSISTENCE_PROBE_FAIL')) {
        throw 'The persistence prepare stage reported failure.'
    }
    Assert-Evidence -Combined $restoreCombined -RequiredEvidence @(
        "CIV_PERSISTENCE_RESTORED uuid=$entityUuid",
        'name=Persist_Runtime_Probe',
        'profession=CONSTRUCTION_WORKER',
        'xp=37',
        'workplace=00000000-0000-0000-0000-000000000002',
        'CIV_PERSISTENCE_WORKPLACE_INDEXED_RESTORE',
        'CIV_PERSISTENCE_RESTORE_PASS',
        'Shutdown completed!'
    )
    if ($restore.ExitCode -ne 0) {
        throw "Persistence restore server exited with code $($restore.ExitCode)."
    }
    Write-Host 'Real Hytale persistence scenario passed across two separate server processes.'
}

function Run-MineSupportScenario {
    param([Parameter(Mandatory = $true)] [string] $RuntimeDir)

    $stdoutLog = Join-Path $RuntimeDir 'minesupport.stdout.log'
    $stderrLog = Join-Path $RuntimeDir 'minesupport.stderr.log'
    $process = Start-Process -FilePath 'java' `
        -ArgumentList @(
            '-Dcivilizations.runtimeProbe=true',
            '-Dcivilizations.mineSupportProbe=true',
            '-jar', $env:HYTALE_SERVER_JAR,
            '--assets', $env:HYTALE_ASSETS_PATH,
            '--auth-mode', 'offline',
            '--disable-sentry',
            '--boot-command', 'civtest'
        ) `
        -WorkingDirectory $RuntimeDir `
        -RedirectStandardOutput $stdoutLog `
        -RedirectStandardError $stderrLog `
        -Wait `
        -PassThru

    $combined = Read-CombinedOutput -StdoutLog $stdoutLog -StderrLog $stderrLog
    Write-Host '----- Hytale mine support output -----'
    Write-Host $combined
    Write-Host '----- end Hytale mine support output -----'
    Write-Host "Mine support server exit code: $($process.ExitCode)"

    if ($combined.Contains('CIV_MINE_SUPPORT_RUNTIME_FAIL')) {
        throw 'The Civ mine support runtime scenario reported failure.'
    }
    if ($combined.Contains("chunk isn't currently loaded")) {
        throw 'The Civ mine support runtime scenario touched an unloaded chunk.'
    }
    Assert-Evidence -Combined $combined -RequiredEvidence @(
        'Loaded pack: Hytale:Hytale from Assets.zip',
        'Loaded pack: Civilizations:HytaleCivAssets from hytale-civ-assets',
        'Enabled plugin Civilizations:HytaleCiv',
        'Hytale Server Booted!',
        'Console executed command: civtest',
        'CIV_MINE_SUPPORT_RUNTIME_STARTED',
        'CIV_MINE_SUPPORT_PREFAB_PLACED',
        'CIV_MINE_SUPPORT_DECO_MARKED beamCells=4',
        'CIV_MINE_SUPPORT_STABLE beamCells=4',
        'CIV_MINE_SUPPORT_BREAKABLE',
        'remainingBeam=3',
        'CIV_MINE_SUPPORT_RUNTIME_PASS',
        'Shut down plugin Civilizations:HytaleCiv',
        'Shutdown completed!'
    )
    if ($process.ExitCode -ne 0) {
        throw "Mine support server exited with code $($process.ExitCode)."
    }
    Write-Host 'Real Hytale mine support scenario passed.'
}

function Run-WarmRuntimeScenario {
    param([Parameter(Mandatory = $true)] [string] $RuntimeDir)

    $stdoutLog = Join-Path $RuntimeDir 'warmruntime.stdout.log'
    $stderrLog = Join-Path $RuntimeDir 'warmruntime.stderr.log'
    $process = Start-Process -FilePath 'java' `
        -ArgumentList @(
            '-Dcivilizations.runtimeProbe=true',
            '-jar', $env:HYTALE_SERVER_JAR,
            '--assets', $env:HYTALE_ASSETS_PATH,
            '--auth-mode', 'offline',
            '--disable-sentry',
            '--boot-command', 'civwarmruntimebenchmark'
        ) `
        -WorkingDirectory $RuntimeDir `
        -RedirectStandardOutput $stdoutLog `
        -RedirectStandardError $stderrLog `
        -Wait `
        -PassThru

    $combined = Read-CombinedOutput -StdoutLog $stdoutLog -StderrLog $stderrLog
    Write-Host '----- Hytale warm runtime benchmark output -----'
    Write-Host $combined
    Write-Host '----- end Hytale warm runtime benchmark output -----'
    Write-Host "Warm runtime benchmark server exit code: $($process.ExitCode)"

    if ($combined.Contains('CIV_WARM_RUNTIME_BENCHMARK_FAIL')) {
        throw 'The warm Hytale runtime benchmark reported failure.'
    }
    Assert-Evidence -Combined $combined -RequiredEvidence @(
        'Loaded pack: Hytale:Hytale from Assets.zip',
        'Loaded pack: Civilizations:HytaleCivAssets from hytale-civ-assets',
        'Enabled plugin Civilizations:HytaleCiv',
        'Hytale Server Booted!',
        'Console executed command: civwarmruntimebenchmark',
        'CIV_WARM_RUNTIME_BENCHMARK_STARTED',
        'CIV_WARM_RUNTIME_WORLD_STARTED world=civ-warm-benchmark-1x dilation=1.0',
        'CIV_WARM_RUNTIME_WORLD_STARTED world=civ-warm-benchmark-4x dilation=4.0',
        'CIV_WARM_RUNTIME_WORLD_PASS world=civ-warm-benchmark-1x',
        'CIV_WARM_RUNTIME_WORLD_PASS world=civ-warm-benchmark-4x',
        'CIV_WARM_RUNTIME_BENCHMARK_RESULT',
        'CIV_WARM_RUNTIME_WORLDS_REMOVED oneX=true fourX=true',
        'CIV_WARM_RUNTIME_BENCHMARK_PASS',
        'Shutdown completed!'
    )
    if ($process.ExitCode -ne 0) {
        throw "Warm runtime benchmark server exited with code $($process.ExitCode)."
    }
    Write-Host 'Warm Hytale runtime benchmark passed.'
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
            'woodcutter' { Run-WoodcutterScenario -RuntimeDir $runtimeDir }
            'persistence' { Run-PersistenceScenario -RuntimeDir $runtimeDir }
            'minesupport' { Run-MineSupportScenario -RuntimeDir $runtimeDir }
            'warmruntime' { Run-WarmRuntimeScenario -RuntimeDir $runtimeDir }
            default { throw "No runtime implementation exists for registered scenario: $scenario" }
        }
    } finally {
        Remove-Runtime -RuntimeDir $runtimeDir
    }
    Write-Host "===== END Hytale runtime scenario: $scenario ====="
}
