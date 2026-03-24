@echo off
setlocal
cd /d "%~dp0\.."
java -cp "out;lib\gson-2.10.1.jar" com.group58.recruit.Main
