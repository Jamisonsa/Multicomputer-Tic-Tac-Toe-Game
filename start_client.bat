@echo off
echo Starting TicTacToe Chat Client...

cd "%~dp0"

REM Create bin folder if it doesn't exist
if not exist bin mkdir bin

REM Compile all client files into /bin/
javac -d bin ChatClient.java LoginWindow.java TicTacToePanel.java SoundManager.java

REM Run client from /bin/
cd bin
java ChatClient
pause
