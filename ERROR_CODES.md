# Error Codes

Generated automatically. Edit descriptions below.

| Code | Description |
|------|-------------|
| ACCESS_MODIFIER | Illegal access to a non-visible field or method (private/protected rules violated). Applies to instance and static members; protected is only visible to the declaring class and subclasses. |
| BOUNDS_VIOLATION | Array index or size out of valid range (negative or ≥ length), or allocation exceeds the allowed maximum size. Applies equally to multi-dimensional arrays. |
| CONSTANT_CONDITION | Condition in control flow is a constant literal (likely logic issue). |
| DEAD_STORE | Value is written but never read before being overwritten or scope end. |
| EMPTY_BLOCK | Block contains no executable statements. |
| GENERIC_ARITY | Wrong number of generic type arguments in use or construction (including missing type arguments when required). |
| INTERNAL_ERROR | Unexpected internal compiler/interpreter failure (bug). |
| NATIVE_ARITY | Wrong number of arguments passed to a native function. |
| NULL_DEREFERENCE | Definite null dereference (object is known null). |
| POSSIBLE_NULL_DEREFERENCE | Potential null dereference (object may be null). |
| REDECLARATION | Symbol redefined in the same scope (class, field, method, variable). |
| REDUNDANT_NULL_CHECK | Null check on a value already proven non-null. |
| TYPE_MISMATCH | Expression type incompatible with expected target type. Includes mismatches after generic type parameter substitution (e.g., assigning sab to a field of type T where T resolves to num). |
| UNDECLARED_IDENTIFIER | Use of a variable/class/member not declared or not in scope. In static contexts, unqualified names do not resolve to instance fields (a diagnostic is emitted instead of implicit resolution). |
| UNKNOWN_NATIVE | Reference to a native function name not registered. |
| UNREACHABLE_CODE | Code after a terminating statement (return/throw) cannot run. |
| UNUSED_PARAMETER | Function parameter declared but not used. |
| UNUSED_VARIABLE | Local variable declared but never read. |
| VARIABLE_SHADOWING | Local variable hides another variable from an outer scope. |
| STATIC_FORWARD_REFERENCE | Static field initializer reads a same-class static field declared later (illegal forward reference). Detected through nested expressions, including array and multi-dimensional initializers. |
| STATIC_INIT_CYCLE | Static fields have an initialization dependency cycle (no valid order). Cycles are reported even if they occur indirectly through expressions or array initializers. |
