@echo off
echo Starting TicTacToe Chat Server...

cd "%~dp0"

REM Create bin folder if it doesn't exist
if not exist bin mkdir bin

REM Compile server files into /bin/
javac -d bin ChatServer.java

REM Run server from /bin/
cd bin
java ChatServer
pause
