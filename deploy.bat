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
