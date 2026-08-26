@echo off
echo =======================================================
echo      RKDownloader - GitHub Update Push Automation
echo =======================================================
echo.
git add .
set /p commit_msg="Enter commit message (default: Update Supabase config): "
if "%commit_msg%"=="" set commit_msg=Update Supabase config
git commit -m "%commit_msg%"
git push origin main
echo.
echo [SUCCESS] Updates pushed to GitHub successfully!
echo.
pause