# lib-env.ps1 - Toolchain resolution shared by the gate and run scripts.
# Dot-source: . "$PSScriptRoot\lib-env.ps1"
#
# Minecraft 26.1.2 refuses to configure under Gradle running on Java 21, and the
# machine's default JAVA_HOME still points at a JDK 21. Every script that shells
# out to gradlew must therefore pin Java 25 explicitly.

function Get-Jdk25Home {
    <# .SYNOPSIS Absolute path of a Java 25 home, or throws with what it looked at. #>
    if ($env:YSM_JDK25 -and (Test-Path (Join-Path $env:YSM_JDK25 'bin\java.exe'))) {
        return $env:YSM_JDK25
    }
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'release'))) {
        $rel = Get-Content (Join-Path $env:JAVA_HOME 'release') -ErrorAction SilentlyContinue
        if ($rel -match 'JAVA_VERSION="25') { return $env:JAVA_HOME }
    }
    $candidates = @()
    $jdkRoot = Join-Path $env:USERPROFILE '.gradle\jdks'
    if (Test-Path $jdkRoot) {
        $candidates += Get-ChildItem $jdkRoot -Directory -ErrorAction SilentlyContinue
    }
    foreach ($base in @('C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Java')) {
        if (Test-Path $base) { $candidates += Get-ChildItem $base -Directory -ErrorAction SilentlyContinue }
    }
    foreach ($c in $candidates) {
        $java = Join-Path $c.FullName 'bin\java.exe'
        $rel  = Join-Path $c.FullName 'release'
        if ((Test-Path $java) -and (Test-Path $rel)) {
            $txt = Get-Content $rel -ErrorAction SilentlyContinue
            if ($txt -match 'JAVA_VERSION="25') { return $c.FullName }
        }
    }
    throw "No Java 25 home found. Set YSM_JDK25 to a JDK 25 install (looked under $jdkRoot and Program Files)."
}

function Use-Jdk25 {
    <# .SYNOPSIS Point JAVA_HOME/PATH at Java 25 for this process and its children. #>
    $home25 = Get-Jdk25Home
    $env:JAVA_HOME = $home25
    $env:PATH = (Join-Path $home25 'bin') + ';' + $env:PATH
    return $home25
}

function Get-JbrHome {
    <#
      .SYNOPSIS Path of the JetBrains Runtime 25 install, or $null.
      .DESCRIPTION JBR ships enhanced HotSwap ("DCEVM"), which lets a running dev client pick
      up method-body edits without the ~40s-2min restart the acceptance loop otherwise pays.
    #>
    if ($env:YSM_JBR -and (Test-Path (Join-Path $env:YSM_JBR 'bin\java.exe'))) { return $env:YSM_JBR }
    $root = Join-Path $env:USERPROFILE '.gradle\jdks'
    if (-not (Test-Path $root)) { return $null }
    $candidates = Get-ChildItem $root -Directory -Filter 'jbr*' -ErrorAction SilentlyContinue
    foreach ($c in $candidates) {
        $direct = Join-Path $c.FullName 'bin\java.exe'
        if (Test-Path $direct) { return $c.FullName }
        $nested = Get-ChildItem $c.FullName -Directory -ErrorAction SilentlyContinue |
                  Where-Object { Test-Path (Join-Path $_.FullName 'bin\java.exe') } |
                  Select-Object -First 1
        if ($nested) { return $nested.FullName }
    }
    return $null
}

function Use-Jbr {
    <#
      .SYNOPSIS Point JAVA_HOME at JetBrains Runtime 25 if present, else fall back to any JDK 25.
      .OUTPUTS The home that was selected, plus whether it is JBR.
    #>
    $jbr = Get-JbrHome
    if ($jbr) {
        $env:JAVA_HOME = $jbr
        $env:PATH = (Join-Path $jbr 'bin') + ';' + $env:PATH
        return [pscustomobject]@{ Home = $jbr; IsJbr = $true }
    }
    return [pscustomobject]@{ Home = (Use-Jdk25); IsJbr = $false }
}
