@echo off
setlocal
cd /d "%~dp0"
java -Dfile.encoding=UTF-8 -jar target\campus-work-order-rbac.jar %*
if errorlevel 1 pause
