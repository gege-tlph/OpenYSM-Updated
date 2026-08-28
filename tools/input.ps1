<#
  input.ps1 - Drive ONE specific dev client with real OS-level input.

  Everything is delivered as genuine Windows input to that client's window, so
  what is exercised is the shipped key bindings / GUI code, not a test hook.

  Usage:
    .\tools\input.ps1 -GameDir fabric\run-client-a -Keys Y
    .\tools\input.ps1 -GameDir fabric\run-client-a -Keys W -HoldMs 1500
    .\tools\input.ps1 -GameDir fabric\run-client-a -Chat "/ysm model set Alice misc/1_alex gsl"
    .\tools\input.ps1 -GameDir fabric\run-client-a -ClickX 640 -ClickY 400
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$GameDir,
    [string]$Token   = "",
    [string[]]$Keys  = @(),
    [string]$Chat    = "",
    [string]$Text    = "",
    [int]$ClickX     = -1,
    [int]$ClickY     = -1,
    [int]$HoldMs     = 60,
    [int]$GapMs      = 250,
    [switch]$NoFocus
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\lib-win.ps1"

$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not [System.IO.Path]::IsPathRooted($GameDir)) { $GameDir = Join-Path $repoRoot $GameDir }
if ([string]::IsNullOrWhiteSpace($Token)) { $Token = Split-Path -Leaf $GameDir }

$win = Get-McWindow -Token $Token
if (-not $win) { Write-Error "No Minecraft window found for token '$Token'."; exit 2 }
if (-not $NoFocus) { [void](Set-McForeground -Hwnd $win.Hwnd) }
Write-Host "[input] token=$Token pid=$($win.ProcessId) hwnd=$($win.Hwnd)"

foreach ($k in $Keys) {
    Send-McKey -Name $k -HoldMs $HoldMs
    Write-Host "[input] key $k (hold ${HoldMs}ms)"
    Start-Sleep -Milliseconds $GapMs
}

if ($Chat -ne "") {
    Send-McKey -Name 'T' -HoldMs 60           # open chat
    Start-Sleep -Milliseconds 400
    Send-McText -Text $Chat
    Start-Sleep -Milliseconds 200
    Send-McKey -Name 'ENTER' -HoldMs 60
    Write-Host "[input] chat sent: $Chat"
    Start-Sleep -Milliseconds $GapMs
}

if ($Text -ne "") {
    Send-McText -Text $Text
    Write-Host "[input] typed: $Text"
}

if ($ClickX -ge 0 -and $ClickY -ge 0) {
    Send-McClick -Hwnd $win.Hwnd -X $ClickX -Y $ClickY
    $rect = Get-McClientRect -Hwnd $win.Hwnd
    Write-Host "[input] click at client ($ClickX,$ClickY) of $($rect.Width)x$($rect.Height)"
}
