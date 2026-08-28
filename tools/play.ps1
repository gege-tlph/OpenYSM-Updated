<#
  play.ps1 - Launch the dev dedicated server or a dev client, with logs on disk.

  Every process writes to tools-logs\<name>.log so the agent driving the session
  never has to keep game output in memory; use logscan.ps1 to summarise it.

  Usage:
    .\tools\play.ps1 -Role server -WaitFor 'Done \(' -TimeoutSec 300
    .\tools\play.ps1 -Role client -Name Alice -GameDir fabric\run-client-a -Server localhost:25565 -WaitFor 'Sync state: IDLE'
    .\tools\play.ps1 -Role client -Name Solo  -GameDir fabric\run-client-a -World playtest2
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('server','client')][string]$Role,
    [string]$Name       = 'Player',
    [string]$GameDir    = '',
    [string]$Server     = '',
    [string]$World      = '',
    [int]$Width         = 1280,
    [int]$Height        = 720,
    [string]$WaitFor    = '',
    [int]$TimeoutSec    = 420,
    [string]$LogDir     = 'tools-logs',
    [switch]$EnableRcon,
    [string]$RconPassword = 'ysmgate',
    [int]$RconPort      = 25575
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot\lib-env.ps1"
$jdk = Use-Jdk25            # 26.1.2 will not configure under the machine default JDK 21
Write-Host "[play] JAVA_HOME=$jdk"
if (-not [System.IO.Path]::IsPathRooted($LogDir)) { $LogDir = Join-Path $repoRoot $LogDir }
if (-not (Test-Path $LogDir)) { New-Item -ItemType Directory -Path $LogDir -Force | Out-Null }

$tag = if ($Role -eq 'server') { 'server' } else { $Name.ToLowerInvariant() }
$log = Join-Path $LogDir "$tag.log"
if (Test-Path $log) { Remove-Item $log -Force }

if ($Role -eq 'server') {
    $runDir = Join-Path $repoRoot 'fabric\run'
    if ($EnableRcon) {
        $props = Join-Path $runDir 'server.properties'
        if (Test-Path $props) {
            $lines = Get-Content $props
            $map = @{ 'enable-rcon' = 'true'; 'rcon.password' = $RconPassword; 'rcon.port' = "$RconPort"; 'broadcast-rcon-to-ops' = 'false' }
            foreach ($k in $map.Keys) {
                if ($lines -match "^$([regex]::Escape($k))=") {
                    $lines = $lines -replace "^$([regex]::Escape($k))=.*", "$k=$($map[$k])"
                } else {
                    $lines += "$k=$($map[$k])"
                }
            }
            Set-Content -Path $props -Value $lines -Encoding ASCII
            Write-Host "[play] rcon enabled in $props (port $RconPort)"
        } else {
            Write-Host "[play] server.properties not present yet; run once, then re-run with -EnableRcon"
        }
    }
    $gradleArgs = @(':fabric:runServer', '--no-daemon', '--console=plain', "--args=nogui")
} else {
    if ([string]::IsNullOrWhiteSpace($GameDir)) { Write-Error 'client role needs -GameDir'; exit 2 }
    if (-not [System.IO.Path]::IsPathRooted($GameDir)) { $GameDir = Join-Path $repoRoot $GameDir }
    if (-not (Test-Path $GameDir)) { New-Item -ItemType Directory -Path $GameDir -Force | Out-Null }

    if ($GameDir -match '\s') {
        Write-Error "game dir must not contain spaces (gradle --args cannot carry nested quoting): $GameDir"
        exit 2
    }
    $mcArgs = "--username $Name --gameDir $GameDir --width $Width --height $Height"
    if ($Server -ne '') { $mcArgs += " --quickPlayMultiplayer $Server" }
    elseif ($World -ne '') { $mcArgs += " --quickPlaySingleplayer $World" }
    # The whole --args value is one argv element; without the embedded quotes
    # Start-Process splits on spaces and gradle reads "Alice" as a task name.
    $gradleArgs = @(':fabric:runClient', '--no-daemon', '--console=plain', "--args=`"$mcArgs`"")
}

$gradlew = Join-Path $repoRoot 'gradlew.bat'
Write-Host "[play] launching $Role ($tag): $gradlew $($gradleArgs -join ' ')"
$proc = Start-Process -FilePath $gradlew -ArgumentList $gradleArgs -WorkingDirectory $repoRoot `
                      -RedirectStandardOutput $log -RedirectStandardError "$log.err" `
                      -WindowStyle Minimized -PassThru

Write-Host "[play] pid=$($proc.Id) log=$log"

if ($WaitFor -ne '') {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    $hit = $false
    while ((Get-Date) -lt $deadline) {
        if ($proc.HasExited) {
            Write-Host "[play] process exited early with code $($proc.ExitCode)"
            break
        }
        if (Test-Path $log) {
            $content = Get-Content $log -Raw -ErrorAction SilentlyContinue
            if ($content -and $content -match $WaitFor) { $hit = $true; break }
        }
        Start-Sleep -Seconds 3
    }
    if ($hit) {
        Write-Host "[play] READY: matched /$WaitFor/ after $([int]((Get-Date) - (Get-Item $log).CreationTime).TotalSeconds)s"
    } else {
        Write-Host "[play] TIMEOUT: /$WaitFor/ not seen in ${TimeoutSec}s (see $log)"
    }
}

Write-Output $proc.Id
