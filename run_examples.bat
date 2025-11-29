@echo off
REM run_examples.bat
set ROOT=%~dp0
set LIBS=%ROOT%libs\jocl-2.0.4.jar
set SRC=%ROOT%src
set OUTDIR=%ROOT%results
if not exist "%OUTDIR%" mkdir "%OUTDIR%"

set SAMPLES=%ROOT%samples\DonQuixote-388208.txt;%ROOT%samples\Dracula-165307.txt;%ROOT%samples\MobyDick-217452.txt
set TARGET=the
set RUNS=3

cd /d "%SRC%"
echo Compilando...
javac -cp ".;%LIBS%" *.java
echo Compilado.

for %%F in ("%ROOT%samples\DonQuixote-388208.txt" "%ROOT%samples\Dracula-165307.txt" "%ROOT%samples\MobyDick-217452.txt") do (
  for %%G in (%%~nF) do (
    set "base=%%~nF"
  )
  set "sample=%%F"
  call :runSample "%%F"
)
goto :after

:runSample
setlocal
set sample=%1
for %%A in (%sample%) do set base=%%~nA
set outcsv=%OUTDIR%\%base%_results.csv
echo Running %base%...
java -cp ".;%LIBS%" WordCountMain all "%sample%" %TARGET% %RUNS% "%outcsv%"
endlocal
goto :eof

:after
echo Todos finalizados.
