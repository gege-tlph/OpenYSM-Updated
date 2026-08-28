<#
  capture.ps1 - Take a real screenshot of ONE specific dev client and archive it.

  The client takes the shot itself (F2), so Minecraft writes its own framebuffer
  into <GameDir>\screenshots\. The identity of the captured client is therefore
  proven by the file's location, not by which window happened to be on top.

  Usage:
    .\tools\capture.ps1 -GameDir fabric\run-client-a -Tag alice-sees-bob
    .\tools\capture.ps1 -GameDir fabric\run-client-b -Tag bob-sees-alice -OutDir docs\porting\26.1.2\evidence
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$GameDir,
    [Parameter(Mandatory)][string]$Tag,
    [string]$OutDir      = "docs\porting\26.1.2\evidence",
    [string]$Token       = "",
    [int]$MaxWidth       = 960,
    [int]$TimeoutSec     = 20,
    [switch]$KeepOriginal,
    [double]$CropX = 0, [double]$CropY = 0, [double]$CropW = 1, [double]$CropH = 1,
    # Last-resort fallback, OFF by default: posting raw WM_KEYDOWN into the GLFW
    # window coincided with an EXCEPTION_ACCESS_VIOLATION inside glfw.dll during
    # RenderSystem.pollEvents (fabric/run/hs_err_pid26596.log, 2026-08-29).
    [switch]$AllowPostMessage
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\lib-win.ps1"

$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not [System.IO.Path]::IsPathRooted($GameDir)) { $GameDir = Join-Path $repoRoot $GameDir }
if (-not [System.IO.Path]::IsPathRooted($OutDir))  { $OutDir  = Join-Path $repoRoot $OutDir }
if ([string]::IsNullOrWhiteSpace($Token)) { $Token = Split-Path -Leaf $GameDir }

$shotDir = Join-Path $GameDir 'screenshots'
$before  = @{}
if (Test-Path $shotDir) {
    Get-ChildItem $shotDir -Filter *.png -ErrorAction SilentlyContinue | ForEach-Object { $before[$_.Name] = $true }
}

$win = Get-McWindow -Token $Token
if (-not $win) { Write-Error "No Minecraft window found for token '$Token'. Is that client running?"; exit 2 }
Write-Host "[capture] token=$Token pid=$($win.ProcessId) hwnd=$($win.Hwnd) title='$($win.Title)'"

$fg = Confirm-McForeground -Hwnd $win.Hwnd
if (-not $fg) { Write-Host '[capture] warning: window did not reach the foreground; F2 may go elsewhere' }
Send-McKey -Name 'F2' -HoldMs 80

function Wait-NewShot {
    param([int]$Seconds)
    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-Path $shotDir) {
            $new = Get-ChildItem $shotDir -Filter *.png -ErrorAction SilentlyContinue |
                   Where-Object { -not $before.ContainsKey($_.Name) } |
                   Sort-Object LastWriteTime -Descending
            if ($new) {
                Start-Sleep -Milliseconds 400   # let the PNG finish writing
                return $new[0]
            }
        }
        Start-Sleep -Milliseconds 300
    }
    return $null
}

$shot = Wait-NewShot -Seconds $TimeoutSec
if (-not $shot) {
    Write-Host "[capture] no screenshot yet (foreground ok=$fg); re-focusing and sending F2 again"
    [void](Confirm-McForeground -Hwnd $win.Hwnd)
    Start-Sleep -Milliseconds 600
    Send-McKey -Name 'F2' -HoldMs 120
    $shot = Wait-NewShot -Seconds $TimeoutSec
}
if (-not $shot -and $AllowPostMessage) {
    Write-Host '[capture] falling back to PostMessage (see -AllowPostMessage note: crashed GLFW once)'
    Send-McKey -Name 'F2' -Hwnd $win.Hwnd -PostMessage -HoldMs 80
    $shot = Wait-NewShot -Seconds $TimeoutSec
}
if (-not $shot) {
    Write-Error "[capture] FAILED: no new PNG in $shotDir after two attempts."
    exit 3
}

$dest = Join-Path $OutDir ("{0}.png" -f $Tag)
if ($CropX -ne 0 -or $CropY -ne 0 -or $CropW -ne 1 -or $CropH -ne 1) {
    $info = Save-CroppedImage -Source $shot.FullName -Destination $dest -X $CropX -Y $CropY -W $CropW -H $CropH -MaxWidth $MaxWidth
} else {
    $info = Save-ResizedImage -Source $shot.FullName -Destination $dest -MaxWidth $MaxWidth
}
if (-not $KeepOriginal) { Remove-Item $shot.FullName -Force -ErrorAction SilentlyContinue }

Write-Host ("[capture] OK  {0}  ({1}x{2})  source={3}" -f $info.Path, $info.Width, $info.Height, $shot.Name)
Write-Output $info.Path
