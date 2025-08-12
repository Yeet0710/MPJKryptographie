# MPJ‑Express Cluster-Setup auf **Windows** (LAN/Hotspot)

Dieses Repository enthält ein kleines **MPJ‑Express**-Projekt (Java), das auf **mehreren Laptops** parallel laufen soll.
Die README führt dich Schritt für Schritt von der Installation bis zum **Cluster-Run** – mit Fokus auf **Windows 10/11**
und einem **eigenen Hotspot-Netz** (empfohlen, da Uni-/Gäste-WLANs häufig Peer‑to‑Peer blockieren).

---

## TL;DR (Schnellstart)

1. **Voraussetzungen** auf allen Laptops: JDK, MPJ‑Express (gleiche Version), Pfade/Umgebungsvariablen setzen.  
2. **Eigenes Netz** aufbauen: Windows‑Hotspot am Host aktivieren und alle Clients verbinden (IPs 192.168.137.x).  
3. **Firewall** auf allen Laptops anpassen: ICMP (Ping) erlauben, TCP **2000‑2100** erlauben.  
4. **Reachability testen**: gegenseitig pingen (`ping -4 192.168.137.x`).  
5. **Projekt kompilieren** (gleiches Layout/Classpath auf allen):  
   ```powershell
   javac -d bin -cp "$env:MPJ_HOME\lib\mpj.jar" src\main\java\org\example\*.java
   ```
6. **hosts.txt** mit den Hotspot‑IP‑Adressen erstellen.  
7. **SSH ohne Passwort** vom Host zu allen Clients einrichten (OpenSSH Server).  
8. Cluster starten: `mpjboot.bat` → `mpjrun.bat` → `mpjhalt.bat`.

> Wenn du nur **einen** Laptop nutzt: `mpjrun.bat -dev multicore -np 8 …`

---

## 1) Voraussetzungen (auf **allen** Laptops)

### Software
- **Java JDK** (empfohlen 17 oder 21) – `java -version`, `javac -version` müssen funktionieren.
- **MPJ‑Express** (gleiche Version, z. B. `mpj-v0_44`).  
  Download, entpacken, und Umgebungsvariable setzen:
  - `MPJ_HOME = C:\MPJ\mpj-v0_44`
  - `PATH` um `%MPJ_HOME%\bin` erweitern
- **OpenSSH Server** (für bequemen Cluster‑Start per `mpjboot.bat`):
  ```powershell
  Add-WindowsCapability -Online -Name OpenSSH.Server~~~~0.0.1.0
  Start-Service sshd
  Set-Service -Name sshd -StartupType Automatic
  New-NetFirewallRule -Name "OpenSSH-Server" -Direction Inbound -Protocol TCP -LocalPort 22 -Action Allow
  ```
  > Alternativ kann man die Daemons manuell starten – einfacher ist SSH.

> **Hinweis:** Microsoft MPI / OpenMPI ist **nicht nötig** für MPJ‑Express (Java‑basiert).

### Projektlayout & Pfade
- Auf allen Laptops **identischer Projektpfad** (empfohlen) **oder** ein **Netzlaufwerk** (z. B. `Z:`) nutzen.  
- Einheitlicher Classpath ist wichtig: `bin; %MPJ_HOME%\lib\mpj.jar`

---

## 2) Eigenes Netzwerk (empfohlen) – Windows‑Hotspot

### Hotspot aktivieren (Host‑Laptop)
1. Einstellungen → **Netzwerk & Internet** → **Mobiler Hotspot** → **Ein**.  
2. SSID/Passwort merken, alle **Clients** verbinden.

Typische IPs:  
- Host (Hotspot): `192.168.137.1`  
- Clients: `192.168.137.x`

### Netzwerkprofil & Firewall korrekt einstellen (auf **allen** Laptops)
1. **Privates** Profil setzen:
   ```powershell
   Get-NetConnectionProfile
   Set-NetConnectionProfile -InterfaceAlias "<Dein Interface>" -NetworkCategory Private
   ```
2. **Ping (ICMPv4 Echo) erlauben**:
   ```powershell
   New-NetFirewallRule -DisplayName "ICMPv4 Echo In" -Protocol ICMPv4 -IcmpType 8 `
     -Direction Inbound -Action Allow -Profile Any
   ```
3. **MPJ‑Ports (TCP 2000–2100) erlauben**:
   ```powershell
   New-NetFirewallRule -DisplayName "MPJ TCP 2000-2100 In" -Direction Inbound `
     -Protocol TCP -LocalPort 2000-2100 -Action Allow -Profile Any
   ```

### Reachability testen
- IPs prüfen: `ipconfig` → IPv4 der aktiven Verbindung muss `192.168.137.x` sein.
- Ping Host ↔ Clients:
  ```powershell
  ping -4 192.168.137.1           # vom Client zum Host
  ping -4 192.168.137.<clientIP>  # vom Host zum Client
  ```

---

## 3) Build: Kompilieren (auf **allen** Laptops oder einmal auf Netzlaufwerk)

Im Projektordner:
```powershell
javac -d bin -cp "$env:MPJ_HOME\lib\mpj.jar" src\main\java\org\example\*.java
```
**Lokaltest (Multicore auf einem Laptop):**
```powershell
$env:MPJ_HOME\bin\mpjrun.bat -dev multicore -np 4 -cp "bin;$env:MPJ_HOME\lib\mpj.jar" org.example.Main
```

---

## 4) `hosts.txt` erstellen

Im Projektordner (Beispiel mit Host + 1 Client):
```
192.168.137.1 2
192.168.137.39 2
```
- Linke Spalte: **IP oder Hostname**
- Rechte Spalte: **Prozesse** pro Host

> Summe der rechten Spalte = Wert für `-np` beim Start.

---

## 5) SSH ohne Passwort (für `mpjboot.bat`)

Auf dem **Host** einmalig:
```powershell
ssh-keygen -t ed25519
# Key auf Client(s) kopieren – per Git Bash "ssh-copy-id" oder manuell:
type $env:USERPROFILE\.ssh\id_ed25519.pub | ssh <user>@192.168.137.39 "mkdir -p ~/.ssh && cat >> ~/.ssh/authorized_keys"
```
Test:
```powershell
ssh <user>@192.168.137.39 hostname
```
→ Muss **ohne** Passwortabfrage funktionieren.

---

## 6) Cluster-Run

### 6.1 Daemons starten (über SSH)
```powershell
$env:MPJ_HOME\bin\mpjboot.bat -machinesfile hosts.txt
```
Wenn erfolgreich, laufen auf allen Hosts `mpjdaemon`‑Prozesse.

### 6.2 Anwendung starten
```powershell
$env:MPJ_HOME\bin\mpjrun.bat -np 4 -machinesfile hosts.txt -dev niodev `
  -cp "bin;$env:MPJ_HOME\lib\mpj.jar" org.example.Main
```

### 6.3 Daemons stoppen
```powershell
$env:MPJ_HOME\bin\mpjhalt.bat -machinesfile hosts.txt
```

> **Device:** Für mehrere Rechner immer `-dev niodev` (TCP‑Sockets). Für Einzelrechner‑Tests `-dev multicore`.

---

## 7) Hinweise zur Pfadstrategie

- **Einheitlicher Pfad** auf allen Laptops ist am einfachsten.  
- Alternativ **Netzlaufwerk** (z. B. `Z:`) für Code/`bin`.  
- Optional `-wdir "C:\pfad\zum\projekt"` an `mpjrun.bat` anhängen, wenn ihr die Arbeitsordner erzwingen wollt.

**Beispiel mit `-wdir`:**
```powershell
$env:MPJ_HOME\bin\mpjrun.bat -np 4 -machinesfile hosts.txt -dev niodev `
  -wdir "C:\mpj\projekt" -cp "bin;$env:MPJ_HOME\lib\mpj.jar" org.example.Main
```

---

## 8) Troubleshooting

| Problem | Ursache | Lösung |
|---|---|---|
| `ping` scheitert | Firewall blockt ICMP, falsche IP, falsches Profil | ICMP‑Regel setzen (s. o.), `ipconfig` prüfen, Profil „Privat“ |
| `Test-NetConnection … -Port 2000` False | Kein Prozess lauscht, Port blockiert | Port ist erst offen **während** MPJ läuft; Firewallregel TCP 2000–2100 setzen |
| `ClassNotFoundException` | Falscher Classpath/Ordner | `-cp "bin;$env:MPJ_HOME\lib\mpj.jar"` prüfen; identische Pfade/Netzlaufwerk nutzen |
| `Address already in use` | Alte Daemons/Prozesse aktiv | `mpjhalt.bat`, ggf. Java‑Prozesse beenden |
| Start hängt bei Remote‑Hosts | SSH nicht eingerichtet | OpenSSH Server installieren, key‑based auth einrichten; `ssh <host> hostname` testen |
| Nichts findet sich im Uni‑WLAN | Client‑Isolation/VLAN | Eigenes Hotspot‑Netz (diese README) oder Labor‑VLAN über IT |
| Unterschiedliche MPJ‑Versionen | Inkompatibel | Auf **allen** Laptops exakt gleiche MPJ‑Express‑Version verwenden |

---

## 9) Nützliche Befehle (Cheatsheet)

```powershell
# Versionen prüfen
java -version
javac -version
$env:MPJ_HOME
& "$env:MPJ_HOME\bin\mpjrun.bat" -h

# IPs & Erreichbarkeit
ipconfig
ping -4 192.168.137.1
Test-NetConnection 192.168.137.39 -Port 22
Test-NetConnection 192.168.137.39 -Port 2000

# Build
javac -d bin -cp "$env:MPJ_HOME\lib\mpj.jar" src\main\java\org\example\*.java

# Cluster
$env:MPJ_HOME\bin\mpjboot.bat -machinesfile hosts.txt
$env:MPJ_HOME\bin\mpjrun.bat -np 4 -machinesfile hosts.txt -dev niodev -cp "bin;$env:MPJ_HOME\lib\mpj.jar" org.example.Main
$env:MPJ_HOME\bin\mpjhalt.bat -machinesfile hosts.txt
```

---

## 10) Erwartete Ausgabe

Jeder Prozess meldet seinen Status. Mindestens ein Prozess sollte eine (wahrscheinlich) **Primzahl** finden, z. B.:
```
Process 1 found a probable prime: 123456789...
Es wurde eine Primzahl in 532 ms gefunden.
```
Auf nicht erfolgreichen Prozessen siehst du:
```
Process 2 did not find a prime.
```

---

### Lizenz / Autor
Interner Projektleitfaden für das MPJ‑Kryptographie‑/Primzahl‑Demo.
