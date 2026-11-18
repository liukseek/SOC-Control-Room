@echo off
setlocal
cd /d "%~dp0"
title SOC: CONTROL ROOM - Compilazione

echo ==========================================
echo   SOC: CONTROL ROOM - Compilazione
echo ==========================================
echo.

where java >nul 2>nul
if errorlevel 1 (
    echo [ERRORE] Java non trovato. Installa il JDK 21 o superiore.
    goto :errore
)

where javac >nul 2>nul
if errorlevel 1 (
    echo [ERRORE] Trovato Java ma non il JDK ^(manca javac^).
    echo Installa il JDK 21 o superiore, ad esempio da https://adoptium.net
    goto :errore
)

set "MVN=mvn"
where mvn >nul 2>nul
if errorlevel 1 (
    call :maven_locale
    if errorlevel 1 goto :errore
)

echo Compilazione in corso, attendere...
echo La prima volta puo' richiedere qualche minuto.
echo.
call "%MVN%" -q clean package -DskipTests
if errorlevel 1 (
    echo.
    echo [ERRORE] Compilazione non riuscita.
    goto :errore
)

echo Compilazione completata.
if /i not "%~1"=="nopause" pause
exit /b 0

:errore
if /i not "%~1"=="nopause" pause
exit /b 1

:maven_locale
set "MAVEN_VERSION=3.9.9"
set "MAVEN_HOME_LOCAL=%~dp0.maven\apache-maven-%MAVEN_VERSION%"

if exist "%MAVEN_HOME_LOCAL%\bin\mvn.cmd" (
    set "MVN=%MAVEN_HOME_LOCAL%\bin\mvn.cmd"
    exit /b 0
)

echo Maven non installato: scarico Maven %MAVEN_VERSION% nella cartella del progetto...
if not exist "%~dp0.maven" mkdir "%~dp0.maven"

powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; try { Invoke-WebRequest -Uri 'https://archive.apache.org/dist/maven/maven-3/%MAVEN_VERSION%/binaries/apache-maven-%MAVEN_VERSION%-bin.zip' -OutFile '%~dp0.maven\maven.zip'; Expand-Archive -Path '%~dp0.maven\maven.zip' -DestinationPath '%~dp0.maven' -Force; Remove-Item '%~dp0.maven\maven.zip'; exit 0 } catch { Write-Host $_.Exception.Message; exit 1 }"

if not exist "%MAVEN_HOME_LOCAL%\bin\mvn.cmd" (
    echo [ERRORE] Download di Maven non riuscito. Controlla la connessione a internet.
    exit /b 1
)

echo Maven scaricato.
echo.
set "MVN=%MAVEN_HOME_LOCAL%\bin\mvn.cmd"
exit /b 0
