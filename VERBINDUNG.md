# MPJ-Express Cluster-Setup auf **Windows** (LAN/Hotspot)

Diese Anleitung beschreibt, wie ihr euer Java/MPJ-Express-Projekt **auf mehreren Laptops** im gleichen Netz startet.
Fokus: **Windows 10/11**, **Hotspot-Netz** (empfohlen) und **saubere, wiederholbare Schritte**.

---

## TL;DR (Schnellstart)

1. **JDK + MPJ-Express** auf allen Laptops installieren, `%MPJ_HOME%` setzen, `bin` in `PATH` aufnehmen.
2. **Eigenes Netz via Windows-Hotspot** aufsetzen; alle Clients verbinden (IP-Bereich `192.168.137.x`).
3. **Firewall erlauben** (als Admin): ICMP (Ping) + TCP **2000-2100**.
4. **Reachability testen**: gegenseitig pingen (`ping -4 192.168.137.x`).
5. **Build**: `javac` mit `mpj.jar` → `bin`.
6. **hosts.txt** mit IPs + Prozesszahlen anlegen (`-np = Summe`).
7. **SSH ohne Passwort** einrichten (OpenSSH Server).
8. **Starten**: `mpjboot.bat` → `mpjrun.bat -dev niodev` → `mpjhalt.bat`.

> Einzellaptop-Test: `mpjrun.bat -dev multicore -np 4 …`

---

## 1) Voraussetzungen (auf **allen** Laptops)

### Software

* **Java JDK** (empfohlen 17 oder 21)
  Prüfen:

  ```powershell
  java -version
  javac -version
  ```
* **MPJ-Express** (gleiche Version, z. B. `mpj-v0_44`)
  Umgebungsvariablen:

    * `MPJ_HOME = C:\MPJ\mpj-v0_44`
    * `PATH` um `%MPJ_HOME%\bin` erweitern
      Check:

  ```powershell
  $env:MPJ_HOME
  & "$env:MPJ_HOME\bin\mpjrun.bat" -h
  ```
* **OpenSSH Server** (für bequemen Remote-Start per `mpjboot.bat`) – PowerShell **als Administrator**:

  ```powershell
  Add-WindowsCapability -Online -Name OpenSSH.Server~~~~0.0.1.0
  Start-Service sshd
  Set-Service -Name sshd -StartupType Automatic
  New-NetFirewallRule -Name "OpenSSH-Server" -Direction Inbound -Protocol TCP -LocalPort 22 -Action Allow
  ```

> Hinweis: MPJ-Express ist **Java-basiert**; Microsoft MPI / OpenMPI wird **nicht** benötigt.

### Projektlayout & Pfade

* Am einfachsten: **identischer Projektpfad** auf allen Laptops.
* Alternativ: **Netzlaufwerk** (z. B. `Z:`) einbinden.
* Oder zur Sicherheit **Arbeitsverzeichnis erzwingen**: `-wdir "C:\pfad\zum\projekt"` beim Start (siehe unten).

---

## 2) Eigenes Netzwerk (empfohlen): Windows-Hotspot

### Hotspot aktivieren (Host-Laptop)

Windows-Einstellungen → **Netzwerk & Internet** → **Mobiler Hotspot** → **Ein**.
Alle Clients verbinden. Typische IPs:

* Host: `192.168.137.1`
* Clients: `192.168.137.x`

### Richtige Schnittstelle ermitteln

```powershell
Get-NetIPConfiguration | Where-Object { $_.IPv4Address.IPAddress -like '192.168.137.*' } |
  Select-Object InterfaceAlias, InterfaceIndex, IPv4Address
```

> In Uni/Firmen-Netzen ist die Profil-Umstellung oft per GPO gesperrt (`DomainAuthenticated`). **Nicht schlimm** – wir setzen Firewall-Regeln mit `-Profile Any`, das reicht.

### Firewall-Regeln setzen (als Administrator)

```powershell
# ICMPv4 Echo (Ping) erlauben – unabhängig vom Profil
New-NetFirewallRule -DisplayName "ICMPv4 Echo In" -Protocol ICMPv4 -IcmpType 8 `
  -Direction Inbound -Action Allow -Profile Any

# MPJ-Ports (TCP 2000-2100) erlauben
New-NetFirewallRule -DisplayName "MPJ TCP 2000-2100 In" -Direction Inbound `
  -Protocol TCP -LocalPort 2000-2100 -Action Allow -Profile Any
```

### Reachability testen (in **PowerShell**, nicht CMD)

```powershell
# Ping Host ↔ Client
ping -4 192.168.137.1
ping -4 192.168.137.<client>

# Portcheck (nur sinnvoll, wenn ein Listener läuft)
Test-NetConnection 192.168.137.<client> -Port 22
Test-NetConnection 192.168.137.<client> -Port 2000
```

---

## 3) Build (Kompilieren) – auf allen Laptops oder zentral auf Netzlaufwerk

Im Projektordner:

```powershell
javac -d bin -cp "$env:MPJ_HOME\lib\mpj.jar" src\main\java\org\example\*.java
```

**Lokaler Multicore-Smoke-Test** (ohne Netz):

```powershell
$env:MPJ_HOME\bin\mpjrun.bat -dev multicore -np 4 -cp "bin;$env:MPJ_HOME\lib\mpj.jar" org.example.Main
```

---

## 4) `hosts.txt` erstellen

In den Projektordner legen, z. B.:

```
192.168.137.1 2
192.168.137.39 2
```

* Linke Spalte: IP/Hostname
* Rechte Spalte: **Prozessanzahl pro Host**

**Wichtig:** `-np` beim Start = **Summe** aller Prozesszahlen in `hosts.txt` → hier also `-np 4`.

---

## 5) SSH ohne Passwort (für `mpjboot.bat`)

Auf dem **Host** (PowerShell):

```powershell
ssh-keygen -t ed25519

# Public Key auf Client(s) ablegen (Git Bash: ssh-copy-id; sonst manuell)
type $env:USERPROFILE\.ssh\id_ed25519.pub | ssh <user>@192.168.137.39 "mkdir -p ~/.ssh && cat >> ~/.ssh/authorized_keys"

# Test
ssh <user>@192.168.137.39 hostname
```

> Wenn SSH nicht möglich ist, kann man MPJ-Daemons auch manuell starten; SSH ist jedoch am bequemsten.

---

## 6) Cluster-Start

### 6.1 Daemons starten

```powershell
$env:MPJ_HOME\bin\mpjboot.bat -machinesfile hosts.txt
```

### 6.2 Anwendung starten

```powershell
$env:MPJ_HOME\bin\mpjrun.bat -np 4 -machinesfile hosts.txt -dev niodev `
  -cp "bin;$env:MPJ_HOME\lib\mpj.jar" org.example.Main
```

**Optional – Arbeitsverzeichnis erzwingen (bei unterschiedlichen Pfaden):**

```powershell
$env:MPJ_HOME\bin\mpjrun.bat -np 4 -machinesfile hosts.txt -dev niodev `
  -wdir "C:\mpj\projekt" `
  -cp "bin;$env:MPJ_HOME\lib\mpj.jar" org.example.Main
```

### 6.3 Daemons stoppen

```powershell
$env:MPJ_HOME\bin\mpjhalt.bat -machinesfile hosts.txt
```

> **Device:** Mehrere Rechner → `-dev niodev` (TCP-Sockets). Einzelrechner → `-dev multicore`.

---

## 7) Troubleshooting (kurz & knackig)

| Problem                                   | Mögliche Ursache                                     | Lösung                                                                       |
| ----------------------------------------- | ---------------------------------------------------- | ---------------------------------------------------------------------------- |
| `ping` schlägt fehl                       | ICMP geblockt, falsche IP, falsches Interface/Profil | ICMP-Regel setzen, `ipconfig` prüfen, richtige 192.168.137.x-Adresse wählen  |
| `Test-NetConnection … -Port 2000` = False | Kein Listener aktiv                                  | Normal; erst **während** MPJ läuft ist der Port offen                        |
| `ClassNotFoundException`                  | Classpath/Ordner falsch                              | `-cp "bin;$env:MPJ_HOME\lib\mpj.jar"`, identische Pfade, ggf. `-wdir` nutzen |
| Hänger beim Start/Remote                  | SSH nicht ohne Passwort                              | `ssh <user>@<client> hostname` testen; OpenSSH-Server/Firewall-Port 22       |
| `Address already in use`                  | Alte Daemons/Java laufen                             | `mpjhalt.bat`, ggf. Java-Prozesse beenden                                    |
| Profil lässt sich nicht ändern            | GPO/DomainAuthenticated                              | Kein Problem: Firewall-Regeln mit `-Profile Any` reichen                     |

---

## 8) Cheatsheet

```powershell
# Versionen
java -version
javac -version
$env:MPJ_HOME
& "$env:MPJ_HOME\bin\mpjrun.bat" -h

# IP & Reachability
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

## 9) Erwartete Ausgabe

Jeder Prozess meldet seinen Status. Mindestens ein Prozess sollte eine (wahrscheinliche) **Primzahl** finden, z. B.:

```
Process 1 found a probable prime: 123456789...
Es wurde eine Primzahl in 532 ms gefunden.
```

Nicht erfolgreiche Prozesse melden:

```
Process 2 did not find a prime.
```

---

### Hinweise

* `Test-NetConnection` ist ein **PowerShell-Cmdlet** – nicht in CMD ausführen.
* In Uni/Firma kann `NetworkCategory` bewusst gesperrt sein; **Firewall-Regeln mit `-Profile Any` sind ausreichend**.
