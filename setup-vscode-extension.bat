@echo off
REM DhrLang VS Code Extension Development Script for Windows

echo 🚀 DhrLang VS Code Extension Development Setup
echo ==============================================

cd /d "%~dp0vscode-extension"

REM Check if Node.js is installed
node --version >nul 2>&1
if errorlevel 1 (
    echo ❌ Node.js is not installed. Please install Node.js 18+ first.
    echo    Download from: https://nodejs.org/
    pause
    exit /b 1
)

echo 📦 Installing dependencies...
npm install

REM Install TypeScript compiler if not present
tsc --version >nul 2>&1
if errorlevel 1 (
    echo 📦 Installing TypeScript...
    npm install -g typescript
)

REM Install VS Code Extension CLI
vsce --version >nul 2>&1
if errorlevel 1 (
    echo 📦 Installing VS Code Extension CLI...
    npm install -g @vscode/vsce
)

echo 🔨 Compiling TypeScript...
npm run compile

echo 📋 Running extension package validation...
vsce package --no-yarn

echo.
echo ✅ Development setup complete!
echo.
echo 🛠️  Available commands:
echo    npm run compile       - Compile TypeScript
echo    npm run watch         - Watch and auto-compile
echo    vsce package          - Create .vsix package
echo    code --install-extension dhrlang-vscode-*.vsix - Install locally
echo.
echo 🧪 To test the extension:
echo    1. Open VS Code
echo    2. Press F5 to launch Extension Development Host
echo    3. Create a .dhr file and test features
echo.
echo 📦 Extension package created: dhrlang-vscode-*.vsix
echo    Install with: code --install-extension dhrlang-vscode-*.vsix

pause