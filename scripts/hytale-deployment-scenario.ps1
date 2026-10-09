function Run-DeploymentScenario {
    param([Parameter(Mandatory = $true)][string]$RuntimeDir, [switch]$InjectFailure, [switch]$UseRealArtifacts)
    $mods = Join-Path $RuntimeDir 'mods'
    $plugins = @(Get-ChildItem -LiteralPath $mods -Filter 'hytale-civ-*.jar' -File)
    if ($plugins.Count -ne 1) { throw "Expected one sandbox Civ plugin; found $($plugins.Count)" }
    $jar = $plugins[0].FullName
    $assets = Join-Path $mods 'hytale-civ-assets'
    if (-not (Test-Path -LiteralPath $assets -PathType Container)) { throw 'Sandbox assets missing' }

    $first = Start-HytaleProbe -RuntimeDir $RuntimeDir -LogPrefix 'deploy.prepare' -BootCommand 'civpersistenceprobe' -JvmProperties @('-Dcivilizations.persistenceProbeStage=prepare') -TimeoutSeconds 75
    Write-Host $first.Combined
    if ($first.Combined.Contains('CIV_PERSISTENCE_PROBE_FAIL')) { throw 'Initial Civ persistence setup failed' }
    Assert-Evidence -Combined $first.Combined -RequiredEvidence @('CIV_PERSISTENCE_PREPARE_PASS', 'Shutdown completed!')
    $match = [regex]::Match($first.Combined, 'CIV_PERSISTENCE_PREPARED uuid=([0-9a-fA-F-]{36})')
    if (-not $match.Success) { throw 'Missing prepared NPC UUID' }
    $uuid = $match.Groups[1].Value
    Write-Host "HCIV_DEPLOY_STOPPED uuid=$uuid"

    $tx = Join-Path $RuntimeDir 'deployment-transaction'
    $backup = Join-Path $tx 'backup'
    $stage = Join-Path $tx 'stage'
    New-Item -ItemType Directory -Path $backup, $stage -Force | Out-Null
    $backupJar = Join-Path $backup $plugins[0].Name
    $backupAssets = Join-Path $backup 'hytale-civ-assets'
    Copy-Item -LiteralPath $jar -Destination $backupJar -ErrorAction Stop
    Copy-Item -LiteralPath $assets -Destination $backupAssets -Recurse -ErrorAction Stop
    $candidateJar = Join-Path $stage $plugins[0].Name
    $candidateAssets = Join-Path $stage 'hytale-civ-assets'
    Copy-Item -LiteralPath $assets -Destination $candidateAssets -Recurse -ErrorAction Stop
    if ($UseRealArtifacts) {
        $targetJars = @(Get-ChildItem -LiteralPath 'build/libs' -Filter 'hytale-civ-*.jar' -File)
        if ($targetJars.Count -ne 1) { throw "Expected one target CI artifact, found $($targetJars.Count)" }
        Copy-Item -LiteralPath $targetJars[0].FullName -Destination $candidateJar -ErrorAction Stop
        Write-Host "HCIV_DEPLOY_REAL_ARTIFACTS baseline=$env:HCIV_BASELINE_SHA target=$env:HCIV_TARGET_SHA"
    } else {
        Copy-Item -LiteralPath $jar -Destination $candidateJar -ErrorAction Stop
        # Synthetic JAR alteration remains solely for the existing isolated smoke/rollback probes.
        Add-Type -AssemblyName System.IO.Compression
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $zip = [System.IO.Compression.ZipFile]::Open($candidateJar, [System.IO.Compression.ZipArchiveMode]::Update)
        try {
            $entry = $zip.CreateEntry('META-INF/hciv-deployment-probe.txt')
            $stream = $entry.Open()
            try {
                $bytes = [System.Text.Encoding]::UTF8.GetBytes('phase2-isolated-version-b')
                $stream.Write($bytes, 0, $bytes.Length)
            } finally { $stream.Dispose() }
        } finally { $zip.Dispose() }
    }
    $beforeHash = (Get-FileHash -LiteralPath $backupJar -Algorithm SHA256).Hash
    $afterHash = (Get-FileHash -LiteralPath $candidateJar -Algorithm SHA256).Hash
    if ($beforeHash -eq $afterHash) { throw 'Staged plugin is not a distinct artifact' }
    if (-not (Test-Path -LiteralPath (Join-Path $candidateAssets 'manifest.json') -PathType Leaf)) { throw 'Staged asset manifest missing' }
    Write-Host "HCIV_DEPLOY_BACKUP_CREATED hash=$beforeHash"
    Write-Host "HCIV_DEPLOY_STAGED hash=$afterHash"

    $changed = $false
    try {
        $changed = $true
        Copy-Item -LiteralPath $candidateJar -Destination $jar -Force -ErrorAction Stop
        Remove-Item -LiteralPath $assets -Recurse -Force -ErrorAction Stop
        Copy-Item -LiteralPath $candidateAssets -Destination $assets -Recurse -ErrorAction Stop
        if ((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash -ne $afterHash) { throw 'Installed JAR hash mismatch' }
        Write-Host 'HCIV_DEPLOY_INSTALLED'
        if ($InjectFailure) { throw 'EXPECTED_DEPLOYMENT_FAILURE_INJECTION' }
        $second = Start-HytaleProbe -RuntimeDir $RuntimeDir -LogPrefix 'deploy.restore' -BootCommand 'civpersistenceprobe' -JvmProperties @('-Dcivilizations.persistenceProbeStage=restore', "-Dcivilizations.persistenceProbeEntityUuid=$uuid") -TimeoutSeconds 75
        Write-Host $second.Combined
        if ($second.Combined.Contains('CIV_PERSISTENCE_PROBE_FAIL')) { throw 'Civ restart reported failure' }
        Assert-Evidence -Combined $second.Combined -RequiredEvidence @("CIV_PERSISTENCE_RESTORED uuid=$uuid", 'CIV_PERSISTENCE_WORKPLACE_INDEXED_RESTORE', 'CIV_PERSISTENCE_RESTORE_PASS', 'Shutdown completed!')
        Write-Host 'HCIV_DEPLOY_RESTART_VERIFIED'
        Write-Host 'HCIV_DEPLOY_SCENARIO_PASS'
    } catch {
        $failure = $_
        if ($changed) {
            # Roll back only the isolated copy and retain the original runtime world.
            Copy-Item -LiteralPath $backupJar -Destination $jar -Force -ErrorAction Stop
            if (Test-Path -LiteralPath $assets) { Remove-Item -LiteralPath $assets -Recurse -Force -ErrorAction Stop }
            Copy-Item -LiteralPath $backupAssets -Destination $assets -Recurse -ErrorAction Stop
            if ((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash -ne $beforeHash) { throw 'ROLLBACK FAILURE: hash mismatch' }
            Write-Host 'HCIV_DEPLOY_ROLLBACK_RESTORED'
        }
        if ($InjectFailure -and $failure.Exception.Message -eq 'EXPECTED_DEPLOYMENT_FAILURE_INJECTION') {
            if (-not (Test-Path -LiteralPath (Join-Path $assets 'manifest.json') -PathType Leaf)) { throw 'Rollback asset manifest missing' }
            $rollback = Start-HytaleProbe -RuntimeDir $RuntimeDir -LogPrefix 'deploy.rollback' -BootCommand 'civpersistenceprobe' -JvmProperties @('-Dcivilizations.persistenceProbeStage=restore', "-Dcivilizations.persistenceProbeEntityUuid=$uuid") -TimeoutSeconds 75
            Write-Host $rollback.Combined
            if ($rollback.Combined.Contains('CIV_PERSISTENCE_PROBE_FAIL')) { throw 'Restored Civ installation failed to recover persisted NPC' }
            Assert-Evidence -Combined $rollback.Combined -RequiredEvidence @("CIV_PERSISTENCE_RESTORED uuid=$uuid", 'CIV_PERSISTENCE_RESTORE_PASS', 'Shutdown completed!')
            Write-Host 'HCIV_DEPLOY_ROLLBACK_RUNTIME_PASS'
            return
        }
        throw $failure
    }
}
