@echo off
setlocal
cd /d "%~dp0"
title SOC: CONTROL ROOM - Test locale

set "SERVER=soc-server\target\soc-server-1.0.0-SNAPSHOT.jar"
set "CLIENT=soc-client\target\soc-client-1.0.0-SNAPSHOT.jar"

if not exist "%SERVER%" goto :compila
if not exist "%CLIENT%" goto :compila
goto :avvia

:compila
call "%~dp0compila.bat" nopause
if errorlevel 1 (
    pause
    exit /b 1
)

:avvia
echo Avvio server e 3 client sullo stesso PC...
start "SOC Server" cmd /c "chcp 65001 >nul & java -Dstdout.encoding=UTF-8 -jar "%SERVER%""
timeout /t 3 /nobreak >nul
start "" javaw -jar "%CLIENT%"
start "" javaw -jar "%CLIENT%"
start "" javaw -jar "%CLIENT%"
exit /b 0
