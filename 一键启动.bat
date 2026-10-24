@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo 正在启动统一收件箱...
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\easy-start.ps1"
if errorlevel 1 (
  echo.
  echo 启动失败，请把上面的错误发给我。
  pause
)
