# DhrLang Tutorials

Welcome to DhrLang! These tutorials will guide you from basic concepts to advanced features.

## 📚 Tutorial Index

### Beginner Level
1. [Hello World & Basic Syntax](#1-hello-world--basic-syntax)
2. [Variables & Data Types](#2-variables--data-types)
3. [Control Flow](#3-control-flow)
4. [Functions](#4-functions)

### Intermediate Level
5. [Arrays & Collections](#5-arrays--collections)
6. [Object-Oriented Programming](#6-object-oriented-programming)
7. [Exception Handling](#7-exception-handling)
8. [String Manipulation](#8-string-manipulation)

### Advanced Level
9. [Generics & Type System](#9-generics--type-system)
10. [Access Modifiers](#10-access-modifiers)
11. [Static Members & Initialization](#11-static-members--initialization)
12. [Best Practices](#12-best-practices)

---

## 1. Hello World & Basic Syntax

### Your First Program
Create `tutorial01.dhr`:

```dhrlang
// This is a comment - टिप्पणी
मुख्य() {
    प्रिंट("नमस्ते, DhrLang!");
    प्रिंट("Hello, World!");
}
```

**Key Points:**
- `मुख्य()` is the main function (entry point)
- `प्रिंट()` outputs text to console
- Semicolons `;` end statements
- Comments use `//` for single line

### Mixed Language Support
```dhrlang
main() {
    print("English keywords work too!");
    // You can mix Hindi and English
    संख्या count = 5;
    print("Count: " + count);
}
```

**Run it:** `java -jar DhrLang.jar tutorial01.dhr`

---

## 2. Variables & Data Types

### Basic Types
```dhrlang
मुख्य() {
    // Numbers - संख्या
    संख्या age = 25;
    संख्या population = 1400000000;
    
    // Decimals - दशमलव
    दशमलव price = 99.99;
    दशमलव pi = 3.14159;
    
    // Strings - स्ट्रिंग
    स्ट्रिंग name = "राहुल";
    स्ट्रिंग city = "दिल्ली";
    
    // Booleans - बूलियन
    बूलियन isStudent = true;
    बूलियन hasJob = false;
    
    // Characters - चार
    चार grade = 'A';
    चार symbol = '₹';
    
    // Output
    प्रिंट("Name: " + name);
    प्रिंट("Age: " + age);
    प्रिंट("Price: ₹" + price);
    प्रिंट("Student: " + isStudent);
}
```

### Type Conversion
```dhrlang
मुख्य() {
    स्ट्रिंग numberStr = "42";
    संख्या num = Integer.parseInt(numberStr);
    
    दशमलव decimal = 3.14;
    संख्या rounded = (संख्या) decimal; // Casting
    
    प्रिंट("String to number: " + num);
    प्रिंट("Decimal to int: " + rounded);
}
```

---

## 3. Control Flow

### Conditions (शर्तें)
```dhrlang
मुख्य() {
    संख्या age = 20;
    
    अगर (age >= 18) {
        प्रिंट("You can vote! 🗳️");
    } नहीं तो अगर (age >= 16) {
        प्रिंट("You can get a license! 🚗");
    } नहीं तो {
        प्रिंट("You're still young! 👶");
    }
    
    // Switch-case equivalent
    संख्या day = 1;
    स्विच (day) {
        केस 1: प्रिंट("Monday"); break;
        केस 2: प्रिंट("Tuesday"); break;
        डिफ़ॉल्ट: प्रिंट("Other day");
    }
}
```

### Loops (लूप)
```dhrlang
मुख्य() {
    // For loop - के लिए
    प्रिंट("Counting 1 to 5:");
    के लिए (संख्या i = 1; i <= 5; i++) {
        प्रिंट("Count: " + i);
    }
    
    // While loop - जबकि
    संख्या countdown = 3;
    प्रिंट("Countdown:");
    जबकि (countdown > 0) {
        प्रिंट(countdown);
        countdown--;
    }
    प्रिंट("Blast off! 🚀");
    
    // Enhanced for loop
    संख्या[] numbers = {1, 2, 3, 4, 5};
    के लिए (संख्या num : numbers) {
        प्रिंट("Number: " + num);
    }
}
```

---

## 4. Functions

### Basic Functions
```dhrlang
// Function definition
संख्या add(संख्या a, संख्या b) {
    वापसी a + b;
}

स्ट्रिंग greet(स्ट्रिंग name) {
    वापसी "नमस्ते, " + name + "!";
}

मुख्य() {
    संख्या result = add(10, 20);
    स्ट्रिंग message = greet("अमित");
    
    प्रिंट("Sum: " + result);
    प्रिंट(message);
}
```

### Function Overloading
```dhrlang
// Same name, different parameters
संख्या multiply(संख्या a, संख्या b) {
    वापसी a * b;
}

दशमलव multiply(दशमलव a, दशमलव b) {
    वापसी a * b;
}

स्ट्रिंग multiply(स्ट्रिंग str, संख्या times) {
    स्ट्रिंग result = "";
    के लिए (संख्या i = 0; i < times; i++) {
        result += str;
    }
    वापसी result;
}

मुख्य() {
    प्रिंट("Int multiply: " + multiply(3, 4));
    प्रिंट("Float multiply: " + multiply(2.5, 1.5));
    प्रिंट("String multiply: " + multiply("हा", 3));
}
```

---

## 5. Arrays & Collections

### Arrays
```dhrlang
मुख्य() {
    // Array declaration
    संख्या[] numbers = new संख्या[5];
    numbers[0] = 10;
    numbers[1] = 20;
    numbers[2] = 30;
    
    // Array initialization
    स्ट्रिंग[] cities = {"दिल्ली", "मुंबई", "कोलकाता", "चेन्नई"};
    
    // Multi-dimensional arrays
    संख्या[][] matrix = {{1, 2}, {3, 4}, {5, 6}};
    
    // Array operations
    प्रिंट("First city: " + cities[0]);
    प्रिंट("Array length: " + cities.length);
    
    // Iterate through array
    के लिए (स्ट्रिंग city : cities) {
        प्रिंट("City: " + city);
    }
    
    // Matrix access
    प्रिंट("Matrix[1][1]: " + matrix[1][1]);
}
```

### Dynamic Arrays (Lists)
```dhrlang
import java.util.ArrayList;

मुख्य() {
    // Dynamic list
    ArrayList<स्ट्रिंग> fruits = new ArrayList<>();
    fruits.add("आम");
    fruits.add("केला");
    fruits.add("सेब");
    
    प्रिंट("Fruits count: " + fruits.size());
    
    के लिए (स्ट्रिंग fruit : fruits) {
        प्रिंट("Fruit: " + fruit);
    }
    
    // Remove item
    fruits.remove("केला");
    प्रिंट("After removing banana: " + fruits.size());
}
```

---

## 6. Object-Oriented Programming

### Classes & Objects
```dhrlang
// Class definition
क्लास Student {
    // Instance variables
    स्ट्रिंग name;
    संख्या age;
    स्ट्रिंग course;
    
    // Constructor
    Student(स्ट्रिंग studentName, संख्या studentAge, स्ट्रिंग studentCourse) {
        this.name = studentName;
        this.age = studentAge;
        this.course = studentCourse;
    }
    
    // Methods
    void displayInfo() {
        प्रिंट("Student: " + name);
        प्रिंट("Age: " + age);
        प्रिंट("Course: " + course);
    }
    
    स्ट्रिंग getGrade(संख्या marks) {
        अगर (marks >= 90) वापसी "A+";
        नहीं तो अगर (marks >= 80) वापसी "A";
        नहीं तो अगर (marks >= 70) वापसी "B";
        नहीं तो वापसी "C";
    }
}

मुख्य() {
    // Create objects
    Student student1 = new Student("राज", 20, "Computer Science");
    Student student2 = new Student("प्रिया", 19, "Mathematics");
    
    student1.displayInfo();
    प्रिंट("Grade: " + student1.getGrade(85));
    प्रिंट("");
    student2.displayInfo();
    प्रिंट("Grade: " + student2.getGrade(92));
}
```

### Inheritance
```dhrlang
// Base class
क्लास Animal {
    स्ट्रिंग name;
    संख्या age;
    
    Animal(स्ट्रिंग name, संख्या age) {
        this.name = name;
        this.age = age;
    }
    
    void makeSound() {
        प्रिंट(name + " makes a sound");
    }
}

// Derived class
क्लास Dog extends Animal {
    स्ट्रिंग breed;
    
    Dog(स्ट्रिंग name, संख्या age, स्ट्रिंग breed) {
        super(name, age); // Call parent constructor
        this.breed = breed;
    }
    
    @Override
    void makeSound() {
        प्रिंट(name + " barks! 🐕");
    }
    
    void wagTail() {
        प्रिंट(name + " wags tail happily!");
    }
}

मुख्य() {
    Dog myDog = new Dog("बडी", 3, "Golden Retriever");
    myDog.makeSound();
    myDog.wagTail();
}
```

---

## 7. Exception Handling

### Try-Catch-Finally
```dhrlang
मुख्य() {
    // Basic exception handling
    कोशिश {
        संख्या result = 10 / 0; // This will cause an error
        प्रिंट("Result: " + result);
    } पकड़ना (ArithmeticException e) {
        प्रिंट("Error: Cannot divide by zero!");
        प्रिंट("Details: " + e.getMessage());
    } अंततः {
        प्रिंट("This always executes");
    }
    
    // Multiple catch blocks
    कोशिश {
        संख्या[] arr = {1, 2, 3};
        प्रिंट(arr[10]); // Index out of bounds
    } पकड़ना (ArrayIndexOutOfBoundsException e) {
        प्रिंट("Array index error: " + e.getMessage());
    } पकड़ना (Exception e) {
        प्रिंट("General error: " + e.getMessage());
    }
}
```

### Custom Exceptions
```dhrlang
// Custom exception class
क्लास InsufficientBalanceException extends Exception {
    InsufficientBalanceException(स्ट्रिंग message) {
        super(message);
    }
}

क्लास BankAccount {
    दशमलव balance;
    
    BankAccount(दशमलव initialBalance) {
        this.balance = initialBalance;
    }
    
    void withdraw(दशमलव amount) throws InsufficientBalanceException {
        अगर (amount > balance) {
            throw new InsufficientBalanceException("Insufficient balance: " + balance);
        }
        balance -= amount;
        प्रिंट("Withdrawn: ₹" + amount + ", Balance: ₹" + balance);
    }
}

मुख्य() {
    BankAccount account = new BankAccount(1000.0);
    
    कोशिश {
        account.withdraw(500.0);  // Success
        account.withdraw(800.0);  // This will fail
    } पकड़ना (InsufficientBalanceException e) {
        प्रिंट("Banking error: " + e.getMessage());
    }
}
```

---

## 8. String Manipulation

### String Operations
```dhrlang
मुख्य() {
    स्ट्रिंग text = "DhrLang Programming";
    
    // Basic operations
    प्रिंट("Length: " + text.length());
    प्रिंट("Uppercase: " + text.toUpperCase());
    प्रिंट("Lowercase: " + text.toLowerCase());
    
    // String methods
    प्रिंट("Character at 0: " + text.charAt(0));
    प्रिंट("Index of 'Lang': " + text.indexOf("Lang"));
    प्रिंट("Contains 'Program': " + text.contains("Program"));
    
    // String manipulation
    स्ट्रिंग replaced = text.replace("Programming", "Development");
    प्रिंट("Replaced: " + replaced);
    
    स्ट्रिंग substring = text.substring(0, 7);
    प्रिंट("Substring: " + substring);
    
    // String concatenation
    स्ट्रिंग firstName = "राम";
    स्ट्रिंग lastName = "शर्मा";
    स्ट्रिंग fullName = firstName + " " + lastName;
    प्रिंट("Full name: " + fullName);
    
    // String formatting
    संख्या age = 25;
    दशमलव salary = 50000.50;
    स्ट्रिंग formatted = String.format("Age: %d, Salary: ₹%.2f", age, salary);
    प्रिंट(formatted);
}
```

### String Arrays & Processing
```dhrlang
मुख्य() {
    // Split string into array
    स्ट्रिंग sentence = "DhrLang is awesome and powerful";
    स्ट्रिंग[] words = sentence.split(" ");
    
    प्रिंट("Word count: " + words.length);
    के लिए (संख्या i = 0; i < words.length; i++) {
        प्रिंट("Word " + (i+1) + ": " + words[i]);
    }
    
    // Join array back to string
    स्ट्रिंग rejoined = String.join("-", words);
    प्रिंट("Rejoined: " + rejoined);
    
    // String builder for efficient concatenation
    StringBuilder sb = new StringBuilder();
    के लिए (संख्या i = 1; i <= 5; i++) {
        sb.append("Number ").append(i).append(" ");
    }
    प्रिंट("Built string: " + sb.toString());
}
```

---

## 9. Generics & Type System

### Generic Classes
```dhrlang
// Generic class definition
क्लास Container<T> {
    निजी T value;
    
    Container(T initialValue) {
        this.value = initialValue;
    }
    
    T getValue() {
        वापसी value;
    }
    
    void setValue(T newValue) {
        this.value = newValue;
    }
    
    void printType() {
        प्रिंट("Type: " + value.getClass().getSimpleName());
    }
}

मुख्य() {
    // Generic with Integer
    Container<संख्या> numberContainer = new Container<>(42);
    प्रिंट("Number: " + numberContainer.getValue());
    numberContainer.printType();
    
    // Generic with String
    Container<स्ट्रिंग> stringContainer = new Container<>("Hello DhrLang");
    प्रिंट("String: " + stringContainer.getValue());
    stringContainer.printType();
    
    // Type safety - this would cause compile error:
    // numberContainer.setValue("Not a number"); // Error!
}
```

### Generic Methods
```dhrlang
// Generic method
<T> void swap(T[] array, संख्या i, संख्या j) {
    T temp = array[i];
    array[i] = array[j];
    array[j] = temp;
}

<T> T findMax(T[] array) {
    T max = array[0];
    के लिए (संख्या i = 1; i < array.length; i++) {
        अगर (((Comparable<T>) array[i]).compareTo(max) > 0) {
            max = array[i];
        }
    }
    वापसी max;
}

मुख्य() {
    // Generic method with integers
    संख्या[] numbers = {3, 1, 4, 1, 5, 9};
    प्रिंट("Before swap: " + Arrays.toString(numbers));
    swap(numbers, 0, numbers.length - 1);
    प्रिंट("After swap: " + Arrays.toString(numbers));
    
    संख्या maxNumber = findMax(numbers);
    प्रिंट("Max number: " + maxNumber);
    
    // Generic method with strings
    स्ट्रिंग[] names = {"राम", "श्याम", "गीता", "सीता"};
    स्ट्रिंग maxName = findMax(names);
    प्रिंट("Max name: " + maxName);
}
```

---

## 10. Access Modifiers

### Access Control
```dhrlang
क्लास BankAccount {
    // Private - only accessible within this class
    निजी दशमलव balance;
    निजी स्ट्रिंग accountPin;
    
    // Protected - accessible in subclasses
    संरक्षित स्ट्रिंग accountType;
    संरक्षित संख्या accountNumber;
    
    // Public - accessible everywhere
    सार्वजनिक स्ट्रिंग holderName;
    सार्वजनिक स्ट्रिंग bankName;
    
    // Constructor
    सार्वजनिक BankAccount(स्ट्रिंग name, दशमलव initialBalance) {
        this.holderName = name;
        this.balance = initialBalance;
        this.accountPin = "1234"; // Private, secure
        this.bankName = "DhrLang Bank"; // Public info
    }
    
    // Public method to access private data
    सार्वजनिक दशमलव getBalance(स्ट्रिंग pin) {
        अगर (this.accountPin.equals(pin)) {
            वापसी balance;
        } नहीं तो {
            प्रिंट("Invalid PIN!");
            वापसी -1;
        }
    }
    
    // Private helper method
    निजी boolean validatePin(स्ट्रिंग pin) {
        वापसी this.accountPin.equals(pin);
    }
    
    सार्वजनिक void deposit(दशमलव amount) {
        अगर (amount > 0) {
            balance += amount;
            प्रिंट("Deposited: ₹" + amount);
        }
    }
}

क्लास SavingsAccount extends BankAccount {
    सार्वजनिक SavingsAccount(स्ट्रिंग name, दशमलव balance) {
        super(name, balance);
        this.accountType = "Savings"; // Can access protected member
    }
    
    सार्वजनिक void printAccountType() {
        प्रिंट("Account Type: " + accountType); // Protected access OK
        // प्रिंट("PIN: " + accountPin); // Error! Private not accessible
    }
}

मुख्य() {
    BankAccount account = new BankAccount("राहुल शर्मा", 5000.0);
    
    // Public access
    प्रिंट("Holder: " + account.holderName);
    प्रिंट("Bank: " + account.bankName);
    
    // Private access through public method
    दशमलव balance = account.getBalance("1234");
    प्रिंट("Balance: ₹" + balance);
    
    // This would cause error - private access:
    // प्रिंट(account.balance); // Error!
    
    account.deposit(1000);
}
```

---

## 11. Static Members & Initialization

### Static Variables & Methods
```dhrlang
क्लास Calculator {
    // Static variable - shared across all instances
    स्टैटिक संख्या operationCount = 0;
    स्टैटिक final दशमलव PI = 3.14159;
    
    // Instance variable
    स्ट्रिंग calculatorModel;
    
    // Constructor
    Calculator(स्ट्रिंग model) {
        this.calculatorModel = model;
    }
    
    // Static method - can be called without creating instance
    स्टैटिक संख्या add(संख्या a, संख्या b) {
        operationCount++;
        वापसी a + b;
    }
    
    स्टैटिक संख्या multiply(संख्या a, संख्या b) {
        operationCount++;
        वापसी a * b;
    }
    
    स्टैटिक दशमलव circleArea(दशमलव radius) {
        operationCount++;
        वापसी PI * radius * radius;
    }
    
    // Static method to get operation count
    स्टैटिक संख्या getOperationCount() {
        वापसी operationCount;
    }
    
    // Instance method
    void printModel() {
        प्रिंट("Calculator Model: " + calculatorModel);
    }
}

मुख्य() {
    // Call static methods without creating instance
    संख्या sum = Calculator.add(10, 20);
    संख्या product = Calculator.multiply(5, 6);
    दशमलव area = Calculator.circleArea(7.0);
    
    प्रिंट("Sum: " + sum);
    प्रिंट("Product: " + product);
    प्रिंट("Circle Area: " + area);
    प्रिंट("Operations performed: " + Calculator.getOperationCount());
    
    // Create instances
    Calculator calc1 = new Calculator("Scientific");
    Calculator calc2 = new Calculator("Basic");
    
    calc1.printModel();
    calc2.printModel();
    
    // Static variable is shared
    Calculator.add(1, 1); // Increment operation count
    प्रिंट("Total operations: " + Calculator.getOperationCount());
}
```

### Static Initialization
```dhrlang
क्लास DatabaseConfig {
    स्टैटिक स्ट्रिंग databaseUrl;
    स্টাটিক संख्या maxConnections;
    স्टैटिक boolean isConfigured = false;
    
    // Static initialization block
    स्टैटिक {
        प्रिंट("Initializing database configuration...");
        databaseUrl = "jdbc:mysql://localhost:3306/dhrlangdb";
        maxConnections = 100;
        isConfigured = true;
        प्रिंट("Database configuration complete!");
    }
    
    स्टैटिक void printConfig() {
        प्रिंट("Database URL: " + databaseUrl);
        प्रिंट("Max Connections: " + maxConnections);
        प्रिंट("Configured: " + isConfigured);
    }
}

मुख्य() {
    // Static block runs when class is first referenced
    प्रिंट("About to access DatabaseConfig...");
    DatabaseConfig.printConfig();
}
```

---

## 12. Best Practices

### Code Organization
```dhrlang
// Good: Clear class structure with proper access modifiers
क्लास StudentManagement {
    // Private data
    निजी ArrayList<Student> students;
    निजी संख्या totalStudents;
    
    // Constructor
    सार्वजनिक StudentManagement() {
        this.students = new ArrayList<>();
        this.totalStudents = 0;
    }
    
    // Public interface methods
    सार्वजनिक void addStudent(Student student) {
        students.add(student);
        totalStudents++;
        logAction("Added student: " + student.getName());
    }
    
    सार्वजनिक Student findStudent(स्ट्रिंग name) {
        के लिए (Student student : students) {
            अगर (student.getName().equals(name)) {
                वापसी student;
            }
        }
        वापसी null;
    }
    
    सार्वजनिक संख्या getStudentCount() {
        वापसी totalStudents;
    }
    
    // Private helper method
    निजी void logAction(स्ट्रिंग action) {
        प्रिंट("[LOG] " + action);
    }
    
    // Good: Input validation
    सार्वजनिक void updateStudentGrade(स्ट्रिंग name, संख्या grade) {
        अगर (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Student name cannot be empty");
        }
        
        अगर (grade < 0 || grade > 100) {
            throw new IllegalArgumentException("Grade must be between 0 and 100");
        }
        
        Student student = findStudent(name);
        अगर (student != null) {
            student.setGrade(grade);
            logAction("Updated grade for " + name + " to " + grade);
        } नहीं तो {
            throw new IllegalArgumentException("Student not found: " + name);
        }
    }
}
```

### Error Handling Best Practices
```dhrlang
क्लास FileProcessor {
    सार्वजनिक स्ट्रिंग readFile(स्ट्रिंग filename) {
        अगर (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Filename cannot be null or empty");
        }
        
        कोशिश {
            // Simulate file reading
            अगर (!filename.endsWith(".txt")) {
                throw new IllegalArgumentException("Only .txt files supported");
            }
            
            // Simulate file content
            वापसी "File content from " + filename;
            
        } पकड़ना (Exception e) {
            // Log the error
            प्रिंट("Error reading file " + filename + ": " + e.getMessage());
            
            // Re-throw with more context
            throw new RuntimeException("Failed to read file: " + filename, e);
        }
    }
    
    सार्वजनिक void processFiles(स्ट्रिंग[] filenames) {
        संख्या successCount = 0;
        संख्या errorCount = 0;
        
        के लिए (स्ट्रिंग filename : filenames) {
            कोशिश {
                स्ट्रिंग content = readFile(filename);
                प्रिंट("Processed: " + filename);
                successCount++;
            } पकड़ना (Exception e) {
                प्रिंट("Failed to process: " + filename + " - " + e.getMessage());
                errorCount++;
            }
        }
        
        प्रिंट("Processing complete. Success: " + successCount + ", Errors: " + errorCount);
    }
}

मुख्य() {
    FileProcessor processor = new FileProcessor();
    स्ट्रिंग[] files = {"data.txt", "config.xml", "readme.txt"};
    
    processor.processFiles(files);
}
```

### Performance Tips
```dhrlang
मुख्य() {
    // Good: Use StringBuilder for multiple concatenations
    StringBuilder sb = new StringBuilder();
    के लिए (संख्या i = 0; i < 1000; i++) {
        sb.append("Item ").append(i).append(" ");
    }
    स्ट्रिंग result = sb.toString();
    
    // Good: Cache frequently used values
    संख्या arrayLength = someArray.length; // Cache length
    के लिए (संख्या i = 0; i < arrayLength; i++) {
        // Use cached length instead of someArray.length
    }
    
    // Good: Use appropriate data structures
    HashMap<स्ट्रिंग, संख्या> studentGrades = new HashMap<>(); // O(1) lookup
    studentGrades.put("राहुल", 85);
    studentGrades.put("प्रिया", 92);
    
    // Good: Early return to avoid deep nesting
    अगर (someCondition) {
        वापसी;
    }
    
    // Continue with main logic...
}
```

---

## 🎯 Practice Exercises

### Exercise 1: Student Grade Calculator
Create a program that:
1. Manages multiple students
2. Calculates average grades
3. Determines pass/fail status
4. Uses proper error handling

### Exercise 2: Bank Account System
Implement:
1. Different account types (Savings, Current)
2. Transaction history
3. Interest calculation
4. Access control for sensitive operations

### Exercise 3: Library Management
Build:
1. Book inventory system
2. Member management
3. Borrowing and returning books
4. Search functionality

---

## 📖 Additional Resources

- **Reference**: [Language Specification](../SPEC.md)
- **Examples**: [Input Programs](../input/)
- **API Docs**: [Generated Documentation](../docs/api/)
- **Community**: [GitHub Discussions](https://github.com/dhruv-15-03/DhrLang/discussions)

---

**Happy Coding with DhrLang! 🚀**

*Next: Try building your own project using these concepts!*