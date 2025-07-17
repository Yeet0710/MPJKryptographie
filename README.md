# MPJKryptographie-Projekt

Dieses Projekt dient der Nutzung von MPJ-Express um eine eigen-Entwicklung des RSA-Verfahrens zu Softwareseitig
zu verschnellern.

!Dieses Projekt ist momentan für Windows-Betriebssysteme optimiert!

________________

## Nutzung des Projektes
### Voraussetzungen
- Installation von [MPJ-Express](https://mpj-express.org/)
- Hinzufügen von MPJ-Express zu den Umgebungsvariablen
- Installation von [Microsoft-MPI](https://learn.microsoft.com/de-de/message-passing-interface/microsoft-mpi)

### Starten des Projektes
1. Öffnen des Terminals innerhalb des Projektverzeichnisses
2. Kompilieren des Projektes mit (`javac -d bin -cp "C:\MPJ\mpj-v0_44\lib\mpj.jar" src\main\java\org\example\*.java`)
3. Starten des Projektes mit (`C:\MPJ\mpj-v0_44\bin\mpjrun.bat -np 12 -cp "bin;C:\MPJ\mpj-v0_44\lib\mpj.jar" org.example.Main`)

#### Wichtig!
Ändere die Verzeichnisse in den obigen Befehlen, falls du MPJ-Express an einem anderen Ort installiert hast
und setze die Anzahl der Prozesse (`-np`) auf die preferierte Anzahl.
________________

## Version 1.0.0
- **Implementierung**: MPJ-Express
- **Implementierung**: Primzahlgenerierung und das testen per Miller-Rabin-Test
- **Hinzugefügt**: Resource zum starten des Projektes