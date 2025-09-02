# -------------------------
# Admin-Rechte prüfen
# -------------------------
if (-not ([Security.Principal.WindowsIdentity]::GetCurrent().Groups -contains 'S-1-5-32-544')) {
    Write-Error 'Skript muss mit Administrator-Rechten ausgeführt werden!'
    exit 1
} else {
    Write-Host 'Skript wird mit Administratorrechten ausgeführt.'
}

# -------------------------
# Helfer: sicher an Maschinen-PATH anhängen
# -------------------------
function Add-ToMachinePath([string]$segment) {
    if ([string]::IsNullOrWhiteSpace($segment)) { return }
    $machinePath = [Environment]::GetEnvironmentVariable('Path','Machine')
    if ($machinePath -notlike "*$segment*") {
        $newPath = ($machinePath.TrimEnd(';')) + ';' + $segment
        [Environment]::SetEnvironmentVariable('Path', $newPath, 'Machine')
        Write-Host ('PATH um ' + $segment + ' erweitert')
    } else {
        Write-Host ('PATH enthält bereits: ' + $segment)
    }
}

# -------------------------
# 1) Java prüfen & ggf. installieren (JDK 21)
# -------------------------
$desiredJavaMajor = 21
$javaOk   = $false
$javaBin  = ''
$jdkDir   = $null

$javaCmd = Get-Command java.exe -ErrorAction SilentlyContinue
if ($null -ne $javaCmd) {
    $javaVersionOutput = (& $javaCmd.Source -version) 2>&1 | Out-String
    $javaMajor = $null
    if ($javaVersionOutput -match 'version\s+"?(\d+)\b') {
        $javaMajor = [int]$Matches[1]
    }
    if ($javaMajor -eq $desiredJavaMajor) {
        Write-Host ('Java ' + $desiredJavaMajor + ' bereits installiert.')
        $javaOk = $true
    } else {
        $shown = $(if ($javaMajor) { $javaMajor } else { 'unbekannt' })
        Write-Warning ('Gefundene Java-Version: ' + $shown + ' (erwartet: ' + $desiredJavaMajor + ').')
    }
} else {
    Write-Host 'Java nicht gefunden – wird installiert...'
}

if (-not $javaOk) {
    $jdkUrl = 'https://aka.ms/download-jdk/microsoft-jdk-21.0.5-windows-x64.msi'
    $tmpMsi = Join-Path $env:TEMP 'jdk21.msi'

    Write-Host 'Lade Java 21 herunter...'
    Invoke-WebRequest -Uri $jdkUrl -OutFile $tmpMsi

    Write-Host 'Installiere Java 21...'
    Start-Process msiexec.exe -Wait -ArgumentList ('/i "' + $tmpMsi + '" /qn /norestart')

    $jdkDir = 'C:\Program Files\Microsoft\jdk-' + $desiredJavaMajor
    if (!(Test-Path $jdkDir)) {
        $jdkDir = Get-ChildItem 'C:\Program Files\Microsoft' -Directory |
                  Where-Object { $_.Name -like ('jdk-' + $desiredJavaMajor + '*') } |
                  Select-Object -First 1 | ForEach-Object FullName
    }
    if (!(Test-Path $jdkDir)) {
        throw ('Java ' + $desiredJavaMajor + ' konnte nach der Installation nicht gefunden werden.')
    }

    [Environment]::SetEnvironmentVariable('JAVA_HOME', $jdkDir, 'Machine')
    Write-Host ('JAVA_HOME = ' + $jdkDir)

    $javaBin = Join-Path $jdkDir 'bin'
    Add-ToMachinePath $javaBin

    Write-Host 'Java 21 Installation abgeschlossen.'
} else {
    try {
        $javaPath = (Get-Command java.exe -ErrorAction Stop).Source
        $javaBin  = Split-Path $javaPath -Parent
        $jdkDir   = Split-Path $javaBin -Parent
        if ($jdkDir) {
            [Environment]::SetEnvironmentVariable('JAVA_HOME', $jdkDir, 'Machine')
            Write-Host ('JAVA_HOME (bestehend) = ' + $jdkDir)
        }
    } catch {}
    if ($javaBin) { Add-ToMachinePath $javaBin }
}

# -------------------------
# 2) MPJ-Express installieren
# -------------------------
$destRoot = 'C:\MPJ'
New-Item -ItemType Directory -Force -Path $destRoot | Out-Null

$mpjZipUrl = 'https://sourceforge.net/projects/mpjexpress/files/releases/mpj-v0_44.zip/download'
$tmpZip    = Join-Path $env:TEMP 'mpj-express.zip'
if (Test-Path $tmpZip) { Remove-Item $tmpZip -Force }

Write-Host 'Lade MPJ-Express herunter...'
$curl = (Get-Command curl.exe -ErrorAction SilentlyContinue)
if ($null -eq $curl) { throw 'curl.exe nicht gefunden (sollte bei Windows 10/11 vorhanden sein).' }
& $curl.Source -L $mpjZipUrl -o $tmpZip

if (-not (Test-Path $tmpZip) -or (Get-Item $tmpZip).Length -lt 100000) {
    throw 'Download fehlgeschlagen oder Datei zu klein – ggf. Proxy/Internet prüfen.'
}

# ZIP validieren
try {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($tmpZip)
    $zip.Dispose()
} catch {
    throw 'Heruntergeladene Datei ist kein gültiges ZIP (möglicherweise HTML-Redirect gespeichert).'
}

Write-Host 'Entpacke MPJ-Express...'
Expand-Archive -LiteralPath $tmpZip -DestinationPath $destRoot -Force

$MPJ_HOME = Join-Path $destRoot 'mpj-v0_44'
if (!(Test-Path $MPJ_HOME)) {
    $MPJ_HOME = Get-ChildItem $destRoot -Directory | Select-Object -First 1 | ForEach-Object FullName
}
if (!(Test-Path $MPJ_HOME)) {
    throw 'MPJ-Express-Verzeichnis wurde nicht gefunden.'
}

[Environment]::SetEnvironmentVariable('MPJ_HOME', $MPJ_HOME, 'Machine')
Write-Host ('MPJ_HOME = ' + $MPJ_HOME)

$mpjBin = Join-Path $MPJ_HOME 'bin'
Add-ToMachinePath $mpjBin

# -------------------------
# 3) Aufräumen & Hinweis
# -------------------------
try {
    if (Test-Path $tmpZip) { Remove-Item $tmpZip -Force }
    if (Test-Path $tmpMsi) { Remove-Item $tmpMsi -Force }
} catch {}

Write-Host ''
Write-Host 'Installation abgeschlossen.'
Write-Host 'Hinweis: Neue Umgebungsvariablen werden in bereits offenen Terminals erst nach einem Neustart wirksam.'

# ------------------------
# 4) Firewall-Regeln ändern
# ------------------------
Write-Host 'Firewall-Regeln werden jetzt geändert'
Write-Host 'Port 40055 wird nun geöffnet'

New-NetFirewallRule -DisplayName "MPJ TCP 40055 In" -Direction Inbound -Protocol TCP -LocalPort 2000-2100 -Action Allow -Profile Any

New-NetFirewallRule -DisplayName "MPJ TCP 2000-2100 In" -Direction Inbound -Protocol TCP -LocalPort 2000-2100 -Action Allow -Profile Any

Write-Host 'Port erfolgreich geöffnet'
Write-Host 'Das GitHub-Repo wird nun heruntergeladen!'

# ------------------------
# 5) Installieren des MPJ-Kryptographie-Projekts (GitHub)
# ------------------------

# Pfade und URLs
$destRoot   = "C:\MPJ"
$zipPath    = "$env:TEMP\mpj-krypto.zip"
$repoUrl    = "https://github.com/Yeet0710/MPJKryptographie/archive/refs/heads/master.zip"
$tempDir    = "$env:TEMP\mpj-krypto-extract"

Write-Host "Lade ZIP-Datei von GitHub herunter ..."
Invoke-WebRequest -Uri $repoUrl -OutFile $zipPath -UseBasicParsing

# temporären Ordner leeren/anlegen
if (Test-Path $tempDir) { Remove-Item $tempDir -Recurse -Force }
New-Item -ItemType Directory -Path $tempDir | Out-Null

Write-Host "Entpacke ZIP nach $tempDir ..."
Expand-Archive -LiteralPath $zipPath -DestinationPath $tempDir -Force

# GitHub hängt standardmäßig "-master" an den Ordnernamen
$rootFolder = Get-ChildItem $tempDir | Where-Object { $_.PSIsContainer } | Select-Object -First 1

# Quellpfad: nur org\example (ohne bin)
$sourcePath = Join-Path $rootFolder.FullName "bin\org\example"

# Ziel vorbereiten
if (Test-Path $destRoot) { Remove-Item $destRoot -Recurse -Force }
New-Item -ItemType Directory -Path $destRoot | Out-Null

# Kopiere nur den gewünschten Teil, aber starte direkt ab "org"
Copy-Item -Path $sourcePath -Destination $destRoot -Recurse -Force

Write-Host "Fertig! Ordnerstruktur org/example liegt jetzt unter: $destRoot"



# ------------------------
# 6) Starten des MPJ-Daemons
# ------------------------

Write-Host "Der MPJ-Daemon wird nun automatisch gestartet."
& "$env:MPJ_HOME\bin\mpjdaemon.bat" -boot

Write-Host "MPJ-Daemon gestartet. Status wird nun überprüft..."
& "$env:MPJ_HOME\bin\mpjdaemon.bat" -status

Write-Host 'Bitte starte nun das Terminal neu!'