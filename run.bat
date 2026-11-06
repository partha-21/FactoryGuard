@echo off
REM One-command start for Windows. Double-click this file.
cd /d "%~dp0"

where docker >nul 2>nul
if errorlevel 1 (
  echo Docker is not installed. Install Docker Desktop first: https://www.docker.com/products/docker-desktop/
  pause
  exit /b 1
)
docker info >nul 2>nul
if errorlevel 1 (
  echo Docker is installed but not running. Open Docker Desktop, wait until it says it is running, then double-click this file again.
  pause
  exit /b 1
)

echo Step 1/3: building the two demo app versions (first run takes a few minutes)...
docker compose --profile images build
if errorlevel 1 goto failed
echo Step 2/3: starting FactoryGuard, the MQTT broker, the database and the sensor simulator...
docker compose up -d --build
if errorlevel 1 goto failed
echo Step 3/3: waiting for the dashboard to start (up to 2 minutes)...
powershell -NoProfile -Command "for($i=0;$i -lt 60;$i++){ try { Invoke-WebRequest -UseBasicParsing http://localhost:3000/login.html | Out-Null; exit 0 } catch { Start-Sleep 2 } }; exit 1"
echo Ready.
echo.
echo   Open the dashboard:  http://localhost:3000
echo   Stop everything:     double-click stop.bat
start http://localhost:3000
pause
exit /b 0

:failed
echo Something went wrong. Scroll up to read the error, or see the Troubleshooting section in README.md
pause
exit /b 1
