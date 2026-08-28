# lib-win.ps1 - Win32 helpers shared by the acceptance tooling.
# Dot-source this file: . "$PSScriptRoot\lib-win.ps1"
#
# Why this exists: the 2026-08-24 acceptance session captured the *same* window
# twice while claiming it had two client views. Everything here is built so the
# identity of a captured client is proven by construction (the game writes its
# own framebuffer into <gameDir>/screenshots), never inferred from a window title.

$ErrorActionPreference = 'Stop'

if (-not ('YsmWin' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Text;
using System.Runtime.InteropServices;

public class YsmWin {
    public delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);

    [DllImport("user32.dll")] public static extern bool EnumWindows(EnumWindowsProc cb, IntPtr l);
    [DllImport("user32.dll")] public static extern int GetWindowText(IntPtr h, StringBuilder s, int c);
    [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr h);
    [DllImport("user32.dll")] public static extern int GetWindowThreadProcessId(IntPtr h, out int pid);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int cmd);
    [DllImport("user32.dll")] public static extern bool BringWindowToTop(IntPtr h);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern bool AttachThreadInput(uint idAttach, uint idAttachTo, bool fAttach);
    [DllImport("kernel32.dll")] public static extern uint GetCurrentThreadId();
    [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, uint flags, UIntPtr extra);
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint flags, uint dx, uint dy, uint data, UIntPtr extra);
    [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint msg, IntPtr w, IntPtr l);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr h, ref POINT p);

    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int Left, Top, Right, Bottom; }
    [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X, Y; }
}
'@
}

$script:SW_RESTORE            = 9
$script:KEYEVENTF_KEYUP       = 0x0002
$script:MOUSEEVENTF_LEFTDOWN  = 0x0002
$script:MOUSEEVENTF_LEFTUP    = 0x0004
$script:WM_KEYDOWN            = 0x0100
$script:WM_KEYUP              = 0x0101

# vk / scancode pairs. Scancode matters: GLFW derives its key id from the
# scancode in lParam, so sending vk alone can translate to the wrong key.
$script:KeyMap = @{
    'F1'=@(0x70,0x3B); 'F2'=@(0x71,0x3C); 'F3'=@(0x72,0x3D); 'F5'=@(0x74,0x3F);
    'F11'=@(0x7A,0x57); 'ESCAPE'=@(0x1B,0x01); 'ESC'=@(0x1B,0x01); 'ENTER'=@(0x0D,0x1C);
    'SPACE'=@(0x20,0x39); 'TAB'=@(0x09,0x0F); 'SHIFT'=@(0x10,0x2A); 'CTRL'=@(0x11,0x1D);
    'W'=@(0x57,0x11); 'A'=@(0x41,0x1E); 'S'=@(0x53,0x1F); 'D'=@(0x44,0x20);
    'E'=@(0x45,0x12); 'Q'=@(0x51,0x10); 'T'=@(0x54,0x14); 'Y'=@(0x59,0x15);
    'Z'=@(0x5A,0x2C); 'X'=@(0x58,0x2D); 'C'=@(0x43,0x2E); 'F'=@(0x46,0x21);
    'R'=@(0x52,0x13); 'G'=@(0x47,0x22); 'B'=@(0x42,0x30); 'N'=@(0x4E,0x31);
    'M'=@(0x4D,0x32); 'P'=@(0x50,0x19); 'I'=@(0x49,0x17); 'O'=@(0x4F,0x18);
    'UP'=@(0x26,0x48); 'DOWN'=@(0x28,0x50); 'LEFT'=@(0x25,0x4B); 'RIGHT'=@(0x27,0x4D);
    'SLASH'=@(0xBF,0x35); 'BACKSPACE'=@(0x08,0x0E)
}

function Get-McClientProcess {
    <#
      .SYNOPSIS Find the java process of a dev client by the unique token in its command line.
      .PARAMETER Token Usually the --gameDir value, e.g. 'run-client-a'.
    #>
    param([Parameter(Mandatory)][string]$Token)

    $procs = @(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" |
        Where-Object { $_.CommandLine -and $_.CommandLine -like "*$Token*" })
    if ($procs.Count -eq 0) { return $null }
    # The Gradle worker that actually runs Minecraft is the one holding a window;
    # prefer processes whose command line mentions the client main class.
    $mc = @($procs | Where-Object { $_.CommandLine -like '*net.fabricmc*' -or $_.CommandLine -like '*KnotClient*' -or $_.CommandLine -like '*minecraft*' })
    if ($mc.Count -gt 0) { return $mc[0] }
    return $procs[0]
}

function Get-McWindow {
    <#
      .SYNOPSIS Resolve the Minecraft top-level window for a dev client identified by $Token.
      .OUTPUTS PSCustomObject with Hwnd, ProcessId, Title - or $null.
    #>
    param([Parameter(Mandatory)][string]$Token)

    $proc = Get-McClientProcess -Token $Token
    if (-not $proc) { return $null }
    $targetPid = [int]$proc.ProcessId

    $found = $null
    $cb = [YsmWin+EnumWindowsProc]{
        param($h, $l)
        if ([YsmWin]::IsWindowVisible($h)) {
            $wpid = 0
            [void][YsmWin]::GetWindowThreadProcessId($h, [ref]$wpid)
            if ($wpid -eq $targetPid) {
                $sb = New-Object System.Text.StringBuilder 512
                [void][YsmWin]::GetWindowText($h, $sb, 512)
                $t = $sb.ToString()
                if ($t -like 'Minecraft*') {
                    $script:__foundWindow = [pscustomobject]@{ Hwnd = $h; ProcessId = $wpid; Title = $t }
                    return $false
                }
            }
        }
        return $true
    }
    $script:__foundWindow = $null
    [void][YsmWin]::EnumWindows($cb, [IntPtr]::Zero)
    return $script:__foundWindow
}

function Set-McForeground {
    <#  .SYNOPSIS Raise a window past the Windows foreground lock. #>
    param([Parameter(Mandatory)][IntPtr]$Hwnd)

    [void][YsmWin]::ShowWindow($Hwnd, $script:SW_RESTORE)
    [void][YsmWin]::BringWindowToTop($Hwnd)
    if ([YsmWin]::SetForegroundWindow($Hwnd)) { Start-Sleep -Milliseconds 250; return $true }

    # Foreground lock: attach our input queue to the target window's thread.
    $fg = [YsmWin]::GetForegroundWindow()
    $dummy = 0
    $fgThread = [YsmWin]::GetWindowThreadProcessId($fg, [ref]$dummy)
    $tgThread = [YsmWin]::GetWindowThreadProcessId($Hwnd, [ref]$dummy)
    [void][YsmWin]::AttachThreadInput([uint32]$fgThread, [uint32]$tgThread, $true)
    $ok = [YsmWin]::SetForegroundWindow($Hwnd)
    [void][YsmWin]::AttachThreadInput([uint32]$fgThread, [uint32]$tgThread, $false)
    Start-Sleep -Milliseconds 250
    return $ok
}

function Confirm-McForeground {
    <#
      .SYNOPSIS Raise a window and verify it actually became the foreground window.
      .DESCRIPTION Windows can silently refuse SetForegroundWindow; sending input to a
      window that never got focus is how keystrokes end up in the wrong client.
    #>
    param([Parameter(Mandatory)][IntPtr]$Hwnd, [int]$Attempts = 3)

    for ($i = 0; $i -lt $Attempts; $i++) {
        [void](Set-McForeground -Hwnd $Hwnd)
        Start-Sleep -Milliseconds 350
        if ([YsmWin]::GetForegroundWindow() -eq $Hwnd) { return $true }
    }
    return ([YsmWin]::GetForegroundWindow() -eq $Hwnd)
}

function Send-McKey {
    <#
      .SYNOPSIS Send a key to the focused window (SendInput-style), or post it to a hwnd as fallback.
      .PARAMETER Name Key name from the KeyMap table, e.g. 'F2', 'Y', 'ESCAPE'.
      .PARAMETER HoldMs How long to hold the key down.
      .PARAMETER Hwnd If given and -PostMessage is set, post WM_KEYDOWN/UP instead of synthesizing input.
    #>
    param(
        [Parameter(Mandatory)][string]$Name,
        [int]$HoldMs = 60,
        [IntPtr]$Hwnd = [IntPtr]::Zero,
        [switch]$PostMessage
    )
    $key = $script:KeyMap[$Name.ToUpperInvariant()]
    if (-not $key) { throw "Unknown key name '$Name'" }
    $vk = [byte]$key[0]; $sc = [byte]$key[1]

    if ($PostMessage -and $Hwnd -ne [IntPtr]::Zero) {
        $lpDown = [IntPtr](1 -bor ($sc -shl 16))
        $lpUp   = [IntPtr](1 -bor ($sc -shl 16) -bor 0xC0000000)
        [void][YsmWin]::PostMessage($Hwnd, $script:WM_KEYDOWN, [IntPtr]$vk, $lpDown)
        Start-Sleep -Milliseconds $HoldMs
        [void][YsmWin]::PostMessage($Hwnd, $script:WM_KEYUP, [IntPtr]$vk, $lpUp)
        return
    }

    [YsmWin]::keybd_event($vk, $sc, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds $HoldMs
    [YsmWin]::keybd_event($vk, $sc, $script:KEYEVENTF_KEYUP, [UIntPtr]::Zero)
}

function Send-McText {
    <# .SYNOPSIS Type an ASCII string one key at a time (for chat/commands). #>
    param([Parameter(Mandatory)][string]$Text, [int]$DelayMs = 25)
    foreach ($ch in $Text.ToCharArray()) {
        $up = ([string]$ch).ToUpperInvariant()
        if ($script:KeyMap.ContainsKey($up)) {
            Send-McKey -Name $up -HoldMs 25
        } elseif ($ch -eq ' ') {
            Send-McKey -Name 'SPACE' -HoldMs 25
        } elseif ($ch -eq '/') {
            Send-McKey -Name 'SLASH' -HoldMs 25
        }
        Start-Sleep -Milliseconds $DelayMs
    }
}

function Send-McClick {
    <# .SYNOPSIS Click at client-area coordinates of the given window. #>
    param(
        [Parameter(Mandatory)][IntPtr]$Hwnd,
        [Parameter(Mandatory)][int]$X,
        [Parameter(Mandatory)][int]$Y
    )
    $pt = New-Object YsmWin+POINT
    $pt.X = $X; $pt.Y = $Y
    [void][YsmWin]::ClientToScreen($Hwnd, [ref]$pt)
    [void][YsmWin]::SetCursorPos($pt.X, $pt.Y)
    Start-Sleep -Milliseconds 120
    [YsmWin]::mouse_event($script:MOUSEEVENTF_LEFTDOWN, 0, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 60
    [YsmWin]::mouse_event($script:MOUSEEVENTF_LEFTUP, 0, 0, 0, [UIntPtr]::Zero)
    Start-Sleep -Milliseconds 200
}

function Get-McClientRect {
    param([Parameter(Mandatory)][IntPtr]$Hwnd)
    $r = New-Object YsmWin+RECT
    [void][YsmWin]::GetClientRect($Hwnd, [ref]$r)
    return [pscustomobject]@{ Width = $r.Right - $r.Left; Height = $r.Bottom - $r.Top }
}

function Save-ResizedImage {
    <# .SYNOPSIS Copy a PNG to $Destination, scaled down so its width is at most $MaxWidth. #>
    param(
        [Parameter(Mandatory)][string]$Source,
        [Parameter(Mandatory)][string]$Destination,
        [int]$MaxWidth = 960
    )
    Add-Type -AssemblyName System.Drawing
    $src = [System.Drawing.Image]::FromFile($Source)
    try {
        $scale = [Math]::Min(1.0, $MaxWidth / [double]$src.Width)
        $w = [int]($src.Width * $scale); $h = [int]($src.Height * $scale)
        $bmp = New-Object System.Drawing.Bitmap $w, $h
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.DrawImage($src, 0, 0, $w, $h)
        $g.Dispose()
        $dir = Split-Path -Parent $Destination
        if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
        $bmp.Save($Destination, [System.Drawing.Imaging.ImageFormat]::Png)
        $bmp.Dispose()
        return [pscustomobject]@{ Path = $Destination; Width = $w; Height = $h }
    } finally { $src.Dispose() }
}

function Save-CroppedImage {
    <#
      .SYNOPSIS Crop a region of a PNG (normalised 0..1 coords) and save it, scaled to fit MaxWidth.
      .DESCRIPTION Used to inspect one detail (a hand, a layer, a GUI widget) at native
      resolution instead of reading a whole downscaled frame.
    #>
    param(
        [Parameter(Mandatory)][string]$Source,
        [Parameter(Mandatory)][string]$Destination,
        [double]$X = 0, [double]$Y = 0, [double]$W = 1, [double]$H = 1,
        [int]$MaxWidth = 960
    )
    Add-Type -AssemblyName System.Drawing
    $src = [System.Drawing.Image]::FromFile($Source)
    try {
        $cx = [int]($src.Width * $X); $cy = [int]($src.Height * $Y)
        $cw = [Math]::Max(1, [int]($src.Width * $W)); $ch = [Math]::Max(1, [int]($src.Height * $H))
        if ($cx + $cw -gt $src.Width)  { $cw = $src.Width  - $cx }
        if ($cy + $ch -gt $src.Height) { $ch = $src.Height - $cy }
        $rect = New-Object System.Drawing.Rectangle $cx, $cy, $cw, $ch
        $scale = [Math]::Min(4.0, $MaxWidth / [double]$cw)
        $ow = [int]($cw * $scale); $oh = [int]($ch * $scale)
        $bmp = New-Object System.Drawing.Bitmap $ow, $oh
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $g.DrawImage($src, (New-Object System.Drawing.Rectangle 0, 0, $ow, $oh), $rect, [System.Drawing.GraphicsUnit]::Pixel)
        $g.Dispose()
        $dir = Split-Path -Parent $Destination
        if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
        $bmp.Save($Destination, [System.Drawing.Imaging.ImageFormat]::Png)
        $bmp.Dispose()
        return [pscustomobject]@{ Path = $Destination; Width = $ow; Height = $oh }
    } finally { $src.Dispose() }
}

function Save-ContactSheet {
    <#
      .SYNOPSIS Compose labelled thumbnails into one grid image.
      .DESCRIPTION Lets a whole corpus (all builtin models, all camera modes) be reviewed
      as a single image instead of dozens of separate captures.
    #>
    param(
        [Parameter(Mandatory)][string[]]$Images,
        [Parameter(Mandatory)][string[]]$Labels,
        [Parameter(Mandatory)][string]$Destination,
        [int]$Columns = 7,
        [int]$CellWidth = 190,
        [int]$CellHeight = 150
    )
    Add-Type -AssemblyName System.Drawing
    $rows = [Math]::Ceiling($Images.Count / [double]$Columns)
    $labelH = 16
    $bmp = New-Object System.Drawing.Bitmap ($Columns * $CellWidth), ([int]($rows * ($CellHeight + $labelH)))
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.Clear([System.Drawing.Color]::FromArgb(24, 24, 28))
    $font = New-Object System.Drawing.Font('Consolas', 8)
    $brush = [System.Drawing.Brushes]::White
    for ($i = 0; $i -lt $Images.Count; $i++) {
        $col = $i % $Columns
        $row = [Math]::Floor($i / $Columns)
        $x = $col * $CellWidth
        $y = $row * ($CellHeight + $labelH)
        if (Test-Path $Images[$i]) {
            $img = [System.Drawing.Image]::FromFile($Images[$i])
            try {
                $g.DrawImage($img, (New-Object System.Drawing.Rectangle $x, ($y + $labelH), $CellWidth, $CellHeight))
            } finally { $img.Dispose() }
        }
        $g.DrawString($Labels[$i], $font, $brush, $x + 2, $y + 1)
    }
    $g.Dispose()
    $dir = Split-Path -Parent $Destination
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
    $bmp.Save($Destination, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    return $Destination
}

function Get-ImageDifference {
    <#
      .SYNOPSIS Mean absolute per-pixel difference (0-255) between two PNGs, sampled on a grid.
      .DESCRIPTION Used to prove a model switch actually changed the rendered frame.
    #>
    param(
        [Parameter(Mandatory)][string]$A,
        [Parameter(Mandatory)][string]$B,
        [int]$Step = 4
    )
    Add-Type -AssemblyName System.Drawing
    $ia = [System.Drawing.Bitmap]::FromFile($A)
    $ib = [System.Drawing.Bitmap]::FromFile($B)
    try {
        if ($ia.Width -ne $ib.Width -or $ia.Height -ne $ib.Height) { return -1 }
        $sum = 0.0; $n = 0
        for ($y = 0; $y -lt $ia.Height; $y += $Step) {
            for ($x = 0; $x -lt $ia.Width; $x += $Step) {
                $pa = $ia.GetPixel($x, $y); $pb = $ib.GetPixel($x, $y)
                $sum += [Math]::Abs($pa.R - $pb.R) + [Math]::Abs($pa.G - $pb.G) + [Math]::Abs($pa.B - $pb.B)
                $n += 3
            }
        }
        if ($n -eq 0) { return 0 }
        return [Math]::Round($sum / $n, 3)
    } finally { $ia.Dispose(); $ib.Dispose() }
}
