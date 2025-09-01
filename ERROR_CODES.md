# Error Codes

Generated automatically. Edit descriptions below.

| Code | Description |
|------|-------------|
| ACCESS_MODIFIER | Illegal access to a non-visible field or method (private/protected rules violated). |
| BOUNDS_VIOLATION | Array index or size out of valid range (negative or ≥ length). |
| CONSTANT_CONDITION | Condition in control flow is a constant literal (likely logic issue). |
| DEAD_STORE | Value is written but never read before being overwritten or scope end. |
| EMPTY_BLOCK | Block contains no executable statements. |
| GENERIC_ARITY | Wrong number of generic type arguments in use or construction. |
| INTERNAL_ERROR | Unexpected internal compiler/interpreter failure (bug). |
| NATIVE_ARITY | Wrong number of arguments passed to a native function. |
| NULL_DEREFERENCE | Definite null dereference (object is known null). |
| POSSIBLE_NULL_DEREFERENCE | Potential null dereference (object may be null). |
| REDECLARATION | Symbol redefined in the same scope (class, field, method, variable). |
| REDUNDANT_NULL_CHECK | Null check on a value already proven non-null. |
| TYPE_MISMATCH | Expression type incompatible with expected target type. |
| UNDECLARED_IDENTIFIER | Use of a variable/class/member not declared or not in scope. |
| UNKNOWN_NATIVE | Reference to a native function name not registered. |
| UNREACHABLE_CODE | Code after a terminating statement (return/throw) cannot run. |
| UNUSED_PARAMETER | Function parameter declared but not used. |
| UNUSED_VARIABLE | Local variable declared but never read. |
| VARIABLE_SHADOWING | Local variable hides another variable from an outer scope. |
