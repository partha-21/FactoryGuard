@echo off
cd /d "%~dp0"
docker rm -f demo-app >nul 2>nul
docker compose down
echo Stopped.
pause
