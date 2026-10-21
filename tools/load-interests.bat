@echo off
cd /d "%~dp0.."
call "%~dp0java.bat" Build.java
if errorlevel 1 exit /b 1
call "%~dp0java.bat" -cp "out;lib/*" com.connore.tools.InterestCatalog --sync
exit /b %errorlevel%
