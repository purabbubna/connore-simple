@echo off
cd /d "%~dp0"
call "%~dp0tools\java.bat" -version >nul 2>&1
if errorlevel 1 (
  echo Install JDK 17 or later, reopen this folder, and try again.
  pause
  exit /b 1
)
if not exist .env if not exist config.properties (
  if not defined DB_USER (
    copy .env.example .env >nul
    echo Set your MySQL username and password in .env, then save it.
    start "" notepad "%cd%\.env"
    echo After saving, return to this window and press a key to continue.
    pause
  )
)
if not exist lib\mysql-connector-j*.jar (
  echo Put the MySQL Connector/J JAR in the lib folder first. See README.md.
  pause
  exit /b 1
)
call "%~dp0tools\java.bat" Build.java
if errorlevel 1 (pause & exit /b 1)
call "%~dp0tools\java.bat" -cp "out;lib/*" com.connore.Main --open-browser
set "CONNORE_EXIT=%ERRORLEVEL%"
pause
exit /b %CONNORE_EXIT%
