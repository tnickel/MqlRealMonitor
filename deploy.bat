@echo off
chcp 65001 > nul
setlocal enabledelayedexpansion

echo =======================================================================
echo   MqlRealMonitor Deployment Script
echo =======================================================================

set "DEST_DIR=\\ds918\Forex\tmp"
set "DEST_FILE=!DEST_DIR!\MqlRealMonitor.jar"

rem 1. Prüfen ob Java installiert ist
java -version >nul 2>&1
if errorlevel 1 (
    echo [FEHLER] Java konnte nicht im PATH gefunden werden.
    echo Bitte installieren Sie Java 11 oder höher.
    pause
    exit /b 1
)

rem 2. Prüfen ob Maven installiert ist
call mvn -version >nul 2>&1
if errorlevel 1 (
    echo [FEHLER] Maven konnte nicht gefunden werden [mvn-Befehl ist nicht im PATH].
    echo Das Projekt kann nicht automatisch gebaut werden.
    pause
    exit /b 1
)

rem 3. Projekt kompilieren und paketieren
echo [INFO] Baue das Projekt neu (mvn clean package -DskipTests)...
call mvn clean package -DskipTests
if errorlevel 1 (
    echo [FEHLER] Maven-Build ist fehlgeschlagen.
    pause
    exit /b 1
)

rem 4. Prüfen ob die Quelldatei existiert
if not exist "target\mql-real-monitor.jar" (
    echo [FEHLER] target\mql-real-monitor.jar wurde nicht erstellt.
    pause
    exit /b 1
)

rem 5. Zielpfad prüfen und ggf. erstellen
echo [INFO] Prüfe Zugriff auf Zielverzeichnis: !DEST_DIR!
if not exist "!DEST_DIR!" (
    echo [WARNUNG] Zielverzeichnis !DEST_DIR! existiert nicht oder ist nicht erreichbar.
    echo Bitte stellen Sie sicher, dass die Netzwerkverbindung zu ds918 steht und das Netzlaufwerk verbunden ist.
    pause
    exit /b 1
)

rem 6. Kopieren der Datei
echo [INFO] Kopiere target\mql-real-monitor.jar nach !DEST_FILE!...
copy /Y "target\mql-real-monitor.jar" "!DEST_FILE!"
if errorlevel 1 (
    echo [FEHLER] Kopieren der Datei nach !DEST_FILE! fehlgeschlagen.
    pause
    exit /b 1
)

rem 6b. Deploy auf Zielrechner DESKTOP-NS1MQSV per SSH/SCP
rem     Vorlage: doc\zielrechner_ns1mqsv_betrieb.md in den Scanner-Repos.
rem     Der Monitor dort muss VOR dem Kopieren beendet sein (Jar-Sperre!);
rem     Start danach: StartMqlRealMonitor.bat in der trader-Session (Doppelklick).
set "SSH_KEY=D:\git\MQL\MqlGoldscanner\config\goldscanner_ziel_key"
set "SSH_TARGET=tnickel@192.168.178.164"
set "ZIEL_JAR=C:/Forex/MqlAnalyzer/bin/MqlRealMonitor.jar"
echo [INFO] Deploy auf Zielrechner !SSH_TARGET! nach !ZIEL_JAR!...
if not exist "!SSH_KEY!" (
    echo [WARNUNG] SSH-Key nicht gefunden: !SSH_KEY!
    echo [WARNUNG] Zielrechner-Deploy uebersprungen — NAS-Kopie liegt bereit.
) else (
    rem Erst als .neu kopieren, dann austauschen — bei Jar-Sperre bleibt die
    rem alte Jar unangetastet
    scp -q -o BatchMode=yes -i "!SSH_KEY!" "target\mql-real-monitor.jar" "!SSH_TARGET!:!ZIEL_JAR!.neu"
    if errorlevel 1 (
        echo [WARNUNG] SCP zum Zielrechner fehlgeschlagen — Deploy dort uebersprungen.
    ) else (
        ssh -o BatchMode=yes -i "!SSH_KEY!" "!SSH_TARGET!" "copy /y ""C:\Forex\MqlAnalyzer\bin\MqlRealMonitor.jar.neu"" ""C:\Forex\MqlAnalyzer\bin\MqlRealMonitor.jar"" && del ""C:\Forex\MqlAnalyzer\bin\MqlRealMonitor.jar.neu"""
        if errorlevel 1 (
            echo [WARNUNG] Austausch auf dem Zielrechner fehlgeschlagen — laeuft dort noch der Monitor? Jar.neu blieb liegen.
        ) else (
            echo [INFO] Zielrechner-Deploy erfolgreich. Monitor dort starten: StartMqlRealMonitor.bat in der trader-Session.
        )
    )
)

rem 7. Kopieren des doc\drawdown Ordners falls vorhanden
if exist "doc\drawdown" (
    echo [INFO] Kopiere doc\drawdown Ordner nach !DEST_DIR!\doc\drawdown...
    if not exist "!DEST_DIR!\doc\drawdown" mkdir "!DEST_DIR!\doc\drawdown"
    xcopy /E /I /Y "doc\drawdown" "!DEST_DIR!\doc\drawdown"
    if errorlevel 1 (
        echo [WARNUNG] Kopieren des doc\drawdown Ordners ist fehlgeschlagen.
    )
)

echo [INFO] Deployment war erfolgreich!
echo Datei erfolgreich abgelegt in: !DEST_FILE!
pause
endlocal
