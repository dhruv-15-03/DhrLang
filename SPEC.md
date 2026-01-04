<!--
	DhrLang Formal Language Specification
	Status: ALPHA (0.x). This document is the single source of truth for grammar & semantics.
	Sections marked NORMATIVE define required behavior; those marked INFORMATIVE provide rationale or examples.
-->

# DhrLang Language Specification

Version: 1.1.4 (Spec synchronized with latest implemented feature set / CLI enhancements)
Stability: Stable – subject to semantic versioning.
Implementation Note: As of refactor 2025-08, all evaluation logic resides in a dedicated Evaluator component; the Interpreter is a thin façade managing environments & call depth. As of v1.1.3 (Nov 2025), IR and bytecode execution backends are available via `--backend=ir|bytecode` flags.

## 0. Overview (Informative)
DhrLang is a statically checked, interpreted, object‑oriented language with:
- Single inheritance (classes) & multiple interface implementation
- Primitive + reference types, arrays, basic generics (syntactic; limited enforcement in 0.1)
- Structured control flow, exceptions (with typed catches), static members, increment/decrement, basic standard library
- Multiple execution backends: AST (default), IR, and bytecode

This spec targets the current implementation; future enhancements (full bytecode VM optimization, advanced generics) are noted as FUTURE.

## 1. Lexical Structure (Normative)

### 1.1 Character Set
Source is UTF‑8. Tokens outside ASCII (letters, digits, underscore) are currently rejected unless part of string or char literals. (FUTURE: Unicode identifiers.)

### 1.2 Lines & Whitespace
Whitespace (space, tab, carriage return, newline) separates tokens but is otherwise insignificant except inside string/char literals.

### 1.3 Comments
``// ...`` line comment terminates at newline.
``/* ... */`` block comment may span lines. Nesting is not supported; ``/*`` within an open comment is treated as text.

### 1.4 Identifiers
Pattern: `[A-Za-z_][A-Za-z0-9_]*`
Case sensitive. Leading uppercase is *not* required but influences heuristics (e.g. static access vs instance during parse in some contexts).

### 1.5 Keywords (Reserved)
```
num duo ek sab kya kaam
class interface extends implements
if else while for break continue return
new this super
try catch finally throw
private protected public static abstract final
Override
``` 
Keywords cannot be used as identifiers. `Override` is treated like an annotation keyword (see 6.6).

### 1.6 Literals
- Integer: `[0-9]+` (base 10) → type `num` (64‑bit signed). No underscores, no hex/ octal yet.
- Floating: `[0-9]+\.[0-9]+` → type `duo` (64‑bit IEEE double). No exponent form yet.
- Boolean: `true` / `false` → type `kya`.
- Char: `'a'`, `'\n'` (single char or backslash escape; current escapes recognized: `\n`, `\t`, `\r`, `\'`, `\"`, `\\`). Invalid or multi‑char forms raise a lexical error.
- String: `"..."` with backslash escapes (same set as char). No multiline raw strings yet.
- Null literal: `null` is NOT a keyword; absence uses `null` reference value implicitly. (FUTURE: explicit `null` token.)

### 1.7 Operators & Punctuation
```
() {} [] , ; . ?
+ - * / %
== != < <= > >=
&& || ! = ++ --
```

### 1.8 Tokenization Rules
Maximal munch: longest valid token chosen. Two‐char operators (`==`, `!=`, `<=`, `>=`, `&&`, `||`, `++`, `--`) supersede single char.
Unrecognized characters produce a lexical error with hint when possible (e.g. single `&`).

## 2. Grammar (Normative)
EBNF (terminal tokens in UPPER_CASE, keywords literal):

```
program        ::= (classDecl | interfaceDecl)* EOF ;

classDecl      ::= modifiers? 'class' IDENT typeParams? superClause? implementsClause? '{' classMember* '}' ;
interfaceDecl  ::= modifiers? 'interface' IDENT typeParams? interfaceExtends? '{' interfaceMember* '}' ;

typeParams     ::= '<' typeParam (',' typeParam)* '>' ;
typeParam      ::= IDENT ( 'extends' genericType ( '&' genericType )* )? ;

superClause    ::= 'extends' IDENT genericArgs? ;
implementsClause ::= 'implements' IDENT genericArgs? (',' IDENT genericArgs? )* ;
interfaceExtends ::= 'extends' IDENT (',' IDENT)* ;

classMember    ::= fieldDecl | methodDecl ;
interfaceMember ::= interfaceMethodDecl ;

fieldDecl      ::= modifiers? type IDENT ('=' expression)? ';' ;
methodDecl     ::= modifiers? type IDENT '(' paramList? ')' ( block | ';' ) ;
interfaceMethodDecl ::= modifiers? type IDENT '(' paramList? ')' ';' ;

paramList      ::= param (',' param)* ;
param          ::= type IDENT ;

block          ::= '{' statement* '}' ;

statement      ::= block
								 | varDecl
								 | ifStmt
								 | whileStmt
								 | forStmt
								 | breakStmt
								 | continueStmt
								 | returnStmt
								 | tryStmt
								 | throwStmt
								 | expressionStmt ;

varDecl        ::= type IDENT ('=' expression)? ';' ;
ifStmt         ::= 'if' '(' expression ')' statement ('else' statement)? ;
whileStmt      ::= 'while' '(' expression ')' statement ;
forStmt        ::= 'for' '(' (varDecl | expressionStmt | ';') expression? ';' expression? ')' statement ;
breakStmt      ::= 'break' ';' ;
continueStmt   ::= 'continue' ';' ;
returnStmt     ::= 'return' expression? ';' ;
tryStmt        ::= 'try' block catchClause* finallyClause? ;
catchClause    ::= 'catch' '(' catchParam ')' block ;
catchParam     ::= (IDENT IDENT | IDENT) ; // Either type+name or just name (any)
finallyClause  ::= 'finally' block ;
throwStmt      ::= 'throw' expression ';' ;
expressionStmt ::= expression ';' ;

expression     ::= assignment ;
assignment     ::= logicalOr ( '=' assignment )? ;
logicalOr      ::= logicalAnd ( '||' logicalAnd )* ;
logicalAnd     ::= equality ( '&&' equality )* ;
equality       ::= comparison ( ('==' | '!=') comparison )* ;
comparison     ::= term ( ('<' | '<=' | '>' | '>=') term )* ;
term           ::= factor ( ('+' | '-') factor )* ;
factor         ::= unary ( ('*' | '/' | '%') unary )* ;
unary          ::= ( '!' | '-' | '++' | '--' ) unary | postfix ;
postfix        ::= primary ( ('++' | '--') )? ;
primary        ::= literal
								 | IDENT
								 | 'this'
								 | 'super' '.' IDENT
								 | newExpr
								 | arrayLiteral
								 | '(' expression ')'
								 | primarySuffix ;

primarySuffix  ::= primary ( callSuffix | fieldSuffix | indexSuffix )+ ;
callSuffix     ::= '(' argumentList? ')' ;
fieldSuffix    ::= '.' IDENT ;
indexSuffix    ::= '[' expression ']' ;

newExpr        ::= 'new' ( IDENT genericArgs? '(' argumentList? ')' | baseType '[' expression ']' ) ;
arrayLiteral   ::= '[' (expression (',' expression)*)? ']' ;
argumentList   ::= expression (',' expression)* ;

literal        ::= NUMBER | STRING | CHAR | BOOLEAN ;

type           ::= baseType arraySuffix? genericArgs? | IDENT genericArgs? arraySuffix? ;
baseType       ::= 'num' | 'duo' | 'ek' | 'sab' | 'kya' | 'kaam' ;
arraySuffix    ::= '[]' ;
genericArgs    ::= '<' genericType (',' genericType)* '>' ;
genericType    ::= '?' ( 'extends' IDENT | 'super' IDENT )? | IDENT genericArgs? ;

modifiers      ::= modifier+ ;
modifier       ::= 'public' | 'private' | 'protected' | 'static' | 'abstract' | 'final' | 'Override' ;
```

NOTE: Implementation currently permits certain generic forms but does not fully enforce all constraints (see §7.5, §12).

## 3. Operator Precedence & Associativity (Normative)
From highest to lowest:
1. Postfix increment/decrement (expr++ / expr--), member access `.`, call `()`, index `[]` (left‑assoc except postfix inc/dec which are non‑assoc)
2. Prefix unary: `! - ++ --` (right‑assoc)
3. Multiplicative: `* / %` (left)
4. Additive: `+ -` (left)
5. Comparison: `< <= > >=` (left)
6. Equality: `== !=` (left)
7. Logical AND: `&&` (left, short‑circuit)
8. Logical OR: `||` (left, short‑circuit)
9. Assignment: `=` (right‑assoc)

## 4. Types (Normative)

### 4.1 Primitive Types
| Name | Meaning | Runtime Representation |
|------|---------|------------------------|
| num  | 64‑bit signed integer | java.lang.Long |
| duo  | 64‑bit floating point | java.lang.Double |
| ek   | 16‑bit Unicode char   | java.lang.Character (char at runtime) |
| sab  | String UTF‑16          | java.lang.String |
| kya  | Boolean                | java.lang.Boolean |
| kaam | Void (no value)        | Java null (used only as method return) |

### 4.2 Reference Types
Classes, interfaces, arrays, and parameterized forms (syntactic generics). All non‑primitive except `kaam` may be `null`.

### 4.3 Arrays
Type `T[]` where `T` is any type (primitive or reference). Arrays are covariant at runtime (Java array semantics). (FUTURE: specify invariance for safety.)

Multi-dimensional arrays (`T[][]`, `T[m][n]`, etc.) are fully supported across parser, typechecker, and evaluator. Allocation and access semantics:
- Allocation: `new T[d1][d2]...[dk]` creates a k‑dimensional array; each dimension size expression must be `num (Long)` and non‑negative. Overly large sizes are rejected.
- Jagged arrays: Inner dimensions may be unspecified or allocated to different lengths later (e.g., `new T[2][]; arr[0] = new T[3];`).
- Defaults: Elements are initialized to type defaults recursively: numbers→0, duo→0.0, kya→false, references→null.
- Access: Indexing `arr[i]` is bounds-checked for each dimension; negative or `i >= length` raises an index error.

Examples:
```
num[][] m = new num[3][4];
m[0][1] = 5;
num[] row = m[2];
num x = m[2][3];

num[][] jag = new num[2][];
jag[0] = new num[1];
jag[1] = new num[3];
```

### 4.4 Class & Interface Types
Single class inheritance; multiple interfaces. Abstract classes may declare abstract methods (no body). Interfaces declare signatures only.

### 4.5 Generic Types (Current Semantics)
Parsing supports `Class<A, B>`; TypeChecker enforces arity, bounds, and performs type parameter substitution for generics in fields and methods. Generic substitution is applied for unqualified field access and assignment inside instance methods, with access control and diagnostics. Wildcards `?`, `? extends T`, `? super T` are parsed but enforcement is limited. Generic arrays (e.g. `Container<num>[]`) are supported. Full binding environment and assignability are planned for future versions.

### 4.6 Null
Any reference value (class, interface, array, parameterized) may be `null`. There is no distinct `null` literal token; `null` appears as runtime value produced by uninitialized variables, missing return expressions, or explicit default object field values.

## 5. Scoping & Binding (Normative)
Lexical scoping. Nested blocks introduce new variable scope. Variable shadowing allowed; may produce a warning. Method parameters shadow fields. `this` is implicitly defined within instance methods. `super` valid only if a superclass exists.

Resolution order for unqualified identifiers inside a method:
1. Local variables & parameters
2. Fields of the current class (including inherited)
	- In instance methods, unqualified variable access and assignment will resolve to instance fields if no local variable or parameter matches, with generic substitution and access control enforced.
	- In static methods, unqualified identifiers do NOT resolve to instance fields; diagnostics for undefined variables are preserved.
3. Global (class/interface) symbols in the root environment
Collision produces the nearest binding.

## 6. Classes, Interfaces, Members (Normative)
### 6.1 Declarations
A class declares fields & methods. Fields can be static; methods can be static/abstract/final.

### 6.2 Inheritance
`extends SuperClass` optional; absence means implicit root base (no Object injection yet). Single inheritance only.

### 6.3 Interfaces
Multiple `implements` allowed. Each interface method must be provided by a concrete (non‑abstract) class unless class is abstract.

### 6.4 Static Members
Accessible via `ClassName.member`. Static fields are initialized as follows:
- All static fields start at their type's default value (§4.1 or null).
- Then, for each class independently, static field initializers are evaluated in source order.

Static initializer constraints (Normative):
- A static field initializer in class C MUST NOT read another static field of C that is declared later in the source. Such a read is an error: code STATIC_FORWARD_REFERENCE.
- Static field initializers in class C MUST NOT form a cycle of dependencies (e.g., C.a depends on C.b which depends on C.a). Any cycle is an error: code STATIC_INIT_CYCLE.

Examples:
```
class A {
	static num x = 1;
	static num y = x + 2;        // OK: reads earlier field
}

class B {
	static num y = B.x + 1;      // ERROR [STATIC_FORWARD_REFERENCE]: forward read of x
	static num x = 1;
}

class C {
	static num a = C.b + 1;      // ERROR [STATIC_INIT_CYCLE]
	static num b = C.a + 1;
}
```

### 6.5 Access Modifiers (Normative)
Access control is enforced at compile time for fields and methods, both instance and static:

- public: accessible from any class.
- private: accessible only within the declaring class.
- protected: accessible within the declaring class and any subclass.

Rules apply uniformly to:
- Instance field get/set and method calls
- Static field access/assignment and static method calls via `ClassName.member`

Multiple access modifiers on the same member are illegal. The checker emits an error:
- Field: `Field 'x' cannot have multiple access modifiers.`
- Method: `Method 'foo' cannot have multiple access modifiers.`

Diagnostics for illegal access are reported with actionable messages, e.g.:
- Instance field read: `Cannot access field 'prop' of class 'C' due to access modifier.`
- Static field read or assignment: `Cannot access private/protected static field 'v' from class 'C'.`
- Static method call: `Cannot access private/protected static method 'm' from class 'C'.`

Examples:
```
class A {
	private num x;              // only A's methods can read/write x
	protected num y;            // A and subclasses can read/write y
	public static num z;        // accessible as A.z everywhere

	private static kaam p() {}
	protected static kaam q() {}
	public static kaam r() {}
}

class B extends A {
	static kaam main(){
		B b = new B();
		printLine(b.y);   // OK (protected in subclass)
		printLine(A.q()); // OK (protected static in subclass)
	}
}

class C {
	static kaam main(){
		printLine(A.y);   // ERROR: instance field, and protected outside subclass
		printLine(A.q()); // ERROR: protected static not visible to non-subclass
		printLine(A.p()); // ERROR: private static not visible
	}
}
```

### 6.6 Override Annotation
`Override` (capitalized) may precede a method declaration. Currently advisory; missing annotation on an override is not an error. (FUTURE: detection & warning if missing.)

### 6.7 Abstract Classes
If class has any abstract methods or explicitly declares `abstract`, it cannot be instantiated. Enforcement partial.

## 7. Type Checking (Normative)
### 7.1 Phases
1. Collect interfaces & classes (dupe detection)
2. Resolve inheritance graphs (cycle checks)
3. Build environments for fields & method signatures
4. Validate interface implementation & method bodies
5. Entry point validation (`static kaam main()` with zero params)

### 7.2 Assignment Compatibility
Primitive: exact type for now (no implicit num ↔ duo except explicit arithmetic combining which yields duo if either operand duo). (FUTURE: numeric promotions.)
Reference: same type string or (FUTURE) subclass/interface assignment; currently minimal subclass validation for fields; generics not variance checked.

### 7.3 Expressions
Binary operators require numeric operands for arithmetic/comparison except:
- `+` with sab (string) concatenates (stringify) otherwise numeric addition.
- `==` / `!=` allow any type (reference equality semantics for objects / value equality for primitives) via Java `Objects.equals`.
Logical operators require kya (boolean) and short‑circuit.

### 7.4 Return Checking
If a non‑`kaam` method has paths without `return`, runtime may yield null; the checker SHOULD (future) flag missing returns.

### 7.5 Generics Enforcement (Current)
Arity mismatch for declared generic classes detected; bounds recorded but not fully validated across use sites. Wildcards largely unchecked.

### 7.6 Null Analysis (Partial)
Tracker records some non‑null facts (infrastructure exists) but not fully exploited (FUTURE: flow lattice).

## 8. Runtime & Evaluation (Normative)
Tree‑walk interpreter executes AST directly:
1. Global environment populated with classes (as placeholders) & interfaces.
2. Class metadata constructed (methods, static methods map, static field initialization).
3. Entry point static main resolved & invoked.

Evaluation order: left‑to‑right for arguments and binary operands. Assignment returns assigned value. Increment/decrement semantics:
- Prefix: update then yield new value.
- Postfix: yield old value then update.

Recursion depth limited (current MAX = 1000) raising runtime error on overflow.

## 9. Exceptions (Normative)
Throwing: `throw expression;` wraps primitives in runtime exception object if not already DhrException. Catch matching rules:
| Declared Catch Type | Matches Categories |
|---------------------|--------------------|
| any (param only)    | All                |
| DhrException        | All DhrLang runtime categories |
| ArithmeticException | Arithmetic errors  |
| IndexOutOfBoundsException | Index errors |
| TypeException       | Type category      |
| NullPointerException| Null deref         |

Finally always executes except if runtime throws inside finally itself (propagated). `break`, `continue`, `return` rethrown through try after running finally.

### 9.1 Stringification and printing (Normative)
When an exception value (a `DhrException` or subclass) is concatenated with a string or printed via `print/printLine`, its string form is the exception message only (i.e., `ex.getMessage()`). Exception type and source location are preserved in the runtime error record if the exception escapes to the top level, but user‑level printing within programs intentionally shows just the message.

## 10. Arrays (Normative)
`new T[expr]`: expr must evaluate to num (Long) >=0; size > 1_000_000 rejected. Elements initialized to type default (§4.1 or null).
Indexing `arr[i]` bounds checked; negative or >= length raises index error.

## 11. Standard Library (Normative Summaries)
All provided as global native functions until namespacing (FUTURE). Selected categories: math.*, string.*, array.*, IO, util.* (range, sleep, type predicates). Some string instance-like methods also accessible via property access returning callable objects (e.g. `"abc".length()`).

## 12. Generics Future Semantics (Informative)
Current: Type parameter environment with substitution is implemented for generics in fields and methods, including implicit field access in instance methods. Assignment rules treat raw vs parameterized mismatch as error. Wildcard capture and variance annotations are planned for future versions. Runtime remains erased with optional debug reification tags.

## 13. Diagnostics (Normative)
Diagnostic record fields: `type (ERROR|WARNING)`, `code?`, `message`, `hint?`, `location(file?, line, column)`. De‑duplication by (type|line|col|code|message). Suppression directive:
```// @suppress: CODE1 CODE2 ...```  applies to *next* non‑empty, non‑comment line. `ALL` suppresses all codes for file if placed at top.

## 14. Conformance (Normative)
A program conforms if:
1. Lexing & parsing succeed per grammar.
2. Type checking produces no ERROR diagnostics.
3. Entry point `static kaam main()` exists and has arity 0.
4. Runtime terminates without uncaught runtime error (unless test intentionally asserts error).

## 15. Deviations & Known Limitations (Informative)
- Generics are now enforced with type parameter substitution and diagnostics; generic arrays and deep type substitution are supported for fields and methods.
- Multi-dimensional arrays are fully implemented and supported in all language components.
- (Removed) Static forward reference ambiguity: Now enforced at compile-time per §6.4 (STATIC_FORWARD_REFERENCE, STATIC_INIT_CYCLE).
- No method overloading; duplicate name rejected.
- No package / module system; single global namespace.
- No `null` literal token (implicit only).
- No escape sequences beyond basic set.
- Arithmetic overflow not trapped.

## 16. Reserved for Future Features
| Feature | Planned Section |
|---------|-----------------|
| Bytecode optimization & JIT | §17 (optimization work and potential JIT hooks) |
| Modules / Imports | §18 |
| Formatter / LSP | §19 |

## 17. IR & Bytecode Backends (Implemented as of v1.1.3)
DhrLang includes IR and bytecode backends accessible via `--backend=ir` or `--backend=bytecode` CLI flags.

### 17.1 IR (Intermediate Representation)
The IR backend lowers AST to a structured intermediate representation with:
- Functions containing linear instruction sequences
- Instructions: const, load/store local, binary/unary ops, compare, jumps, labels, print, arrays, fields, calls
- Exception handling: `IrThrow`, `IrTryPush`, `IrTryPop`, `IrCatchBind`
- Typed exception matching for `any`, `Error`, `DhrException`, and custom exception types

### 17.2 Bytecode Format
Stack-based bytecode (DHBC v2) with:
- Magic number, version, constant pool, function table
- Opcodes including: LOAD, STORE, CONST, arithmetic ops, comparisons, jumps, calls, arrays, fields, exceptions
- Exception opcodes: TRY_PUSH, TRY_POP, THROW, CATCH_BIND
- Serialization to `.dbc` files via `--emit-bc` flag

### 17.3 Bytecode VM
Executes bytecode with:
- Call stack with frames containing locals, operand stack, and exception handlers
- Static field storage
- Handler stack per frame for nested try-catch-finally
- Typed exception matching consistent with AST interpreter

### 17.4 Parity & Status
- Full parity tests between AST/IR/bytecode for arrays, calls, fields, and exceptions
- AST remains the default CLI backend for compatibility, but IR and bytecode are intended to be semantically equivalent for the implemented feature set.

Security/robustness notes (informative):
- The bytecode VM validates input bytecode (bounds, indices, and structural constraints) before execution.
- For untrusted code, run with JVM property `dhrlang.bytecode.untrusted=true` to enable conservative defaults and tighter limits.
- A shared instruction step limit exists via `dhrlang.backend.maxSteps`.

## 18. Change Log Policy (Normative)
Each release MUST update `CHANGELOG.md` with Added / Changed / Fixed / Deprecated / Removed / Security headings. Semantic Versioning adopted at 1.0.

## 19. Glossary
- *Erasure*: Removal of generic parameter information at runtime.
- *Assignability*: Relation allowing value of one type to be stored in variable of another per §7.2.
- *Conformance Test*: A test asserting spec compliance; MUST not rely on unspecified behavior.

## 20. Compliance Test Categories (Informative)
1. Lexical edge (unterminated strings, invalid char literals)
2. Parsing precedence & associativity
3. Type mismatch enforcement
4. Interface implementation obligations
5. Static vs instance access
6. Exception flow & finally ordering
7. Array bounds & size limits
8. Recursion depth overflow handling
9. Shadowing & warning emission (optional)
10. Generic syntax acceptance & misuse errors

---
End of DhrLang Specification v0.1

