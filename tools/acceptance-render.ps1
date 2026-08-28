<#
  acceptance-render.ps1 - G5 "render" cluster: exercise the equipment/vehicle/projectile
  render paths on the subject and capture how another client draws each one.

  Every step is driven with real server commands; the result is one labelled contact
  sheet plus a pass/fail line per step.

  Usage: .\tools\acceptance-render.ps1 -Subject Alice -ViewerGameDir fabric\run-client-b
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Subject,
    [Parameter(Mandatory)][string]$ViewerGameDir,
    [string]$Model     = 'wine_fox/19_nine_tailed',
    [string]$OutDir    = 'tools-logs\shots\render-cluster',
    [string]$SheetPath = 'docs\porting\26.1.2\evidence\2026-08-29-render-cluster.png',
    [int]$SettleMs = 1800
)

$ErrorActionPreference = 'Continue'
. "$PSScriptRoot\lib-win.ps1"
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not [System.IO.Path]::IsPathRooted($OutDir))    { $OutDir = Join-Path $repoRoot $OutDir }
if (-not [System.IO.Path]::IsPathRooted($SheetPath)) { $SheetPath = Join-Path $repoRoot $SheetPath }
New-Item -ItemType Directory -Path $OutDir -Force | Out-Null

function Invoke-Rcon { param([string[]]$Commands) & "$PSScriptRoot\rcon.ps1" -Quiet -Command $Commands }

Invoke-Rcon @(
    'time set day', 'weather clear',
    "gamemode creative $Subject",
    "ysm model set $Subject `"$Model`" gsl",
    "kill @e[type=!minecraft:player]",
    "tp $Subject 0 -59 0 0 0"
) | Out-Null

# label -> setup commands. Each step leaves the world ready for one capture.
$steps = @(
    @{ Label = 'base';        Cmds = @("clear $Subject") },
    @{ Label = 'held-item';   Cmds = @("clear $Subject", "give $Subject minecraft:diamond_sword", "item replace entity $Subject weapon.mainhand with minecraft:diamond_sword") },
    @{ Label = 'armor-full';  Cmds = @("item replace entity $Subject armor.head with minecraft:diamond_helmet",
                                       "item replace entity $Subject armor.chest with minecraft:diamond_chestplate",
                                       "item replace entity $Subject armor.legs with minecraft:diamond_leggings",
                                       "item replace entity $Subject armor.feet with minecraft:diamond_boots") },
    @{ Label = 'elytra';      Cmds = @("item replace entity $Subject armor.chest with minecraft:elytra") },
    @{ Label = 'riding-boat'; Cmds = @("item replace entity $Subject armor.chest with air",
                                       "execute at $Subject run summon minecraft:oak_boat ~ ~ ~ {Tags:[`"ysmgate`"]}",
                                       "ride $Subject mount @e[type=minecraft:oak_boat,tag=ysmgate,limit=1]") },
    @{ Label = 'riding-cart'; Cmds = @("ride $Subject dismount",
                                       "kill @e[type=minecraft:oak_boat,tag=ysmgate]",
                                       "execute at $Subject run summon minecraft:minecart ~ ~ ~ {Tags:[`"ysmgate`"]}",
                                       "ride $Subject mount @e[type=minecraft:minecart,tag=ysmgate,limit=1]") },
    @{ Label = 'projectile';  Cmds = @("ride $Subject dismount",
                                       "kill @e[type=minecraft:minecart,tag=ysmgate]",
                                       "execute at $Subject run summon minecraft:arrow ~ ~1 ~1.5 {Tags:[`"ysmgate`"],NoGravity:1b}") },
    @{ Label = 'sleeping';    Cmds = @("kill @e[type=minecraft:arrow,tag=ysmgate]",
                                       "execute at $Subject run setblock ~1 ~ ~ minecraft:red_bed") }
)

$images = @(); $labels = @()
foreach ($step in $steps) {
    Invoke-Rcon $step.Cmds | Out-Null
    Start-Sleep -Milliseconds $SettleMs
    $dest = Join-Path $OutDir ("{0}.png" -f $step.Label)
    try {
        & "$PSScriptRoot\capture.ps1" -GameDir $ViewerGameDir -Tag $step.Label -OutDir $OutDir `
            -CropX 0.28 -CropY 0.12 -CropW 0.44 -CropH 0.80 -MaxWidth 210 | Out-Null
        $images += $dest; $labels += $step.Label
        Write-Host ("[render] {0,-14} captured" -f $step.Label)
    } catch {
        Write-Host ("[render] {0,-14} CAPTURE FAILED: {1}" -f $step.Label, $_.Exception.Message)
    }
}

Invoke-Rcon @("ride $Subject dismount", "kill @e[type=!minecraft:player]", "clear $Subject") | Out-Null

if ($images.Count -gt 0) {
    $sheet = Save-ContactSheet -Images $images -Labels $labels -Destination $SheetPath -Columns 4 -CellWidth 210 -CellHeight 200
    Write-Host "[render] contact sheet: $sheet"
}
Write-Host ("[render] {0}/{1} steps captured" -f $images.Count, $steps.Count)
