@echo off
setlocal
cd /d "%~dp0"
title SOC: CONTROL ROOM - Client

set "JAR=soc-client\target\soc-client-1.0.0-SNAPSHOT.jar"

if not exist "%JAR%" (
    call "%~dp0compila.bat" nopause
    if errorlevel 1 goto :errore
)

start "" javaw -jar "%JAR%"
exit /b 0

:errore
pause
exit /b 1
