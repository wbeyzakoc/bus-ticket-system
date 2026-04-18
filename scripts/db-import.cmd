@echo off
setlocal
powershell -ExecutionPolicy Bypass -File "%~dp0db-import.ps1" %*
endlocal
