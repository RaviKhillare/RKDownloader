@echo off
echo =======================================================
echo      RKDownloader - GitHub Upload Automation Script
echo =======================================================
echo.

:: Check if git is installed
where git >nul 2>nul
if %errorlevel% neq 0 (
    echo [ERROR] Git is not installed on this system.
    echo Please install Git from https://git-scm.com/ and try again.
    pause
    exit /b
)

echo [1/5] Initializing local Git repository...
git init

echo [2/5] Adding files to commit...
git add .

echo [3/5] Committing project files...
git commit -m "Initial commit of RKDownloader App"

echo [4/5] Setting up remote branch...
git branch -M main
:: Remove remote if it already exists, then add
git remote remove origin >nul 2>nul
git remote add origin https://github.com/RaviKhillare/RKDownloader.git

echo [5/5] Pushing to GitHub...
echo Note: This might open a web browser or prompt you for credentials.
git push -u origin main

if %errorlevel% == 0 (
    echo.
    echo =======================================================
    echo [SUCCESS] Project uploaded successfully to GitHub!
    echo Check your repository: https://github.com/RaviKhillare/RKDownloader
    echo =======================================================
) else (
    echo.
    echo [WARNING] Git push failed. This is likely due to authentication.
    echo Please run the following command manually in this directory:
    echo     git push -u origin main
)
echo.
pause
