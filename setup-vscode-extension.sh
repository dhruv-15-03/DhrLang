#!/bin/bash

# DhrLang VS Code Extension Development Script

set -e

echo "🚀 DhrLang VS Code Extension Development Setup"
echo "=============================================="

# Navigate to extension directory
cd "$(dirname "$0")/vscode-extension"

# Check if Node.js is installed
if ! command -v node &> /dev/null; then
    echo "❌ Node.js is not installed. Please install Node.js 18+ first."
    echo "   Download from: https://nodejs.org/"
    exit 1
fi

echo "📦 Installing dependencies..."
npm install

# Install TypeScript compiler if not present
if ! command -v tsc &> /dev/null; then
    echo "📦 Installing TypeScript..."
    npm install -g typescript
fi

# Install VS Code Extension CLI
if ! command -v vsce &> /dev/null; then
    echo "📦 Installing VS Code Extension CLI..."
    npm install -g @vscode/vsce
fi

echo "🔨 Compiling TypeScript..."
npm run compile

echo "📋 Running extension package validation..."
vsce package --no-yarn

echo ""
echo "✅ Development setup complete!"
echo ""
echo "🛠️  Available commands:"
echo "   npm run compile       - Compile TypeScript"
echo "   npm run watch         - Watch and auto-compile"
echo "   vsce package          - Create .vsix package"
echo "   code --install-extension dhrlang-vscode-*.vsix - Install locally"
echo ""
echo "🧪 To test the extension:"
echo "   1. Open VS Code"
echo "   2. Press F5 to launch Extension Development Host"
echo "   3. Create a .dhr file and test features"
echo ""
echo "📦 Extension package created: dhrlang-vscode-*.vsix"
echo "   Install with: code --install-extension dhrlang-vscode-*.vsix"