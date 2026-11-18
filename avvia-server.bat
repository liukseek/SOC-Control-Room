@echo off
setlocal
cd /d "%~dp0"
chcp 65001 >nul
title SOC: CONTROL ROOM - Server

set "JAR=soc-server\target\soc-server-1.0.0-SNAPSHOT.jar"

if not exist "%JAR%" (
    call "%~dp0compila.bat" nopause
    if errorlevel 1 goto :errore
)

echo Avvio del server sulla porta 1234...
echo Chiudi questa finestra per spegnere il server.
echo.
java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar "%JAR%" %*
pause
exit /b 0

:errore
pause
exit /b 1
