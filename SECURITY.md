# Security Policy

## Supported Versions

We provide security updates for the following versions of DhrLang:

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| < 1.0   | :x:                |

## Reporting a Vulnerability

We take security vulnerabilities seriously. If you discover a security vulnerability in DhrLang, please report it privately.

### How to Report

1. **Email**: Send details to dhruv.rastogi@example.com with subject "SECURITY: DhrLang Vulnerability"
2. **GitHub**: Use GitHub's private vulnerability reporting feature
3. **Include**: 
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if known)

### Response Timeline

- **Initial Response**: Within 48 hours
- **Assessment**: Within 1 week
- **Fix Development**: Within 2 weeks (for critical issues)
- **Public Disclosure**: After fix is released and users have time to update

### Security Considerations

DhrLang processes user code and executes it. Key security areas:

#### Code Execution
- Input validation for source code
- Memory safety in interpreter
- Resource usage limits
- Sandbox execution environment

#### File System Access
- Restricted file operations
- Path traversal prevention
- Permission validation

#### Memory Safety
- Bounds checking for arrays
- Stack overflow prevention
- Memory leak detection

### Best Practices for Users

1. **Input Validation**: Always validate user-provided DhrLang code
2. **Sandboxing**: Run DhrLang programs in isolated environments
3. **Resource Limits**: Set appropriate timeouts and memory limits
4. **Updates**: Keep DhrLang updated to the latest version
5. **Access Control**: Limit file system and network access

### Security Features

- **Type Safety**: Strong static typing prevents many runtime errors
- **Bounds Checking**: Array access is bounds-checked at runtime
- **Exception Handling**: Controlled error propagation
- **Input Sanitization**: Lexer validates input tokens

### Known Security Considerations

1. **Resource Exhaustion**: Infinite loops can consume CPU
2. **Memory Usage**: Large data structures can consume memory
3. **File Access**: Programs can read/write files if allowed
4. **Reflection**: Limited reflection capabilities reduce attack surface

### Reporting Non-Security Issues

For non-security bugs and feature requests, please use GitHub Issues.

Thank you for helping keep DhrLang secure! 🔒
