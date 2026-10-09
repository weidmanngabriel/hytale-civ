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
    param([Parameter(Mandatory = $true)] [string] $RuntimeDir)
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
    param([Parameter(Mandatory = $true)] [string] $Scenario)

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

function Start-HytaleProbe {
    param(
        [Parameter(Mandatory = $true)] [string] $RuntimeDir,
        [Parameter(Mandatory = $true)] [string] $LogPrefix,
        [Parameter(Mandatory = $true)] [string] $BootCommand,
        [string[]] $JvmProperties = @(),
        [int] $TimeoutSeconds = 60
    )

    $stdoutLog = Join-Path $RuntimeDir "$LogPrefix.stdout.log"
    $stderrLog = Join-Path $RuntimeDir "$LogPrefix.stderr.log"
    $arguments = @('-Dcivilizations.runtimeProbe=true') + $JvmProperties + @(
        '-jar', $env:HYTALE_SERVER_JAR,
        '--assets', $env:HYTALE_ASSETS_PATH,
        '--auth-mode', 'offline',
        '--disable-sentry',
        '--boot-command', $BootCommand
    )

    $process = Start-Process -FilePath 'java' `
        -ArgumentList $arguments `
        -WorkingDirectory $RuntimeDir `
        -RedirectStandardOutput $stdoutLog `
        -RedirectStandardError $stderrLog `
        -PassThru

    $finished = $process.WaitForExit($TimeoutSeconds * 1000)
    if (-not $finished) {
        Write-Warning "Hytale probe '$LogPrefix' exceeded $TimeoutSeconds seconds; terminating Java process tree."
        & taskkill.exe /PID $process.Id /T /F 2>&1 | Write-Host
        $process.WaitForExit(10000) | Out-Null
    } else {
        $process.WaitForExit()
    }

    $combined = Read-CombinedOutput -StdoutLog $stdoutLog -StderrLog $stderrLog
    if (-not $finished) {
        Write-Host "----- timed out Hytale probe output: $LogPrefix -----"
        Write-Host $combined
        Write-Host "----- end timed out Hytale probe output: $LogPrefix -----"
        throw "Hytale probe '$LogPrefix' exceeded its $TimeoutSeconds second process budget."
    }

    return [PSCustomObject]@{
        Process = $process
        Combined = $combined
    }
}

function Assert-CommonRuntimeHealth {
    param([Parameter(Mandatory = $true)] [string] $Combined)
    if ($Combined.Contains('client.disconnection.shutdownReason.missingAssets.failedToLoad')) {
        throw 'Hytale still reports missing assets while using --assets with the local Assets.zip.'
    }
    if ($Combined.Contains("chunk isn't currently loaded")) {
        throw 'A Civ runtime scenario touched an unloaded chunk.'
    }
}

function Run-WoodcutterScenario {
    param([Parameter(Mandatory = $true)] [string] $RuntimeDir)

    $result = Start-HytaleProbe -RuntimeDir $RuntimeDir -LogPrefix 'woodcutter' -BootCommand 'civwoodcutterprobe'
    $combined = $result.Combined
    Write-Host '----- Hytale woodcutter output -----'
    Write-Host $combined
    Write-Host '----- end Hytale woodcutter output -----'

    Assert-CommonRuntimeHealth -Combined $combined
    if ($combined.Contains('CIV_WOODCUTTER_RUNTIME_FAIL')) {
        throw 'The Civ woodcutter runtime scenario reported failure.'
    }
    Assert-Evidence -Combined $combined -RequiredEvidence @(
        'Loaded pack: Hytale:Hytale from Assets.zip',
        'Loaded pack: Civilizations:HytaleCivAssets from hytale-civ-assets',
        'Enabled plugin Civilizations:HytaleCiv',
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
        'Shutdown completed!'
    )
    if ($result.Process.ExitCode -ne 0) {
        Write-Warning "Woodcutter server exited with code $($result.Process.ExitCode) after complete PASS and clean shutdown evidence; treating runtime evidence as authoritative."
    }
    Write-Host 'Real Hytale woodcutter scenario passed.'
}

function Run-MineSupportScenario {
    param([Parameter(Mandatory = $true)] [string] $RuntimeDir)

    $result = Start-HytaleProbe `
        -RuntimeDir $RuntimeDir `
        -LogPrefix 'minesupport' `
        -BootCommand 'civtest' `
        -JvmProperties @('-Dcivilizations.mineSupportProbe=true')
    $combined = $result.Combined
    Write-Host '----- Hytale mine support output -----'
    Write-Host $combined
    Write-Host '----- end Hytale mine support output -----'

    Assert-CommonRuntimeHealth -Combined $combined
    if ($combined.Contains('CIV_MINE_SUPPORT_RUNTIME_FAIL')) {
        throw 'The Civ mine support runtime scenario reported failure.'
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
        'Shutdown completed!'
    )
    if ($result.Process.ExitCode -ne 0) {
        Write-Warning "Mine support server exited with code $($result.Process.ExitCode) after complete PASS and clean shutdown evidence; treating runtime evidence as authoritative."
    }
    Write-Host 'Real Hytale mine support scenario passed.'
}

function Run-SoldierScenario {
    param([Parameter(Mandatory = $true)] [string] $RuntimeDir)

    $result = Start-HytaleProbe -RuntimeDir $RuntimeDir -LogPrefix 'soldier' -BootCommand 'civsoldierprobe'
    $combined = $result.Combined
    Write-Host '----- Hytale soldier output -----'
    Write-Host $combined
    Write-Host '----- end Hytale soldier output -----'

    Assert-CommonRuntimeHealth -Combined $combined
    if ($combined.Contains('CIV_SOLDIER_RUNTIME_FAIL')) {
        throw 'The Civ soldier runtime scenario reported failure.'
    }
    Assert-Evidence -Combined $combined -RequiredEvidence @(
        'Loaded pack: Hytale:Hytale from Assets.zip',
        'Loaded pack: Civilizations:HytaleCivAssets from hytale-civ-assets',
        'Enabled plugin Civilizations:HytaleCiv',
        'Hytale Server Booted!',
        'Console executed command: civsoldierprobe',
        'CIV_SOLDIER_RUNTIME_STARTED',
        'CIV_SOLDIER_HOSTILE_ROLE role=',
        'CIV_SOLDIER_FIXTURE_READY',
        'weapon=Weapon_Sword_Iron',
        'CIV_SOLDIER_TARGET_ACQUIRED',
        'CIV_SOLDIER_CHASE_OBSERVED',
        'CIV_SOLDIER_RECIPROCAL_DAMAGE',
        'CIV_SOLDIER_MANUAL_MOVE_ISSUED',
        'CIV_SOLDIER_COMBAT_INTERRUPTED',
        'CIV_SOLDIER_COMBAT_RESUMED',
        'CIV_SOLDIER_RUNTIME_PASS',
        'Shutdown completed!'
    )
    if ($result.Process.ExitCode -ne 0) {
        Write-Warning "Soldier server exited with code $($result.Process.ExitCode) after complete PASS and clean shutdown evidence; treating runtime evidence as authoritative."
    }
    Write-Host 'Real Hytale soldier combat scenario passed.'
}

function Run-WarmRuntimeScenario {
    param([Parameter(Mandatory = $true)] [string] $RuntimeDir)

    $result = Start-HytaleProbe -RuntimeDir $RuntimeDir -LogPrefix 'warmruntime' -BootCommand 'civwarmruntimebenchmark' -TimeoutSeconds 90
    $combined = $result.Combined
    Write-Host '----- Hytale warm gameplay suite output -----'
    Write-Host $combined
    Write-Host '----- end Hytale warm gameplay suite output -----'

    Assert-CommonRuntimeHealth -Combined $combined
    if ($combined.Contains('CIV_WARM_RUNTIME_BENCHMARK_FAIL') -or $combined.Contains('CIV_WARM_SUITE_FAIL')) {
        throw 'The warm Hytale gameplay suite reported failure.'
    }
    if ($combined.Contains('CIV_WOODCUTTER_RUNTIME_FAIL') -or $combined.Contains('CIV_MINE_SUPPORT_RUNTIME_FAIL')) {
        throw 'A real gameplay probe inside the warm Hytale suite reported failure.'
    }
    Assert-Evidence -Combined $combined -RequiredEvidence @(
        'Loaded pack: Hytale:Hytale from Assets.zip',
        'Loaded pack: Civilizations:HytaleCivAssets from hytale-civ-assets',
        'Enabled plugin Civilizations:HytaleCiv',
        'Hytale Server Booted!',
        'Console executed command: civwarmruntimebenchmark',
        'CIV_WARM_RUNTIME_BENCHMARK_STARTED realScenarios=woodcutter,minesupport warmProcess=true',
        'CIV_WARM_SUITE_STARTED',
        'CIV_WOODCUTTER_RUNTIME_STARTED',
        'CIV_MINE_SUPPORT_RUNTIME_STARTED',
        'CIV_WOODCUTTER_RUNTIME_PASS',
        'CIV_MINE_SUPPORT_RUNTIME_PASS',
        'CIV_WARM_SUITE_SCENARIO_PASS scenario=woodcutter',
        'CIV_WARM_SUITE_SCENARIO_PASS scenario=minesupport',
        'CIV_WARM_SUITE_RESULT scenarios=2',
        'CIV_WARM_SUITE_PASS',
        'Shutdown completed!'
    )
    if ($result.Process.ExitCode -ne 0) {
        Write-Warning "Warm gameplay suite server exited with code $($result.Process.ExitCode) after complete PASS and clean shutdown evidence; treating runtime evidence as authoritative."
    }
    Write-Host 'Warm Hytale gameplay suite passed in one server process.'
}

function Run-MineAtmosphereScenario {
    param([Parameter(Mandatory = $true)] [string] $RuntimeDir)

    $result = Start-HytaleProbe `
        -RuntimeDir $RuntimeDir `
        -LogPrefix 'mineatmosphere' `
        -BootCommand 'civmineatmosphereprobe' `
        -TimeoutSeconds 60
    $combined = $result.Combined
    Write-Host '----- Hytale mine atmosphere output -----'
    Write-Host $combined
    Write-Host '----- end Hytale mine atmosphere output -----'

    Assert-CommonRuntimeHealth -Combined $combined
    if ($combined.Contains('CIV_MINE_ATMOSPHERE_RUNTIME_FAIL')) {
        throw 'The Civ mine atmosphere runtime scenario reported failure.'
    }
    Assert-Evidence -Combined $combined -RequiredEvidence @(
        'Loaded pack: Hytale:Hytale from Assets.zip',
        'Loaded pack: Civilizations:HytaleCivAssets from hytale-civ-assets',
        'Enabled plugin Civilizations:HytaleCiv',
        'Hytale Server Booted!',
        'Console executed command: civmineatmosphereprobe',
        'CIV_MINE_ATMOSPHERE_RUNTIME_STARTED',
        'CIV_MINE_ATMOSPHERE_DECORATION kind=BARREL assets=',
        'CIV_MINE_ATMOSPHERE_DECORATION kind=CRATE assets=',
        'CIV_MINE_ATMOSPHERE_DECORATION kind=TIMBER_PILE assets=',
        'CIV_MINE_ATMOSPHERE_DECORATION kind=MATERIAL_PILE assets=',
        'CIV_MINE_ATMOSPHERE_DECORATION kind=HANGING_CHAIN assets=',
        'CIV_MINE_ATMOSPHERE_DECORATION kind=HANGING_LANTERN assets=',
        'CIV_MINE_ATMOSPHERE_FIXTURE_READY',
        'CIV_MINE_ATMOSPHERE_NAVIGATION_PASS',
        'CIV_MINE_ATMOSPHERE_RUNTIME_PASS',
        'Shutdown completed!'
    )
    if ($result.Process.ExitCode -ne 0) {
        Write-Warning "Mine atmosphere server exited with code $($result.Process.ExitCode) after complete PASS and clean shutdown evidence; treating runtime evidence as authoritative."
    }
    Write-Host 'Real Hytale mine atmosphere scenario passed.'
}

function Run-PersistenceScenario {
    param([Parameter(Mandatory = $true)] [string] $RuntimeDir)

    $prepare = Start-HytaleProbe `
        -RuntimeDir $RuntimeDir `
        -LogPrefix 'persistence.prepare' `
        -BootCommand 'civpersistenceprobe' `
        -JvmProperties @('-Dcivilizations.persistenceProbeStage=prepare')
    $prepareCombined = $prepare.Combined
    Write-Host '----- Hytale persistence prepare output -----'
    Write-Host $prepareCombined
    Write-Host '----- end Hytale persistence prepare output -----'

    if ($prepareCombined.Contains('CIV_PERSISTENCE_PROBE_FAIL')) {
        throw 'The persistence prepare stage reported failure.'
    }
    Assert-Evidence -Combined $prepareCombined -RequiredEvidence @(
        'CIV_PERSISTENCE_PREPARED uuid=',
        'CIV_PERSISTENCE_WORKPLACE_INDEXED_PREPARE',
        'CIV_PERSISTENCE_PREPARE_PASS',
        'Shutdown completed!'
    )
    if ($prepare.Process.ExitCode -ne 0) {
        Write-Warning "Persistence prepare server exited with code $($prepare.Process.ExitCode) after complete PASS and clean shutdown evidence; treating runtime evidence as authoritative."
    }

    $uuidMatch = [regex]::Match(
        $prepareCombined,
        'CIV_PERSISTENCE_PREPARED uuid=([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})'
    )
    if (-not $uuidMatch.Success) {
        throw 'Could not extract the prepared Civ inhabitant UUID from server output.'
    }
    $entityUuid = $uuidMatch.Groups[1].Value

    $restore = Start-HytaleProbe `
        -RuntimeDir $RuntimeDir `
        -LogPrefix 'persistence.restore' `
        -BootCommand 'civpersistenceprobe' `
        -JvmProperties @(
            '-Dcivilizations.persistenceProbeStage=restore',
            "-Dcivilizations.persistenceProbeEntityUuid=$entityUuid"
        )
    $restoreCombined = $restore.Combined
    Write-Host '----- Hytale persistence restore output -----'
    Write-Host $restoreCombined
    Write-Host '----- end Hytale persistence restore output -----'

    if ($restoreCombined.Contains('CIV_PERSISTENCE_PROBE_FAIL')) {
        throw 'The persistence restore stage reported failure.'
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
    if ($restore.Process.ExitCode -ne 0) {
        Write-Warning "Persistence restore server exited with code $($restore.Process.ExitCode) after complete PASS and clean shutdown evidence; treating runtime evidence as authoritative."
    }
    Write-Host 'Real Hytale persistence scenario passed across two separate server processes.'
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

$hasWoodcutter = $Scenarios -contains 'woodcutter'
$hasMineSupport = $Scenarios -contains 'minesupport'
$explicitWarm = $Scenarios -contains 'warmruntime'
$bundleGameplay = $explicitWarm -or ($hasWoodcutter -and $hasMineSupport)

if ($bundleGameplay) {
    Write-Host '===== BEGIN Hytale warm gameplay suite: woodcutter + minesupport ====='
    $runtimeDir = Prepare-Runtime -Scenario 'warm-gameplay'
    try {
        Run-WarmRuntimeScenario -RuntimeDir $runtimeDir
    } finally {
        Remove-Runtime -RuntimeDir $runtimeDir
    }
    Write-Host '===== END Hytale warm gameplay suite ====='
}

foreach ($scenario in $Scenarios) {
    if ($scenario -eq 'warmruntime') {
        continue
    }
    if ($bundleGameplay -and ($scenario -eq 'woodcutter' -or $scenario -eq 'minesupport')) {
        continue
    }

    Write-Host "===== BEGIN Hytale runtime scenario: $scenario ====="
    $runtimeDir = Prepare-Runtime -Scenario $scenario
    try {
        switch ($scenario) {
            'woodcutter' { Run-WoodcutterScenario -RuntimeDir $runtimeDir }
            'persistence' { Run-PersistenceScenario -RuntimeDir $runtimeDir }
            'reload' { & (Join-Path $PSScriptRoot 'hytale-reload-probe.ps1') -RuntimeDir $runtimeDir }
            'deployment' { . (Join-Path $PSScriptRoot 'hytale-deployment-scenario.ps1'); Run-DeploymentScenario -RuntimeDir $runtimeDir }
            'deploymentrollback' { . (Join-Path $PSScriptRoot 'hytale-deployment-scenario.ps1'); Run-DeploymentScenario -RuntimeDir $runtimeDir -InjectFailure }
            'minesupport' { Run-MineSupportScenario -RuntimeDir $runtimeDir }
            'soldier' { Run-SoldierScenario -RuntimeDir $runtimeDir }
            'mineatmosphere' { Run-MineAtmosphereScenario -RuntimeDir $runtimeDir }
            default { throw "No runtime implementation exists for registered scenario: $scenario" }
        }
    } finally {
        Remove-Runtime -RuntimeDir $runtimeDir
    }
    Write-Host "===== END Hytale runtime scenario: $scenario ====="
}