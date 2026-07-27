# DhrLang Installation Guide

## System Requirements

- **Java**: JDK 17 or higher
- **Operating System**: Windows, macOS, Linux
- **Memory**: 512MB RAM minimum
- **Disk Space**: 50MB for runtime, 200MB for development

## Installation Methods

### 🚀 Method 1: Binary Release (Easiest)

1. **Download**: Go to [GitHub Releases](https://github.com/dhruv-15-03/DhrLang/releases/latest)
2. **Extract**: Unzip `DhrLang-x.x.x-distribution.zip`
3. **Verify**: 
   ```bash
   java -jar lib/DhrLang-<version>.jar --version
   ```

### 🔧 Method 2: Package Managers

#### Homebrew (macOS/Linux)
```bash
# Coming soon
brew install dhruv-15-03/tap/dhrlang
dhrlang --version
```

#### Chocolatey (Windows)
```powershell
# Coming soon  
choco install dhrlang
dhrlang --version
```

#### Snap (Linux)
```bash
# Coming soon
sudo snap install dhrlang
dhrlang --version
```

### 🛠️ Method 3: Build from Source

#### Prerequisites
```bash
# Install Java 17+
java -version

# Install Git
git --version
```

#### Build Steps
```bash
# Clone repository
git clone https://github.com/dhruv-15-03/DhrLang.git
cd DhrLang

# Build (this runs tests automatically)
./gradlew build

# Create distribution
./gradlew packageDistribution

# Install globally (optional)
./gradlew installDist
```

## IDE Integration

### Visual Studio Code

1. **Install Extension**:
   ```
   Ctrl+Shift+X → Search "DhrLang" → Install
   ```

2. **Configure**:
   ```json
   // settings.json
   {
       "dhrlang.jarPath": "/path/to/DhrLang-<version>.jar",
       "dhrlang.autoDetectJar": true,
       "dhrlang.enableErrorSquiggles": true
   }
   ```

### IntelliJ IDEA

1. **File Association**:
   - `Settings → Editor → File Types`
   - Add `*.dhr` to Text files

2. **External Tool**:
   ```
   Name: Run DhrLang
   Program: java
   Arguments: -jar $ProjectFileDir$/DhrLang-<version>.jar $FilePath$
   Working Dir: $ProjectFileDir$
   ```

### Vim/Neovim

```vim
" Add to .vimrc
autocmd BufNewFile,BufRead *.dhr set filetype=java
autocmd BufNewFile,BufRead *.dhr set syntax=java
```

## Environment Setup

### Create Workspace
```bash
mkdir my-dhrlang-project
cd my-dhrlang-project

# Create basic structure
mkdir src examples
echo "class Main { static kaam main() { printLine(\"Hello DhrLang!\"); } }" > src/main.dhr
```

### Shell Integration

#### Bash/Zsh
```bash
# Add to ~/.bashrc or ~/.zshrc
export DHRLANG_HOME="/path/to/dhrlang"
alias dhrlang="java -jar $DHRLANG_HOME/DhrLang-<version>.jar"

# Usage
dhrlang src/main.dhr
```

#### PowerShell
```powershell
# Add to $PROFILE
$env:DHRLANG_HOME = "C:\tools\dhrlang"
function dhrlang { java -jar "$env:DHRLANG_HOME\DhrLang-<version>.jar" $args }

# Usage
dhrlang src\main.dhr
```

## Docker Setup

### Dockerfile
```dockerfile
FROM openjdk:17-alpine

# Install DhrLang
COPY DhrLang-<version>.jar /usr/local/bin/dhrlang.jar
RUN echo '#!/bin/sh\njava -jar /usr/local/bin/dhrlang.jar "$@"' > /usr/local/bin/dhrlang && \
    chmod +x /usr/local/bin/dhrlang

WORKDIR /workspace
ENTRYPOINT ["dhrlang"]
```

### Usage
```bash
# Build image
docker build -t dhrlang:latest .

# Run program
docker run --rm -v $(pwd):/workspace dhrlang:latest src/main.dhr
```

## Verification

### Test Installation
```bash
# Check version
java -jar DhrLang-<version>.jar --version

# Run sample program
java -jar DhrLang-<version>.jar examples/sample.dhr

# Run with JSON output
java -jar DhrLang-<version>.jar --json examples/sample.dhr
```

### Expected Output
```
DhrLang version <version>
```

## Troubleshooting

### Common Issues

#### Java Version Error
```
Error: Unsupported Java version
Solution: Install JDK 17+
```

#### File Not Found
```
Error: Could not read file
Solution: Check file path and permissions
```

#### Compilation Error
```
Error: Syntax error at line X
Solution: Check syntax against SPEC.md
```

### Performance Tuning

```bash
# Increase memory for large programs
java -Xmx2g -jar DhrLang-<version>.jar program.dhr

# Enable detailed garbage collection
java -XX:+UseG1GC -XX:+PrintGCDetails -jar DhrLang-<version>.jar program.dhr
```

## Next Steps

1. **Learn Syntax**: Read [GETTING_STARTED.md](GETTING_STARTED.md)
2. **Try Examples**: Explore `examples/` directory
3. **Join Community**: Visit [GitHub Discussions](https://github.com/dhruv-15-03/DhrLang/discussions)
4. **Contribute**: See [CONTRIBUTING.md](CONTRIBUTING.md)

## Uninstall

```bash
# Remove binaries
rm -rf /path/to/dhrlang

# Remove shell aliases (from ~/.bashrc, etc.)
# Remove IDE extensions manually
```

---

**Need Help?** Open an issue on [GitHub](https://github.com/dhruv-15-03/DhrLang/issues) or contact dhruv.rastogi@example.com