@echo off
chcp 65001 > nul
setlocal enabledelayedexpansion

echo =======================================================================
echo   MqlRealMonitor Starter
echo =======================================================================

rem Prüfen ob Java installiert ist
java -version >nul 2>&1
if errorlevel 1 (
    echo [FEHLER] Java konnte nicht im PATH gefunden werden.
    echo Bitte installieren Sie Java 11 oder höher und fügen Sie es zu den Systemvariablen hinzu.
    pause
    exit /b 1
)

set "PARAMS="
set "REBUILD=0"

rem Parameter parsen
:loop
if "%~1"=="" goto endloop
if "%~1"=="--rebuild" (
    set "REBUILD=1"
) else if "%~1"=="-r" (
    set "REBUILD=1"
) else (
    rem Parameter an PARAMS anhängen
    set PARAMS=!PARAMS! "%~1"
)
shift
goto loop
:endloop

rem Erzwinge Rebuild falls gewünscht
if "!REBUILD!"=="1" (
    echo [INFO] Parameter --rebuild erkannt. Starte Bereinigung und Rebuild...
    goto build
)

rem Prüfen ob die JAR-Datei existiert
if not exist "target\mql-real-monitor.jar" (
    echo [INFO] target\mql-real-monitor.jar nicht gefunden. Starte automatischen Build...
    goto build
) else (
    goto run
)

:build
rem Prüfen ob Maven installiert ist
call mvn -version >nul 2>&1
if errorlevel 1 (
    echo [FEHLER] Maven konnte nicht gefunden werden [mvn-Befehl ist nicht im PATH].
    echo Das Projekt kann nicht automatisch gebaut werden.
    echo Bitte installieren Sie Maven oder kopieren Sie die fertige JAR-Datei nach target\mql-real-monitor.jar
    pause
    exit /b 1
)

echo [INFO] Führe Maven-Build aus (mvn clean package -DskipTests)...
call mvn clean package -DskipTests
if errorlevel 1 (
    echo [FEHLER] Maven-Build ist fehlgeschlagen.
    pause
    exit /b 1
)
echo [INFO] Build war erfolgreich.

:run
echo [INFO] Starte MqlRealMonitor...
if defined PARAMS (
    echo [INFO] Übergebene Parameter: !PARAMS!
    java -jar target\mql-real-monitor.jar !PARAMS!
) else (
    java -jar target\mql-real-monitor.jar
)

if errorlevel 1 (
    echo [WARNUNG] MqlRealMonitor wurde mit Fehlercode %errorlevel% beendet.
    pause
)

endlocal
