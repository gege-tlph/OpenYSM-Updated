<#
  hotswap.ps1 - Recompile and push changed classes into a RUNNING dev client.

  Saves the ~40s-2min client restart per iteration. Works for method-body edits;
  adding/removing methods or fields, changing signatures, or touching mixin targets
  still needs a restart (the script says so rather than silently doing nothing).

  Requires the client to have been started with:
      .\tools\play.ps1 -Role client ... -Jdwp

  Usage:
    .\tools\hotswap.ps1                       # compile, then redefine changed classes
    .\tools\hotswap.ps1 -Port 5005
#>
[CmdletBinding()]
param(
    [int]$Port = 5005,
    [string]$Project = ':common',
    [switch]$SkipCompile
)

$ErrorActionPreference = 'Stop'
. "$PSScriptRoot\lib-env.ps1"
$repoRoot = Split-Path -Parent $PSScriptRoot
$rt = Use-Jbr
Write-Host ("[hotswap] JAVA_HOME={0}{1}" -f $rt.Home, $(if ($rt.IsJbr) { ' (JetBrains Runtime)' } else { ' (stock JDK - only trivial redefinitions will apply)' }))

if (-not $SkipCompile) {
    $log = Join-Path $repoRoot 'tools-logs\hotswap-compile.log'
    $task = "$Project" + ':compileJava'
    Write-Host "[hotswap] compiling $task"
    $p = Start-Process -FilePath (Join-Path $repoRoot 'gradlew.bat') `
                       -ArgumentList @($task, '--no-daemon', '--console=plain') `
                       -WorkingDirectory $repoRoot -RedirectStandardOutput $log `
                       -RedirectStandardError "$log.err" -NoNewWindow -PassThru -Wait
    if ($p.ExitCode -ne 0) {
        Write-Host '[hotswap] COMPILE FAILED:'
        Select-String -Path "$log.err", $log -Pattern '错误:|error:' -ErrorAction SilentlyContinue |
            Select-Object -First 5 | ForEach-Object { Write-Host "    $($_.Line.Trim())" }
        exit 1
    }
}

# jdb is the only redefinition client that ships with the JDK; drive it non-interactively.
$jdb = Join-Path $rt.Home 'bin\jdb.exe'
if (-not (Test-Path $jdb)) { Write-Error "jdb not found at $jdb (need a JDK, not a JRE)"; exit 2 }

$classesDir = Join-Path $repoRoot 'common\build\classes\java\main'
if (-not (Test-Path $classesDir)) { Write-Error "no compiled classes at $classesDir"; exit 2 }

# Redefine every class whose .class file is newer than the last hot swap, so an edit
# spanning several files lands in one pass instead of silently applying only the first.
$stampFile = Join-Path $repoRoot 'tools-logs\hotswap.stamp'
$since = if (Test-Path $stampFile) { (Get-Item $stampFile).LastWriteTime } else { (Get-Date).AddMinutes(-10) }
$changed = @(Get-ChildItem $classesDir -Recurse -Filter *.class -ErrorAction SilentlyContinue |
             Where-Object { $_.LastWriteTime -gt $since -and $_.Name -notmatch '\$\d+\.class$' })
if ($changed.Count -eq 0) {
    Write-Host '[hotswap] no class files changed since the last swap; nothing to do'
    exit 0
}
$lines = @()
foreach ($c in $changed) {
    $rel = $c.FullName.Substring($classesDir.Length + 1)
    $fqcn = ($rel -replace '\.class$', '').Replace([string][char]92, '.')
    $lines += "redefine $fqcn $($c.FullName)"
    Write-Host "[hotswap] redefining $fqcn"
}
$lines += 'cont'
$lines += 'quit'
$script = Join-Path $env:TEMP 'ysm-hotswap-jdb.txt'
$lines | Set-Content $script -Encoding ASCII

Write-Host "[hotswap] attaching to port $Port and redefining"
# PowerShell has no stdin redirection operator; feed jdb through cmd.exe instead.
$outFile = Join-Path $env:TEMP 'ysm-hotswap-jdb.out'
# Force the socket connector: on Windows jdb defaults to shared memory and fails to attach.
$cmd = '"{0}" -connect com.sun.jdi.SocketAttach:hostname=localhost,port={1} < "{2}" > "{3}" 2>&1' -f $jdb, $Port, $script, $outFile
& cmd.exe /c $cmd | Out-Null
$text = if (Test-Path $outFile) { Get-Content $outFile -Raw } else { '' }
if ($text -match 'Unable to attach|Connection refused|shmemBase_attach|无法附加') {
    Write-Host '[hotswap] could not attach - was the client started with -Jdwp?'
    exit 3
}
if ($text -match 'schema change|hierarchy change|add method|delete method') {
    Write-Host '[hotswap] REJECTED: the edit changes class shape (fields/methods/signature).'
    Write-Host '           Restart the client instead - hot swap only replaces method bodies.'
    exit 4
}
New-Item -ItemType File -Path $stampFile -Force | Out-Null
Write-Host ('[hotswap] redefinition submitted for {0} class(es)' -f $changed.Count)
$text -split "`n" | Select-Object -First 12 | ForEach-Object { if ($_.Trim()) { Write-Host "    $($_.Trim())" } }
