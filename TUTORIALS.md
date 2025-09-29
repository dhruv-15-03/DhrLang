# DhrLang Tutorials
# DhrLang Tutorials (Accurate Syntax Edition)

This updated guide reflects the actual implemented DhrLang syntax present in repository test programs. The previous draft used legacy Hindi tokens and Java standard-library APIs that the current compiler does not implement. All runnable code below follows the working grammar (tokens like num, duo, sab, kya, ek, kaam, class, extends, static, etc.).

> Hindi explanatory comments remain, but code tokens are the English-core form used by the compiler today.

## 🔑 Quick Reference

| Concept | Token / Pattern |
|---------|-----------------|
| Entry point | `class Main { static kaam main() { ... } }` |
| Primitive types | `num`, `duo` (floating), `sab` (string), `kya` (boolean), `ek` (char), `any` (experimental wildcard) |
| Void-like | `kaam` |
| Control flow | `if`, `else`, `for`, `while`, `break`, `continue` |
| OOP | `class`, `extends`, `this`, `super` + access: `public`, `private`, `protected` |
| Arrays | `type[] a = [1,2];` or `type[] a = new type[n];` |
| Built-ins (observed) | `printLine`, `arrayLength` |
| String helpers (grammar) | `substring`, `replace`, `charAt` |
| Initialization pattern | Provide `kaam init(...)` method & call post construction |

Experimental / incomplete: advanced exceptions, full generics semantics, Java collections, static init blocks, switch-case.

## 📚 Tutorial Index

1. [Hello World & Basic Syntax](#1-hello-world--basic-syntax)
2. [Variables & Data Types](#2-variables--data-types)
3. [Control Flow](#3-control-flow)
4. [Functions & Methods](#4-functions--methods)
5. [Arrays](#5-arrays)
6. [Object-Oriented Programming](#6-object-oriented-programming)
7. [Error Handling (Experimental)](#7-error-handling-experimental)
8. [Strings & Built-ins](#8-strings--built-ins)
9. [Generics (Experimental)](#9-generics-experimental)
10. [Access Modifiers](#10-access-modifiers)
11. [Static Members](#11-static-members)
12. [Best Practices](#12-best-practices)
13. [Practice Exercises](#-practice-exercises)

---

## 1. Hello World & Basic Syntax

Create `tutorial01.dhr`:

```dhrlang
class Main {
    static kaam main() {
        sab greeting = "Hello, DhrLang!"; // अभिवादन
        num times = 3;
        for (num i = 1; i <= times; i++) {
            printLine(greeting + " (#" + i + ")");
        }
        return; // optional in kaam
    }
}
```

Run:
```
java -jar DhrLang.jar tutorial01.dhr
```

Notes:
- Entry signature is `static kaam main()` inside a class.
- Output with `printLine`.
- Comments use `//`.
- Strings are `sab`.

> Legacy Hindi tokens like `मुख्य`, `प्रिंट` are not accepted by the current runtime.

---

## 2. Variables & Data Types

```dhrlang
class VarsDemo {
    static kaam main() {
        num count = 5;            // पूर्णांक
        duo ratio = 3.14159;      // दशमलव
        sab title = "Demo";       // स्ट्रिंग
        kya flag = true;          // बूलियन
        ek letter = 'A';          // अक्षर

        printLine("Count: " + count);
        printLine("Ratio: " + ratio);
        printLine("Title: " + title);
        printLine("Flag: " + flag);
        printLine("Letter: " + letter);
    }
}
```

> Java parsing helpers and casting examples removed—no standard library parity yet.

---

## 3. Control Flow

```dhrlang
class FlowDemo {
    static kaam main() {
        kya flag = true;
        if (flag) { printLine("Flag true"); } else { printLine("Flag false"); }

        for (num i = 1; i <= 3; i++) {
            printLine("Loop iteration: " + i);
        }

        num w = 0;
        while (w < 2) {
            printLine("While: " + w);
            w++;
        }
    }
}
```

> Switch-case & Hindi control keywords removed (unsupported in current examples).

---

## 4. Functions & Methods

```dhrlang
class FuncDemo {
    num add(num a, num b) { return a + b; }
    sab greet(sab name) { return "Hello, " + name; }

    static kaam main() {
        FuncDemo f = new FuncDemo();
        num r = f.add(10, 20);
        sab g = f.greet("DhrLang");
        printLine("Sum: " + r);
        printLine(g);
    }
}
```

> Overloading examples removed (not in confirmed tests).

---

## 5. Arrays

```dhrlang
class ArrayDemo {
    static kaam main() {
        num[] numbers = [1, 2, 3, 4];
        sab[] names = ["Alice", "Bob", "Charlie"];

        printLine("First: " + numbers[0]);
        numbers[2] = 99;
        printLine("Updated index 2: " + numbers[2]);

        for (num i = 0; i < arrayLength(numbers); i++) {
            printLine("numbers[" + i + "] = " + numbers[i]);
        }

        num[] dyn = new num[3];
        dyn[0] = 10; dyn[1] = 20; dyn[2] = 30;

        num total = 0;
        for (num i = 0; i < arrayLength(dyn); i++) {
            total = total + dyn[i];
        }
        printLine("Total: " + total);
    }
}
```

> Removed Java collections / ArrayList.

---

## 6. Object-Oriented Programming

```dhrlang
class Animal {
    protected sab name;
    protected num age;
    kaam init(sab name, num age) { this.name = name; this.age = age; }
    sab getName() { return this.name; }
    num getAge() { return this.age; }
    kaam makeSound() { printLine(this.name + " makes a sound"); }
}

class Dog extends Animal {
    private sab breed;
    kaam init(sab name, num age, sab breed) { super.init(name, age); this.breed = breed; }
    kaam makeSound() { printLine(this.name + " barks!"); }
    kaam showBreed() { printLine("Breed: " + this.breed); }
}

class OOPDemo {
    static kaam main() {
        Dog d = new Dog("Buddy", 3, "Golden Retriever");
        d.makeSound();
        printLine("Dog age: " + d.getAge());
        d.showBreed();
    }
}
```

> Replaces earlier Hindi token class syntax & Java annotations.

---

## 7. Error Handling (Experimental)

Full try/catch semantics & custom exception classes not confirmed in current test corpus. Treat as provisional.

```dhrlang
// PSEUDO ONLY – not guaranteed to compile yet
kaam risky() { /* ... */ }
class FutureExample { static kaam main() { /* try { risky(); } catch (SomeError e) { printLine("Error"); } */ } }
```

---

## 8. Strings & Built-ins

Observed helpers: `substring`, `replace`, `charAt` plus `+` concatenation.

```dhrlang
class StringDemo {
    static kaam main() {
        sab text = "Hello DhrLang";
        sab part = substring(text, 0, 5); // if implemented
        sab repl = replace(text, "DhrLang", "World");
        printLine(part);
        printLine(repl);
        printLine("First char: " + charAt(text, 0));
    }
}
```

> Removed: toUpperCase, indexOf, StringBuilder, format utilities (unimplemented).

---

## 9. Generics (Experimental)

Generic parsing exists (per warnings & spec) but runtime behaviors remain evolving.

```dhrlang
// Placeholder – using any instead of true type parameter substitution
class Box { any value; kaam init(any v) { this.value = v; } any get() { return this.value; } }
```

---

## 10. Access Modifiers

```dhrlang
class BankAccount {
    private num balance;
    public sab owner;
    protected sab note;
    kaam init(sab owner, num balance) { this.owner = owner; this.balance = balance; }
    num getBalance() { return this.balance; }
    kaam deposit(num amt) { if (amt > 0) { this.balance = this.balance + amt; } }
}
```

---

## 11. Static Members

```dhrlang
class Counter {
    static num total = 0;
    kaam init() { total = total + 1; }
}

class StaticDemo {
    static kaam main() {
        Counter c1 = new Counter();
        Counter c2 = new Counter();
        printLine("Created: " + Counter.total);
    }
}
```

> Complex static init blocks omitted until stabilized.

---

## 12. Best Practices

1. Single clear entry: only one `static kaam main()` per program file executed.
2. Favor explicit primitive types; avoid `any` unless bridging experiments.
3. Keep loops small; extract logic into methods.
4. Guard array bounds manually before indexing when dynamic.
5. Use a dedicated `init` method in lieu of constructors for now.
6. Remove diagnostic `printLine` calls before publishing examples.
7. Consistent naming (`i`, `count`, `total`, `result`).
8. Segregate responsibility: each class should model one concept.
9. Prefer early `return` in `kaam` to flatten nested conditionals.
10. Document experimental features so readers know instability risk.

---

## 🎯 Practice Exercises

### Exercise 1: Prime Scanner
Write a program that:
1. Reads an array of numbers (hardcode for now)
2. Prints primes only
3. Reports count & sum of primes

### Exercise 2: Simple Ledger
Design:
1. `Transaction` class (amount sab description)
2. Array of transactions
3. Functions to compute total debit/credit
4. Print summary

### Exercise 3: Mini OOP – Animals
Implement:
1. Base `Animal` with `init(name, age)` & `makeSound`
2. Derived `Cat` / `Dog` override `makeSound`
3. Store in array and iterate invoking polymorphic behavior

---

## 📖 Additional Resources

- Specification: `SPEC.md`
- Sample Programs: `input/`
- Error Codes: `ERROR_CODES.md`
- Runtime / Compiler Tests: `src/test/java`

---

**Happy Coding with DhrLang! 🚀**

> Legacy Hindi tokens (`मुख्य`, `प्रिंट`, `अगर`, etc.) intentionally removed. A future bilingual compatibility mode can reintroduce them in a separate appendix when implemented.