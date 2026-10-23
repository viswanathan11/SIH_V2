@echo off
title Stop VidyaSetu Services
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop.ps1"
pause
