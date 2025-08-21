# MPJ-Express Cluster-Setup auf **Windows** (LAN/Hotspot)

Diese Anleitung beschreibt, wie ihr euer Java/MPJ-Express-Projekt **auf mehreren Laptops** im gleichen Netz startet.
Fokus: **Windows 10/11**, **Hotspot-Netz** (empfohlen) und **saubere, wiederholbare Schritte**.

---

## TL;DR (Schnellstart)

1. **JDK + MPJ-Express** auf allen Laptops installieren, `%MPJ_HOME%` setzen, `bin` in `PATH` aufnehmen.
2. **Eigenes Netz via Windows-Hotspot** aufsetzen; alle Clients verbinden (IP-Bereich `192.168.137.x`).
3. **Firewall erlauben** (als Admin): ICMP (Ping) + TCP **2000-2100 + 40055** (`New-NetFirewallRule -DisplayName "MPJ TCP 2000-2100 In" -Direction Inbound -Protocol TCP -LocalPort 2000-2100 -Action Allow -Profile Any`).
4. **Reachability testen**: gegenseitig pingen (`ping -4 192.168.137.x`).
5. **host.txt** mit IPs anlegen (In das gleiche Verzeichnis legen von PS).
6. **Daemons starten**: `& "$env:MPJ_HOME\bin\mpjdaemon.bat" -boot`.
7. **Anwendung starten**: `& "$env:MPJ_HOME\bin\mpjrun.bat" -np 8 -machinesfile "C:\MPJ\host.txt" -dev niodev -cp ".;$env:MPJ_HOME\lib\mpj.jar" org.example.Main`.

> Wichtig: Das kompilierte Projekt muss auf allen Laptops im gleichen Verzeichnis liegen (z. B. `C:\MPJ\projekt`).
> Einzellaptop-Test: `mpjrun.bat -dev multicore -np 4 …`
