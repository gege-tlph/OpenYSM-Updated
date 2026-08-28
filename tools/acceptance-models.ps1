<#
  acceptance-models.ps1 - G5 "model" cluster: switch a player through every builtin
  model and capture how another client renders each one.

  Produces one labelled contact sheet plus a per-model frame-difference table, so the
  whole corpus can be judged from a single image instead of dozens of screenshots.

  Usage (server + two clients already running, viewer facing the subject):
    .\tools\acceptance-models.ps1 -Subject Alice -ViewerGameDir fabric\run-client-b
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Subject,
    [Parameter(Mandatory)][string]$ViewerGameDir,
    [string]$Texture   = 'gsl',
    [string]$OutDir    = 'tools-logs\shots\model-sweep',
    [string]$SheetPath = 'docs\porting\26.1.2\evidence\2026-08-29-builtin-model-sweep.png',
    [double]$CropX = 0.30, [double]$CropY = 0.15, [double]$CropW = 0.40, [double]$CropH = 0.75,
    [int]$SettleMs = 1500
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\lib-win.ps1"
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not [System.IO.Path]::IsPathRooted($OutDir))    { $OutDir = Join-Path $repoRoot $OutDir }
if (-not [System.IO.Path]::IsPathRooted($SheetPath)) { $SheetPath = Join-Path $repoRoot $SheetPath }
New-Item -ItemType Directory -Path $OutDir -Force | Out-Null

# Model ids come from the shipped corpus, so the sweep cannot silently skip a model.
$builtin = Join-Path $repoRoot 'common\src\main\resources\assets\yes_steve_model\builtin'
$models = Get-ChildItem $builtin -Recurse -Filter 'ysm.json' | ForEach-Object {
    $rel = $_.DirectoryName.Substring($builtin.Length).TrimStart('\')
    $rel -replace '\\', '/'
} | Sort-Object
Write-Host "[sweep] $($models.Count) builtin models"

$images = @(); $labels = @(); $rows = @(); $previous = $null
foreach ($model in $models) {
    $safe = $model -replace '[/]', '-'
    & "$PSScriptRoot\rcon.ps1" -Quiet -Command "ysm model set $Subject `"$model`" $Texture" | Out-Null
    Start-Sleep -Milliseconds $SettleMs
    $dest = Join-Path $OutDir "$safe.png"
    try {
        & "$PSScriptRoot\capture.ps1" -GameDir $ViewerGameDir -Tag $safe -OutDir $OutDir `
            -CropX $CropX -CropY $CropY -CropW $CropW -CropH $CropH -MaxWidth 190 | Out-Null
    } catch {
        Write-Host "[sweep] capture failed for $model : $($_.Exception.Message)"
        continue
    }
    $diff = if ($previous -and (Test-Path $previous)) { Get-ImageDifference -A $previous -B $dest -Step 3 } else { -1 }
    $rows += [pscustomobject]@{ Model = $model; Diff = $diff }
    $images += $dest; $labels += $model
    $previous = $dest
    Write-Host ("[sweep] {0,-28} frame-diff vs previous: {1}" -f $model, $diff)
}

$sheet = Save-ContactSheet -Images $images -Labels $labels -Destination $SheetPath -Columns 7 -CellWidth 190 -CellHeight 150
Write-Host ''
Write-Host "[sweep] contact sheet: $sheet"

$unchanged = @($rows | Where-Object { $_.Diff -ge 0 -and $_.Diff -lt 1.0 })
Write-Host ("[sweep] captured {0}/{1} models; {2} produced a near-identical frame to the previous model" -f $images.Count, $models.Count, $unchanged.Count)
if ($unchanged.Count -gt 0) { $unchanged | ForEach-Object { Write-Host ("    suspicious: {0} (diff {1})" -f $_.Model, $_.Diff) } }
