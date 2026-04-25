# DhrLang Error Codes Reference

> **Version:** 3.0.0  
> **Last Updated:** January 2026

DhrLang uses a unique error code system for easy reference and searchability. Each error includes:
- **Unique Code**: Easy to search (e.g., "DHR-E201 fix")
- **Line & Column**: Exact location in your code
- **Helpful Hint**: Actionable suggestion to resolve the issue

---

## Error Code Format

```
DHR-[E/W][NNN]
 â”‚    â”‚   â”‚
 â”‚    â”‚   â””â”€â”€ 3-digit number
 â”‚    â””â”€â”€â”€â”€â”€â”€ E=Error, W=Warning
 â””â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ DhrLang prefix
```

**Ranges:**
- `DHR-E001 - DHR-E099`: Lexical Errors (tokenization)
- `DHR-E100 - DHR-E199`: Parse Errors (syntax)
- `DHR-E200 - DHR-E299`: Type Errors (type checking)
- `DHR-E300 - DHR-E399`: Runtime Errors (execution)
- `DHR-E400 - DHR-E499`: Array Errors
- `DHR-E500 - DHR-E599`: Class/Object Errors
- `DHR-E600 - DHR-E699`: Native Function Errors
- `DHR-W001 - DHR-W099`: Style Warnings
- `DHR-W100 - DHR-W199`: Code Quality Warnings

---

## Lexical Errors (DHR-E001 - DHR-E099)

| Code | Name | Description | Common Fix |
|------|------|-------------|------------|
| DHR-E001 | UNTERMINATED_STRING | String literal is not closed | Add closing `"` |
| DHR-E002 | INVALID_CHARACTER | Unsupported character in source | Remove or replace the character |
| DHR-E003 | INVALID_CHAR_LITERAL | Invalid character literal format | Use `'a'` or `'\n'` format |

---

## Parse Errors (DHR-E100 - DHR-E199)

| Code | Name | Description | Common Fix |
|------|------|-------------|------------|
| DHR-E101 | MISSING_SEMICOLON | Statement not terminated | Add `;` at end of statement |
| DHR-E102 | UNMATCHED_BRACE | Braces don't match | Check `{` and `}` pairs |
| DHR-E103 | MISSING_PARENTHESIS | Parentheses don't match | Check `(` and `)` pairs |
| DHR-E104 | INVALID_SYNTAX | General syntax error | Check DhrLang syntax guide |
| DHR-E105 | REDECLARATION | Symbol already defined | Use unique name or remove duplicate |

---

## Type Errors (DHR-E200 - DHR-E299)

| Code | Name | Description | Common Fix |
|------|------|-------------|------------|
| DHR-E201 | TYPE_MISMATCH | Types are incompatible | Use correct type or convert explicitly |
| DHR-E202 | UNDECLARED_IDENTIFIER | Variable/class not declared | Declare before use, check spelling |
| DHR-E203 | GENERIC_ARITY | Wrong number of type arguments | Check generic type signature |

---

## Runtime Errors (DHR-E300 - DHR-E399)

| Code | Name | Description | Common Fix |
|------|------|-------------|------------|
| DHR-E301 | NULL_DEREFERENCE | Accessing null reference | Initialize object before use |
| DHR-E302 | DIVISION_BY_ZERO | Dividing by zero | Check divisor before dividing |
| DHR-E399 | INTERNAL_ERROR | Compiler/interpreter bug | Report issue on GitHub |

---

## Array Errors (DHR-E400 - DHR-E499)

| Code | Name | Description | Common Fix |
|------|------|-------------|------------|
| DHR-E401 | BOUNDS_VIOLATION | Array index out of valid range | Check `0 <= index < arrayLength(arr)` |
| DHR-E402 | NEGATIVE_ARRAY_SIZE | Array size is negative | Use non-negative size |
| DHR-E403 | ARRAY_SIZE_TOO_LARGE | Array exceeds max size (1M) | Use smaller array or chunk data |

---

## Class/Object Errors (DHR-E500 - DHR-E599)

| Code | Name | Description | Common Fix |
|------|------|-------------|------------|
| DHR-E501 | ACCESS_MODIFIER | Private/protected access violation | Use public member or accessor method |

---

## Native Function Errors (DHR-E600 - DHR-E699)

| Code | Name | Description | Common Fix |
|------|------|-------------|------------|
| DHR-E601 | NATIVE_ARITY | Wrong argument count | Check function signature |
| DHR-E602 | UNKNOWN_NATIVE | Unknown native function | Check function name spelling |

---

## Style Warnings (DHR-W001 - DHR-W099)

| Code | Name | Description | Suggestion |
|------|------|-------------|------------|
| DHR-W001 | UNUSED_VARIABLE | Variable declared but never used | Remove or use the variable |
| DHR-W002 | UNUSED_PARAMETER | Parameter declared but never used | Remove or use the parameter |
| DHR-W003 | VARIABLE_SHADOWING | Inner variable hides outer | Use different name |
| DHR-W004 | EMPTY_BLOCK | Block has no statements | Add code or remove block |

---

## Code Quality Warnings (DHR-W100 - DHR-W199)

| Code | Name | Description | Suggestion |
|------|------|-------------|------------|
| DHR-W101 | UNREACHABLE_CODE | Code after return/throw | Remove unreachable code |
| DHR-W102 | DEAD_STORE | Value written but never read | Remove useless assignment |
| DHR-W103 | CONSTANT_CONDITION | if/while condition is always same | Check logic, use variable |
| DHR-W104 | REDUNDANT_NULL_CHECK | Checking non-null for null | Remove unnecessary check |
| DHR-W105 | POSSIBLE_NULL_DEREFERENCE | May be accessing null | Add null check |
| DHR-W106 | STATIC_FORWARD_REFERENCE | Static field uses later field | Reorder declarations |
| DHR-W107 | STATIC_INIT_CYCLE | Circular static initialization | Break dependency cycle |

---

## Example Error Output

```
â•”â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•—
â•‘  âŒ ERROR [DHR-E401] at program.dhr:15:12                        â•‘
â• â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•£
â•‘  Array index 10 out of bounds for array of length 5              â•‘
â•‘                                                                   â•‘
â•‘   13 |     num[] arr = new num[5];                               â•‘
â•‘   14 |     for(num i = 0; i <= 10; i++) {                        â•‘
â•‘ > 15 |         arr[i] = i * 2;                                   â•‘
â•‘      |             ^                                              â•‘
â•‘   16 |     }                                                      â•‘
â•‘                                                                   â•‘
â•‘  ðŸ’¡ Hint: Index 10 exceeds valid range [0-4].                    â•‘
â•‘           Change loop to 'i < 5' or 'i < arrayLength(arr)'       â•‘
â•šâ•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•
```

---

## Suppressing Warnings

Add a suppress comment before the line:

```dhrlang
// @suppress DHR-W001
num unusedVar = 42;  // No warning
```

Or suppress for entire file:

```dhrlang
// @suppress-file DHR-W001 DHR-W002
class MyClass {
    // ...
}
```

---

## Getting Help

- **Search online**: Google "DHR-E201" for discussions
- **GitHub Issues**: [Report bugs](https://github.com/dhruv-15-03/DhrLang/issues)
- **Documentation**: See [SPEC.md](SPEC.md) for language reference

---

**ðŸ‡®ðŸ‡³ DhrLang - Clear errors, happy developers | à¤¸à¥à¤ªà¤·à¥à¤Ÿ à¤¤à¥à¤°à¥à¤Ÿà¤¿à¤¯à¤¾à¤, à¤–à¥à¤¶ à¤¡à¥‡à¤µà¤²à¤ªà¤°à¥à¤¸**

