@echo off
setlocal
pushd "%~dp0"
if errorlevel 1 exit /b 1

echo DhrLang extension development setup - requires JDK 17 and Node.js 22+
node -e "if (Number(process.versions.node.split('.')[0]) < 22) process.exit(1)"
if errorlevel 1 (
    echo Install Node.js 22 or newer before packaging the extension.
    goto failed
)

call gradlew.bat stageCompiler verifyCompilerArtifact
if errorlevel 1 goto failed

pushd vscode-extension
if errorlevel 1 goto failed
call npm ci
if errorlevel 1 goto extension_failed
call npm run test:packaging
if errorlevel 1 goto extension_failed
call npm run package
if errorlevel 1 goto extension_failed
popd

echo Verified JAR, VSIX and release-manifest.json are in build\release.
echo Install the exact VSIX with: code --install-extension build\release\dhrlang-vscode-VERSION.vsix
popd
exit /b 0

:extension_failed
popd
:failed
echo Extension setup failed. Nothing was published.
popd
exit /b 1
