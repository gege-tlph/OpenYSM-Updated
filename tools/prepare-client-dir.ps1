<#
  prepare-client-dir.ps1 - Seed a dev client game directory for unattended acceptance runs.

  Without this the client stops on Minecraft's first-run accessibility onboarding
  (quickPlay never fires), pauses rendering whenever another client takes focus,
  and shows OpenYSM's disclaimer GUI over the first frame.

  Only the run directory is touched - shipped defaults are not changed.

  Usage: .\tools\prepare-client-dir.ps1 -GameDir fabric\run-client-a
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$GameDir,
    [int]$GuiScale = 2,
    [int]$RenderDistance = 8
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not [System.IO.Path]::IsPathRooted($GameDir)) { $GameDir = Join-Path $repoRoot $GameDir }
New-Item -ItemType Directory -Path $GameDir -Force | Out-Null
New-Item -ItemType Directory -Path (Join-Path $GameDir 'config') -Force | Out-Null

# --- options.txt -------------------------------------------------------------
$options = [ordered]@{
    'version'              = '5062'
    'onboardAccessibility' = 'false'   # otherwise the client sits on the welcome screen
    'pauseOnLostFocus'     = 'false'   # keep rendering while the other client is focused
    'fullscreen'           = 'false'
    'guiScale'             = "$GuiScale"
    'renderDistance'       = "$RenderDistance"
    'simulationDistance'   = '8'
    'fov'                  = '0.0'
    'gamma'                = '1.0'
    'maxFps'               = '60'
    'enableVsync'          = 'false'
    'narrator'             = '0'
    'tutorialStep'         = 'none'
    'joinedFirstServer'    = 'true'
    'skipMultiplayerWarning' = 'true'
    'showSubtitles'        = 'false'
    'soundCategory_master' = '0.0'
}
$optionsPath = Join-Path $GameDir 'options.txt'
$existing = @{}
if (Test-Path $optionsPath) {
    foreach ($line in Get-Content $optionsPath) {
        $idx = $line.IndexOf(':')
        if ($idx -gt 0) { $existing[$line.Substring(0, $idx)] = $line.Substring($idx + 1) }
    }
}
foreach ($k in $options.Keys) { $existing[$k] = $options[$k] }
$lines = $existing.Keys | Sort-Object | ForEach-Object { "$_`:$($existing[$_])" }
[System.IO.File]::WriteAllLines($optionsPath, $lines, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "[prepare] options.txt written ($($lines.Count) keys)"

# --- OpenYSM client config ---------------------------------------------------
# Copy from an existing run dir if present, then turn the disclaimer off.
# Must be written WITHOUT a BOM: night-config rejects a BOM with
# "Invalid bare key: '<BOM>[general'" and silently recreates the file.
$ysmCfg = Join-Path $GameDir 'config\yes_steve_model-client.toml'
if (-not (Test-Path $ysmCfg)) {
    $donor = Join-Path $repoRoot 'fabric\run\config\yes_steve_model-client.toml'
    if (Test-Path $donor) { Copy-Item $donor $ysmCfg -Force }
}
if (Test-Path $ysmCfg) {
    $raw = [System.IO.File]::ReadAllText($ysmCfg)
    $raw = $raw -replace '(?m)^(\s*)DisclaimerShow\s*=.*$', '$1DisclaimerShow = false'
    [System.IO.File]::WriteAllText($ysmCfg, $raw, (New-Object System.Text.UTF8Encoding($false)))
    $shown = (Select-String -Path $ysmCfg -Pattern 'DisclaimerShow').Line.Trim()
    Write-Host "[prepare] $shown (no BOM)"
} else {
    Write-Host '[prepare] no OpenYSM client config to seed; defaults will be generated'
}

Write-Host "[prepare] ready: $GameDir"
