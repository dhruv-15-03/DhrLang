# DhrLang Package Manager Guide

## 🚀 Distribution Strategy

To make DhrLang truly accessible to users worldwide, we need to distribute it through popular package managers and provide easy installation methods.

## 📦 Package Manager Targets

### 1. Homebrew (macOS/Linux)
Create a Homebrew formula for easy installation on macOS and Linux.

**Formula Location**: `homebrew-dhrlang/Formula/dhrlang.rb`

```ruby
class Dhrlang < Formula
  desc "Programming language with Hindi keywords - हिंदी में प्रोग्रामिंग भाषा"
  homepage "https://github.com/dhruv-15-03/DhrLang"
  url "https://github.com/dhruv-15-03/DhrLang/releases/download/v1.0.0/DhrLang-1.0.0.tar.gz"
  sha256 "REPLACE_WITH_ACTUAL_SHA256"
  license "MIT"

  depends_on "openjdk@17"

  def install
    libexec.install "DhrLang.jar"
    (bin/"dhrlang").write <<~EOS
      #!/bin/bash
      exec "#{Formula["openjdk@17"].opt_bin}/java" -jar "#{libexec}/DhrLang.jar" "$@"
    EOS
  end

  test do
    (testpath/"test.dhr").write <<~EOS
      मुख्य() {
          प्रिंट("नमस्ते DhrLang!");
      }
    EOS
    assert_match "नमस्ते DhrLang!", shell_output("#{bin}/dhrlang #{testpath}/test.dhr")
  end
end
```

**Installation**:
```bash
brew tap dhruv-15-03/dhrlang
brew install dhrlang
```

### 2. Chocolatey (Windows)
Create a Chocolatey package for Windows users.

**Package Files**: `chocolatey/dhrlang/`

`dhrlang.nuspec`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://schemas.microsoft.com/packaging/2015/06/nuspec.xsd">
  <metadata>
    <id>dhrlang</id>
    <version>1.0.0</version>
    <packageSourceUrl>https://github.com/dhruv-15-03/DhrLang</packageSourceUrl>
    <owners>dhruv-15-03</owners>
    <title>DhrLang</title>
    <authors>Dhruv Patel</authors>
    <projectUrl>https://github.com/dhruv-15-03/DhrLang</projectUrl>
    <iconUrl>https://raw.githubusercontent.com/dhruv-15-03/DhrLang/main/assets/logo.png</iconUrl>
    <copyright>2024 Dhruv Patel</copyright>
    <licenseUrl>https://github.com/dhruv-15-03/DhrLang/blob/main/LICENSE</licenseUrl>
    <requireLicenseAcceptance>false</requireLicenseAcceptance>
    <projectSourceUrl>https://github.com/dhruv-15-03/DhrLang</projectSourceUrl>
    <bugTrackerUrl>https://github.com/dhruv-15-03/DhrLang/issues</bugTrackerUrl>
    <tags>programming-language hindi java compiler</tags>
    <summary>Programming language with Hindi keywords</summary>
    <description>DhrLang is a programming language that uses Hindi keywords, making programming accessible to Hindi speakers. Features include object-oriented programming, generics, exception handling, and seamless Java interoperability.</description>
    <dependencies>
      <dependency id="openjdk17" version="17.0.0" />
    </dependencies>
  </metadata>
  <files>
    <file src="tools\**" target="tools" />
  </files>
</package>
```

`tools/chocolateyinstall.ps1`:
```powershell
$ErrorActionPreference = 'Stop'
$toolsDir = "$(Split-Path -parent $MyInvocation.MyCommand.Definition)"
$url64 = 'https://github.com/dhruv-15-03/DhrLang/releases/download/v1.0.0/DhrLang-1.0.0.jar'

$packageArgs = @{
  packageName   = $env:ChocolateyPackageName
  unzipLocation = $toolsDir
  url64bit      = $url64
  softwareName  = 'DhrLang*'
  checksum64    = 'REPLACE_WITH_ACTUAL_CHECKSUM'
  checksumType64= 'sha256'
  file64        = "$toolsDir\DhrLang.jar"
}

# Download the JAR file
Get-ChocolateyWebFile @packageArgs

# Create dhrlang.bat wrapper
$batchFile = @"
@echo off
java -jar "$toolsDir\DhrLang.jar" %*
"@

$batchFile | Out-File -FilePath "$toolsDir\dhrlang.bat" -Encoding ASCII

# Add to PATH
Install-ChocolateyPath "$toolsDir" 'Machine'
```

**Installation**:
```powershell
choco install dhrlang
```

### 3. Snap (Linux)
Create a Snap package for Linux distributions.

`snap/snapcraft.yaml`:
```yaml
name: dhrlang
version: '1.0.0'
summary: Programming language with Hindi keywords
description: |
  DhrLang is a programming language that uses Hindi keywords, making 
  programming accessible to Hindi speakers. Features include object-oriented 
  programming, generics, exception handling, and seamless Java interoperability.

grade: stable
confinement: strict

base: core22

apps:
  dhrlang:
    command: bin/dhrlang
    plugs:
      - home
      - network
      - network-bind

parts:
  dhrlang:
    plugin: gradle
    source: .
    stage-packages:
      - openjdk-17-jre-headless
    override-build: |
      snapcraftctl build
      mkdir -p $SNAPCRAFT_PART_INSTALL/bin
      cp build/libs/DhrLang-1.0.0-all.jar $SNAPCRAFT_PART_INSTALL/bin/DhrLang.jar
      cat > $SNAPCRAFT_PART_INSTALL/bin/dhrlang << 'EOF'
      #!/bin/bash
      exec java -jar $SNAP/bin/DhrLang.jar "$@"
      EOF
      chmod +x $SNAPCRAFT_PART_INSTALL/bin/dhrlang
```

**Installation**:
```bash
sudo snap install dhrlang
```

### 4. APT Repository (Debian/Ubuntu)
Create a custom APT repository for Debian-based systems.

`DEBIAN/control`:
```
Package: dhrlang
Version: 1.0.0
Section: devel
Priority: optional
Architecture: all
Depends: openjdk-17-jre-headless
Maintainer: Dhruv Patel <email@example.com>
Description: Programming language with Hindi keywords
 DhrLang is a programming language that uses Hindi keywords, making
 programming accessible to Hindi speakers. Features include object-oriented
 programming, generics, exception handling, and seamless Java interoperability.
```

**Installation**:
```bash
# Add repository
curl -fsSL https://dhrlang.dev/apt/gpg | sudo apt-key add -
echo "deb https://dhrlang.dev/apt stable main" | sudo tee /etc/apt/sources.list.d/dhrlang.list

# Install
sudo apt update
sudo apt install dhrlang
```

### 5. npm (Node.js)
Create an npm wrapper for easy installation via npm.

`package.json`:
```json
{
  "name": "dhrlang",
  "version": "1.0.0",
  "description": "Programming language with Hindi keywords",
  "main": "index.js",
  "bin": {
    "dhrlang": "./bin/dhrlang.js"
  },
  "scripts": {
    "postinstall": "node install.js"
  },
  "keywords": ["programming", "language", "hindi", "compiler"],
  "author": "Dhruv Patel",
  "license": "MIT",
  "repository": {
    "type": "git",
    "url": "https://github.com/dhruv-15-03/DhrLang.git"
  }
}
```

`bin/dhrlang.js`:
```javascript
#!/usr/bin/env node
const { spawn } = require('child_process');
const path = require('path');

const jarPath = path.join(__dirname, '..', 'lib', 'DhrLang.jar');
const args = ['-jar', jarPath, ...process.argv.slice(2)];

const child = spawn('java', args, { stdio: 'inherit' });
child.on('exit', code => process.exit(code));
```

**Installation**:
```bash
npm install -g dhrlang
```

## 🐳 Docker Distribution

### Official Docker Image
Create official Docker images for containerized usage.

`Dockerfile`:
```dockerfile
FROM openjdk:17-jre-slim

LABEL maintainer="Dhruv Patel"
LABEL description="DhrLang - Programming language with Hindi keywords"

# Install DhrLang
COPY DhrLang.jar /usr/local/lib/DhrLang.jar

# Create wrapper script
RUN echo '#!/bin/bash\njava -jar /usr/local/lib/DhrLang.jar "$@"' > /usr/local/bin/dhrlang && \
    chmod +x /usr/local/bin/dhrlang

# Set working directory
WORKDIR /workspace

# Default command
ENTRYPOINT ["dhrlang"]
CMD ["--help"]
```

**Usage**:
```bash
# Pull image
docker pull dhrlang/dhrlang:latest

# Run DhrLang program
docker run -v $(pwd):/workspace dhrlang/dhrlang:latest myprogram.dhr

# Interactive shell
docker run -it -v $(pwd):/workspace dhrlang/dhrlang:latest bash
```

## 📱 IDE Extensions

### VS Code Extension
Create a VS Code extension for syntax highlighting and language support.

`package.json` (VS Code extension):
```json
{
  "name": "dhrlang-vscode",
  "displayName": "DhrLang Support",
  "description": "Syntax highlighting and language support for DhrLang",
  "version": "1.0.0",
  "engines": {
    "vscode": "^1.74.0"
  },
  "categories": ["Programming Languages"],
  "contributes": {
    "languages": [{
      "id": "dhrlang",
      "aliases": ["DhrLang", "dhrlang"],
      "extensions": [".dhr"],
      "configuration": "./language-configuration.json"
    }],
    "grammars": [{
      "language": "dhrlang",
      "scopeName": "source.dhrlang",
      "path": "./syntaxes/dhrlang.tmGrammar.json"
    }],
    "commands": [{
      "command": "dhrlang.runFile",
      "title": "Run DhrLang File",
      "icon": "$(play)"
    }],
    "keybindings": [{
      "command": "dhrlang.runFile",
      "key": "ctrl+f5",
      "when": "editorTextFocus && resourceExtname == .dhr"
    }]
  }
}
```

## 🌐 Web Distribution

### Official Website
Create a comprehensive website with:

1. **Download Page** - Direct downloads for all platforms
2. **Online Playground** - Try DhrLang in the browser
3. **Documentation** - Complete language reference
4. **Tutorials** - Step-by-step learning guides
5. **Examples** - Real-world code samples
6. **Community** - Forums and discussion boards

### GitHub Releases
Automated release pipeline:

`.github/workflows/release.yml`:
```yaml
name: Release

on:
  push:
    tags: ['v*']

jobs:
  build-and-release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Setup Java 17
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '17'
          
      - name: Build JAR
        run: ./gradlew shadowJar
        
      - name: Create Release
        uses: softprops/action-gh-release@v1
        with:
          files: |
            build/libs/DhrLang-*.jar
          generate_release_notes: true
          draft: false
          prerelease: false
```

## 📊 Analytics & Metrics

### Download Tracking
Implement download tracking to understand adoption:

1. **GitHub Releases** - Track download counts
2. **Package Managers** - Monitor install statistics
3. **Website** - Google Analytics for page views
4. **Docker Hub** - Pull statistics
5. **VS Code Marketplace** - Extension installs

### Usage Metrics
Optional telemetry (with user consent):

1. **Language Features** - Which features are used most
2. **Error Patterns** - Common compilation errors
3. **Performance** - Compilation and runtime performance
4. **Platforms** - Operating system distribution

## 🚀 Launch Strategy

### Phase 1: Technical Foundation
- [ ] Create all package manager configurations
- [ ] Set up automated build and release pipeline
- [ ] Develop VS Code extension
- [ ] Build official website

### Phase 2: Community Building
- [ ] Social media presence (Twitter, LinkedIn, Reddit)
- [ ] Tech blog posts and articles
- [ ] Conference presentations
- [ ] Developer meetups and workshops

### Phase 3: Ecosystem Growth
- [ ] Community contributions and plugins
- [ ] Integration with other tools
- [ ] Educational partnerships
- [ ] Corporate adoption

## 📈 Success Metrics

### Short-term (3 months)
- 1,000+ downloads across all platforms
- 100+ GitHub stars
- VS Code extension: 500+ installs
- 10+ community contributions

### Medium-term (6 months)
- 5,000+ downloads
- 500+ GitHub stars
- Featured in tech blogs/publications
- Educational institutions adoption

### Long-term (1 year)
- 20,000+ downloads
- 2,000+ GitHub stars
- Active community of 100+ regular users
- Commercial projects using DhrLang

## 🎯 Call to Action

**Ready to make DhrLang official?** Let's start with the most impactful distribution channels:

1. **Homebrew** - Easiest for Mac/Linux developers
2. **VS Code Extension** - Essential for developer experience
3. **GitHub Releases** - Foundation for all other distributions
4. **Official Website** - Central hub for documentation and downloads

Which distribution method would you like to implement first?