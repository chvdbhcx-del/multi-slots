<#
  GC Switcher: one-click switching between GetContact accounts on Windows.
  Each account lives in its own LDPlayer instance named "GC <name>".
  Uses LDPlayer's own command-line tool (ldconsole.exe). GetContact itself is not modified.
  Run: right-click -> "Run with PowerShell", or start "GC-Switcher.cmd".
#>
Add-Type -AssemblyName System.Windows.Forms, System.Drawing
Add-Type @"
using System; using System.Runtime.InteropServices;
public static class Win {
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int cmd);
}
"@

$Prefix   = 'GC '
$Package  = 'app.source.getcontact'
$Template = 'GC-TEMPLATE'
$CfgPath  = Join-Path $PSScriptRoot 'gc-switcher.json'

function Find-LdConsole {
    if (Test-Path $CfgPath) {
        $p = (Get-Content $CfgPath -Raw | ConvertFrom-Json).ldconsole
        if ($p -and (Test-Path $p)) { return $p }
    }
    $candidates = @('C:\LDPlayer\LDPlayer9\ldconsole.exe', 'D:\LDPlayer\LDPlayer9\ldconsole.exe',
                    'C:\LDPlayer\LDPlayer4.0\ldconsole.exe', "$env:ProgramFiles\LDPlayer\LDPlayer9\ldconsole.exe")
    foreach ($c in $candidates) { if (Test-Path $c) { return $c } }
    $dlg = New-Object System.Windows.Forms.OpenFileDialog
    $dlg.Title = 'Укажите ldconsole.exe из папки LDPlayer'
    $dlg.Filter = 'ldconsole.exe|ldconsole.exe'
    if ($dlg.ShowDialog() -ne 'OK') { exit }
    @{ ldconsole = $dlg.FileName } | ConvertTo-Json | Set-Content $CfgPath -Encoding UTF8
    return $dlg.FileName
}
$Ld = Find-LdConsole

function Ld([string[]]$a) {
    $out = & $Ld @a 2>&1 | Out-String
    return $out.Trim()
}

# list2 columns: index,title,top hwnd,bind hwnd,android started,pid,vbox pid
function Get-Instances {
    $rows = @()
    foreach ($line in (Ld @('list2')) -split "`r?`n") {
        $c = $line -split ','
        if ($c.Count -lt 5) { continue }
        $rows += [pscustomobject]@{
            Index = [int]$c[0]; Title = $c[1]; Hwnd = [int64]$c[2]; Running = ($c[4] -eq '1')
        }
    }
    return $rows
}

function Wait-Booted([int]$index, [int]$timeoutSec = 120) {
    $t = 0
    while ($t -lt $timeoutSec) {
        $r = Ld @('adb', '--index', "$index", '--command', 'shell getprop sys.boot_completed')
        if ($r -match '1') { return $true }
        Start-Sleep 2; $t += 2
    }
    return $false
}

$form = New-Object System.Windows.Forms.Form
$form.Text = 'GC Switcher'
$form.Size = New-Object System.Drawing.Size(460, 560)
$form.StartPosition = 'CenterScreen'
$form.Font = New-Object System.Drawing.Font('Segoe UI', 10)

$list = New-Object System.Windows.Forms.ListView
$list.View = 'Details'; $list.FullRowSelect = $true; $list.MultiSelect = $false; $list.HideSelection = $false
$list.Location = '12,12'; $list.Size = '420,380'; $list.Anchor = 'Top,Left,Right,Bottom'
[void]$list.Columns.Add('Аккаунт', 290)
[void]$list.Columns.Add('Статус', 110)
$form.Controls.Add($list)

$status = New-Object System.Windows.Forms.Label
$status.Location = '12,400'; $status.Size = '420,22'; $status.Anchor = 'Left,Right,Bottom'
$form.Controls.Add($status)

function Add-Button($text, $x, $y, $w, $onClick) {
    $b = New-Object System.Windows.Forms.Button
    $b.Text = $text; $b.Location = "$x,$y"; $b.Size = "$w,36"; $b.Anchor = 'Left,Bottom'
    $b.Add_Click($onClick); $form.Controls.Add($b); return $b
}

function Refresh-List {
    $sel = if ($list.SelectedItems.Count) { $list.SelectedItems[0].Tag.Title } else { $null }
    $list.Items.Clear()
    $all = Get-Instances
    $hasTemplate = $false
    foreach ($i in $all) {
        if ($i.Title -eq $Template) { $hasTemplate = $true; continue }
        if (-not $i.Title.StartsWith($Prefix)) { continue }
        $item = New-Object System.Windows.Forms.ListViewItem($i.Title.Substring($Prefix.Length))
        [void]$item.SubItems.Add($(if ($i.Running) { 'открыт' } else { '' }))
        $item.Tag = $i
        [void]$list.Items.Add($item)
        if ($i.Title -eq $sel) { $item.Selected = $true }
    }
    $status.Text = if ($hasTemplate) { "Аккаунтов: $($list.Items.Count)" }
                   else { "Нет копии $Template, см. инструкцию (шаг 2)" }
}

function Selected {
    if (-not $list.SelectedItems.Count) { [System.Windows.Forms.MessageBox]::Show('Выберите аккаунт'); return $null }
    return $list.SelectedItems[0].Tag
}

function Open-Account($inst) {
    $fresh = (Get-Instances | Where-Object Index -eq $inst.Index)
    if ($fresh.Running -and $fresh.Hwnd -ne 0) {
        [Win]::ShowWindow([IntPtr]$fresh.Hwnd, 9) | Out-Null
        [Win]::SetForegroundWindow([IntPtr]$fresh.Hwnd) | Out-Null
        return
    }
    $status.Text = "Запускаю $($inst.Title)…"; $form.Refresh()
    Ld @('launch', '--index', "$($inst.Index)") | Out-Null
    if (Wait-Booted $inst.Index) {
        Ld @('runapp', '--index', "$($inst.Index)", '--packagename', $Package) | Out-Null
    }
    Refresh-List
}

$list.Add_DoubleClick({ $s = Selected; if ($s) { Open-Account $s } })

Add-Button 'Открыть' 12 432 135 { $s = Selected; if ($s) { Open-Account $s } } | Out-Null

Add-Button 'Добавить' 155 432 135 {
    if (-not (Get-Instances | Where-Object Title -eq $Template)) {
        [System.Windows.Forms.MessageBox]::Show("Сначала создайте копию $Template с установленным GetContact (без входа). См. инструкцию.")
        return
    }
    $name = [Microsoft.VisualBasic.Interaction]::InputBox('Имя аккаунта (например, номер):', 'Новый аккаунт', '')
    if (-not $name) { return }
    $status.Text = 'Клонирую шаблон, это займёт до минуты…'; $form.Refresh()
    Ld @('copy', '--name', "$Prefix$name", '--from', $Template) | Out-Null
    Refresh-List
    $status.Text = 'Готово. Откройте аккаунт и войдите по номеру.'
} | Out-Null

Add-Button 'Закрыть' 298 432 135 {
    $s = Selected; if ($s) { Ld @('quit', '--index', "$($s.Index)") | Out-Null; Start-Sleep 1; Refresh-List }
} | Out-Null

Add-Button 'Переименовать' 12 474 135 {
    $s = Selected; if (-not $s) { return }
    $name = [Microsoft.VisualBasic.Interaction]::InputBox('Новое имя:', 'Переименовать', $s.Title.Substring($Prefix.Length))
    if ($name) { Ld @('rename', '--index', "$($s.Index)", '--title', "$Prefix$name") | Out-Null; Refresh-List }
} | Out-Null

Add-Button 'Закрыть все' 155 474 135 {
    foreach ($i in Get-Instances) { if ($i.Running -and $i.Title.StartsWith($Prefix)) { Ld @('quit', '--index', "$($i.Index)") | Out-Null } }
    Start-Sleep 1; Refresh-List
} | Out-Null

Add-Button 'Удалить' 298 474 135 {
    $s = Selected; if (-not $s) { return }
    $ok = [System.Windows.Forms.MessageBox]::Show("Удалить «$($s.Title)» со всеми данными? Вернуть аккаунт можно только новым входом по SMS.",
        'Удаление', 'YesNo', 'Warning')
    if ($ok -eq 'Yes') {
        Ld @('quit', '--index', "$($s.Index)") | Out-Null; Start-Sleep 2
        Ld @('remove', '--index', "$($s.Index)") | Out-Null; Refresh-List
    }
} | Out-Null

Add-Type -AssemblyName Microsoft.VisualBasic
$timer = New-Object System.Windows.Forms.Timer
$timer.Interval = 5000; $timer.Add_Tick({ Refresh-List }); $timer.Start()
Refresh-List
[void]$form.ShowDialog()
