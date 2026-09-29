#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"

echo "DhrLang extension development setup - requires JDK 17 and Node.js 22+"
if ! node -e "if (Number(process.versions.node.split('.')[0]) < 22) process.exit(1)"; then
    echo "Install Node.js 22 or newer before packaging the extension." >&2
    exit 1
fi

bash ./gradlew stageCompiler verifyCompilerArtifact
cd vscode-extension
npm ci
npm run test:packaging
npm run package

echo "Verified JAR, VSIX and release-manifest.json are in build/release."
echo "Install the exact VSIX with: code --install-extension build/release/dhrlang-vscode-VERSION.vsix"
