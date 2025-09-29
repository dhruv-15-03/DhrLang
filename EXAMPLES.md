# DhrLang Examples Gallery

Welcome to the DhrLang Examples Gallery! This collection showcases real-world applications and demonstrates the power of programming in Hindi.

## 📁 Example Categories

### 🎯 Beginner Examples
- [Calculator](#calculator) - Basic arithmetic operations
- [Student Records](#student-records) - Simple data management
- [Number Games](#number-games) - Interactive console games

### 🏗️ Intermediate Examples
- [Banking System](#banking-system) - Account management with OOP
- [Library Management](#library-management) - Book inventory system
- [Quiz Application](#quiz-application) - Interactive quiz with scoring

### 🚀 Advanced Examples
- [Web Server](#web-server) - HTTP server implementation
- [Data Structures](#data-structures) - Custom collections and algorithms
- [File Management](#file-management) - File I/O operations

---

## Calculator

A fully-featured calculator with scientific operations.

**File: `examples/calculator.dhr`**

```dhrlang
import java.util.Scanner;

क्लास Calculator {
    निजी Scanner scanner;
    
    सार्वजनिक Calculator() {
        this.scanner = new Scanner(System.in);
    }
    
    सार्वजनिक void start() {
        प्रिंट("🧮 DhrLang Calculator - कैलकुलेटर");
        प्रिंट("Available operations: +, -, *, /, %, ^ (power), sqrt (square root)");
        प्रिंट("Type 'exit' to quit / बाहर निकलने के लिए 'exit' टाइप करें");
        
        जबकि (true) {
            प्रिंट("\nEnter expression (e.g., 5 + 3): ");
            स्ट्रिंग input = scanner.nextLine().trim();
            
            अगर (input.equalsIgnoreCase("exit")) {
                प्रिंट("धन्यवाद! Calculator बंद हो रहा है...");
                break;
            }
            
            कोशिश {
                दशमलव result = evaluateExpression(input);
                ## Calculator (Rewritten for Current Syntax)

                The previous example used unsupported Hindi keywords. Below is a simplified expression evaluator using only implemented tokens. (Scanning/parsing logic written in pseudo – adapt as needed.)

                ```dhrlang
                class Calculator {
                    sab expr;
                    kaam init(sab e) { this.expr = e; }

                    duo eval() { // VERY naive; split by '+' only for demo
                        sab e = this.expr;
                        duo total = 0.0;
                        num start = 0;
                        // Pseudo loop over characters
                        // (Real implementation would iterate and parse numbers)
                        return 0.0; // placeholder
                    }
                }

                class Main {
                    static kaam main() {
                        Calculator c = new Calculator("1+2+3");
                        duo r = c.eval();
                        printLine("Result: " + r);
                    }
                }
                ```

                > For full arithmetic, implement a tokenizer + recursive descent or shunting-yard; out of scope here.

                ---

                ## Minimal OOP Example

                ```dhrlang
                class User {
                    private sab name;
                    private num id;
                    kaam init(sab name, num id) { this.name = name; this.id = id; }
                    sab getName() { return this.name; }
                }

                class Demo {
                    static kaam main() {
                        User u = new User("Alice", 1);
                        printLine("User: " + u.getName());
                    }
                }
                ```

                ---

                ## Array Processing

                ```dhrlang
                class ArraysDemo {
                    static kaam main() {
                        num[] data = [3,5,7,9];
                        num sum = 0;
                        for (num i = 0; i < arrayLength(data); i++) {
                            sum = sum + data[i];
                        }
                        printLine("Sum: " + sum);
                    }
                }
                ```

                ---

                Old bilingual / Java-interoperability heavy examples were removed to prevent confusion. Refer to `input/` programs and `TUTORIALS.md` for authoritative, runnable patterns.
    
    सार्वजनिक void addGrade(संख्या grade) {
        अगर (grade >= 0 && grade <= 100) {
            grades.add(grade);
        } नहीं तो {
            throw new IllegalArgumentException("Grade must be between 0 and 100");
        }
    }
    
    सार्वजनिक दशमलव getAverage() {
        अगर (grades.isEmpty()) वापसी 0.0;
        
        संख्या sum = 0;
        के लिए (संख्या grade : grades) {
            sum += grade;
        }
        वापसी (दशमलव) sum / grades.size();
    }
    
    सार्वजनिक स्ट्रिंग getGradeCategory() {
        दशमलव avg = getAverage();
        अगर (avg >= 90) वापसी "A+ (उत्कृष्ट)";
        नहीं तो अगर (avg >= 80) वापसी "A (बहुत अच्छा)";
        नहीं तो अगर (avg >= 70) वापसी "B (अच्छा)";
        नहीं तो अगर (avg >= 60) वापसी "C (संतोषजनक)";
        नहीं तो अगर (avg >= 50) वापसी "D (कम)";
        नहीं तो वापसी "F (फेल)";
    }
    
    सार्वजनिक void displayInfo() {
        प्रिंट("=== Student Information ===");
        प्रिंट("Name / नाम: " + name);
        प्रिंट("Roll Number / रोल नंबर: " + rollNumber);
        प्रिंट("Course / कोर्स: " + course);
        प्रिंट("Grades / अंक: " + grades);
        प्रिंट("Average / औसत: " + String.format("%.2f", getAverage()));
        प्रिंट("Category / श्रेणी: " + getGradeCategory());
        प्रिंट("=========================");
    }
    
    // Getters
    सार्वजनिक स्ट्रिंग getName() { वापसी name; }
    सार्वजनिक संख्या getRollNumber() { वापसी rollNumber; }
    सार्वजनिक स्ट्रिंग getCourse() { वापसी course; }
    सार्वजनिक ArrayList<संख्या> getGrades() { वापसी grades; }
}

क्लास StudentManager {
    निजी ArrayList<Student> students;
    निजी Scanner scanner;
    
    सार्वजनिक StudentManager() {
        this.students = new ArrayList<>();
        this.scanner = new Scanner(System.in);
        
        // Add sample data
        addSampleData();
    }
    
    निजी void addSampleData() {
        Student s1 = new Student("राहुल शर्मा", 101, "Computer Science");
        s1.addGrade(85);
        s1.addGrade(92);
        s1.addGrade(78);
        students.add(s1);
        
        Student s2 = new Student("प्रिया पटेल", 102, "Mathematics");
        s2.addGrade(95);
        s2.addGrade(88);
        s2.addGrade(91);
        students.add(s2);
        
        Student s3 = new Student("अमित कुमार", 103, "Physics");
        s3.addGrade(72);
        s3.addGrade(68);
        s3.addGrade(75);
        students.add(s3);
    }
    
    सार्वजनिक void showMenu() {
        जबकि (true) {
            प्रिंट("\n📚 Student Management System - छात्र प्रबंधन प्रणाली");
            प्रिंट("1. Add Student / छात्र जोड़ें");
            प्रिंट("2. View All Students / सभी छात्र देखें");
            प्रिंट("3. Search Student / छात्र खोजें");
            प्रिंट("4. Add Grade / अंक जोड़ें");
            प्रिंट("5. Generate Report / रिपोर्ट बनाएं");
            प्रिंट("6. Exit / बाहर निकलें");
            प्रिंट("Choose option / विकल्प चुनें: ");
            
            संख्या choice = scanner.nextInt();
            scanner.nextLine(); // Consume newline
            
            switch (choice) {
                case 1: addStudent(); break;
                case 2: viewAllStudents(); break;
                case 3: searchStudent(); break;
                case 4: addGradeToStudent(); break;
                case 5: generateReport(); break;
                case 6: 
                    प्रिंट("धन्यवाद! System बंद हो रहा है...");
                    वापसी;
                default: 
                    प्रिंट("Invalid option / अमान्य विकल्प");
            }
        }
    }
    
    निजी void addStudent() {
        प्रिंट("Enter student name / छात्र का नाम: ");
        स्ट्रिंग name = scanner.nextLine();
        
        प्रिंट("Enter roll number / रोल नंबर: ");
        संख्या rollNumber = scanner.nextInt();
        scanner.nextLine();
        
        प्रिंट("Enter course / कोर्स: ");
        स्ट्रिंग course = scanner.nextLine();
        
        Student student = new Student(name, rollNumber, course);
        students.add(student);
        प्रिंट("Student added successfully! / छात्र सफलतापूर्वक जोड़ा गया!");
    }
    
    निजी void viewAllStudents() {
        अगर (students.isEmpty()) {
            प्रिंट("No students found / कोई छात्र नहीं मिला");
            वापसी;
        }
        
        के लिए (Student student : students) {
            student.displayInfo();
            प्रिंट("");
        }
    }
    
    निजी void searchStudent() {
        प्रिंट("Enter roll number to search / खोजने के लिए रोल नंबर: ");
        संख्या rollNumber = scanner.nextInt();
        
        Student found = findStudentByRoll(rollNumber);
        अगर (found != null) {
            found.displayInfo();
        } नहीं तो {
            प्रिंट("Student not found / छात्र नहीं मिला");
        }
    }
    
    निजी void addGradeToStudent() {
        प्रिंट("Enter roll number / रोल नंबर: ");
        संख्या rollNumber = scanner.nextInt();
        
        Student student = findStudentByRoll(rollNumber);
        अगर (student == null) {
            प्रिंट("Student not found / छात्र नहीं मिला");
            वापसी;
        }
        
        प्रिंट("Enter grade (0-100) / अंक (0-100): ");
        संख्या grade = scanner.nextInt();
        
        कोशिश {
            student.addGrade(grade);
            प्रिंट("Grade added successfully! / अंक सफलतापूर्वक जोड़ा गया!");
        } पकड़ना (Exception e) {
            प्रिंट("Error: " + e.getMessage());
        }
    }
    
    निजी void generateReport() {
        अगर (students.isEmpty()) {
            प्रिंट("No students to generate report / रिपोर्ट के लिए कोई छात्र नहीं");
            वापसी;
        }
        
        प्रिंट("\n📊 Class Report - कक्षा रिपोर्ट");
        प्रिंट("=================================");
        
        दशमलव totalAverage = 0.0;
        संख्या excellentStudents = 0;
        
        के लिए (Student student : students) {
            दशमलव avg = student.getAverage();
            totalAverage += avg;
            
            अगर (avg >= 90) excellentStudents++;
            
            प्रिंट(student.getName() + " (" + student.getRollNumber() + 
                    ") - Average: " + String.format("%.2f", avg) + 
                    " - " + student.getGradeCategory());
        }
        
        प्रिंट("\nClass Statistics / कक्षा आंकड़े:");
        प्रिंट("Total Students / कुल छात्र: " + students.size());
        प्रिंट("Class Average / कक्षा औसत: " + String.format("%.2f", totalAverage / students.size()));
        प्रिंट("Excellent Students (A+) / उत्कृष्ट छात्र: " + excellentStudents);
        प्रिंट("=================================");
    }
    
    निजी Student findStudentByRoll(संख्या rollNumber) {
        के लिए (Student student : students) {
            अगर (student.getRollNumber() == rollNumber) {
                वापसी student;
            }
        }
        वापसी null;
    }
}

मुख्य() {
    StudentManager manager = new StudentManager();
    manager.showMenu();
}
```

**Features:**
- Complete CRUD operations for students
- Grade management and averaging
- Grade categorization in Hindi
- Search functionality
- Comprehensive reporting
- Interactive menu system

---

## Banking System

Advanced banking system with multiple account types and transaction history.

**File: `examples/banking_system.dhr`**

```dhrlang
import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

क्लास Transaction {
    निजी स्ट्रिंग type;
    निजी दशमलव amount;
    निजी दशमलव balanceAfter;
    निजी LocalDateTime timestamp;
    निजी स्ट्रिंग description;
    
    सार्वजनिक Transaction(स्ट्रिंग type, दशमलव amount, दशमलव balanceAfter, स्ट्रिंग description) {
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.description = description;
        this.timestamp = LocalDateTime.now();
    }
    
    सार्वजनिक void display() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        प्रिंट(timestamp.format(formatter) + " | " + type + " | ₹" + 
                String.format("%.2f", amount) + " | Balance: ₹" + 
                String.format("%.2f", balanceAfter) + " | " + description);
    }
    
    // Getters
    सार्वजनिक स्ट्रिंग getType() { वापसी type; }
    सार्वजनिक दशमलव getAmount() { वापसी amount; }
    सार्वजनिक LocalDateTime getTimestamp() { वापसी timestamp; }
}

abstract क्लास BankAccount {
    संरक्षित स्ट्रिंग accountNumber;
    संरक्षित स्ट्रिंग holderName;
    संरक्षित दशमलव balance;
    संरक्षित स्ट्रिंग pin;
    संरक्षित ArrayList<Transaction> transactions;
    संरक्षित boolean isActive;
    
    सार्वजनिक BankAccount(स्ट्रिंग accountNumber, स्ट्रिंग holderName, स्ट्रिंग pin) {
        this.accountNumber = accountNumber;
        this.holderName = holderName;
        this.pin = pin;
        this.balance = 0.0;
        this.transactions = new ArrayList<>();
        this.isActive = true;
    }
    
    abstract स्ट्रिंग getAccountType();
    abstract दशमलव getMinimumBalance();
    abstract दशमलव getWithdrawalLimit();
    
    सार्वजनिक boolean validatePin(स्ट्रिंग inputPin) {
        वापसी this.pin.equals(inputPin);
    }
    
    सार्वजनिक void deposit(दशमלव amount, स्ट्रिंग description) {
        अगर (amount <= 0) {
            throw new IllegalArgumentException("राशि धनात्मक होनी चाहिए");
        }
        
        balance += amount;
        Transaction transaction = new Transaction("DEPOSIT", amount, balance, description);
        transactions.add(transaction);
    }
    
    सार्वजनिक void withdraw(दशमलव amount, स्ट्रिंग description) throws Exception {
        अगर (amount <= 0) {
            throw new IllegalArgumentException("राशि धनात्मक होनी चाहिए");
        }
        
        अगर (amount > getWithdrawalLimit()) {
            throw new Exception("निकासी सीमा पार हो गई: ₹" + getWithdrawalLimit());
        }
        
        अगर (balance - amount < getMinimumBalance()) {
            throw new Exception("न्यूनतम बैलेंस बनाए रखना आवश्यक: ₹" + getMinimumBalance());
        }
        
        balance -= amount;
        Transaction transaction = new Transaction("WITHDRAWAL", amount, balance, description);
        transactions.add(transaction);
    }
    
    सार्वजनिक void transfer(BankAccount toAccount, दशमलव amount, स्ट्रिंग description) throws Exception {
        withdraw(amount, "Transfer to " + toAccount.getAccountNumber() + " - " + description);
        toAccount.deposit(amount, "Transfer from " + this.accountNumber + " - " + description);
    }
    
    सार्वजनिक void displayBalance() {
        प्रिंट("Account: " + accountNumber + " (" + getAccountType() + ")");
        प्रिंट("Holder: " + holderName);
        प्रिंट("Balance / बैलेंस: ₹" + String.format("%.2f", balance));
        प्रिंट("Status: " + (isActive ? "Active / सक्रिय" : "Inactive / निष्क्रिय"));
    }
    
    सार्वजनिक void displayTransactionHistory(संख्या limit) {
        प्रिंट("\n📊 Transaction History - लेनदेन इतिहास");
        प्रिंट("Account: " + accountNumber + " - " + holderName);
        प्रिंट("=========================================");
        
        अगर (transactions.isEmpty()) {
            प्रिंट("No transactions found / कोई लेनदेन नहीं मिला");
            वापसी;
        }
        
        संख्या count = Math.min(limit, transactions.size());
        के लिए (संख्या i = transactions.size() - count; i < transactions.size(); i++) {
            transactions.get(i).display();
        }
        प्रिंट("=========================================");
    }
    
    // Getters
    सार्वजनिक स्ट्रिंग getAccountNumber() { वापसी accountNumber; }
    सार्वजनिक स्ट्रिंग getHolderName() { वापसी holderName; }
    सार्वजनिक दशमलव getBalance() { वापसी balance; }
    सार्वजनिक boolean isActive() { वापसी isActive; }
}

क्लास SavingsAccount extends BankAccount {
    निजी दशमलव interestRate = 4.5; // 4.5% per annum
    
    सार्वजनिक SavingsAccount(स्ट्रिंग accountNumber, स्ट्रिंग holderName, स्ट्रिंग pin) {
        super(accountNumber, holderName, pin);
    }
    
    @Override
    स्ट्रिंग getAccountType() {
        वापसी "Savings / बचत खाता";
    }
    
    @Override
    दशमलव getMinimumBalance() {
        वापसी 1000.0;
    }
    
    @Override
    दशमलव getWithdrawalLimit() {
        वापसी 50000.0;
    }
    
    सार्वजनिक void calculateInterest() {
        दशमलव interest = balance * interestRate / 100 / 12; // Monthly interest
        deposit(interest, "Monthly Interest @ " + interestRate + "%");
        प्रिंट("Interest credited: ₹" + String.format("%.2f", interest));
    }
}

क्लास CurrentAccount extends BankAccount {
    निजी दशमलव overdraftLimit = 100000.0;
    
    सार्वजनिक CurrentAccount(स्ट्रिंग accountNumber, स्ट्रिंग holderName, स्ट्रिंग pin) {
        super(accountNumber, holderName, pin);
    }
    
    @Override
    स्ट्रिंग getAccountType() {
        वापसी "Current / चालू खाता";
    }
    
    @Override
    दशमलव getMinimumBalance() {
        वापसी -overdraftLimit; // Can go negative up to overdraft limit
    }
    
    @Override
    दशमलव getWithdrawalLimit() {
        वापसी 200000.0;
    }
    
    सार्वजनिक दशमलव getOverdraftLimit() {
        वापसी overdraftLimit;
    }
}

क्लास Bank {
    निजी HashMap<स्ट्रिंग, BankAccount> accounts;
    निजी Scanner scanner;
    निजी संख्या nextAccountNumber = 10001;
    
    सार्वजनिक Bank() {
        this.accounts = new HashMap<>();
        this.scanner = new Scanner(System.in);
        
        // Add sample accounts
        addSampleAccounts();
    }
    
    निजी void addSampleAccounts() {
        SavingsAccount sa1 = new SavingsAccount("SA10001", "राहुल शर्मा", "1234");
        sa1.deposit(15000.0, "Initial deposit");
        accounts.put("SA10001", sa1);
        
        CurrentAccount ca1 = new CurrentAccount("CA10001", "प्रिया एंटरप्राइजेज", "5678");
        ca1.deposit(50000.0, "Initial deposit");
        accounts.put("CA10001", ca1);
        
        nextAccountNumber = 10002;
    }
    
    सार्वजनिक void showMainMenu() {
        जबकि (true) {
            प्रिंट("\n🏦 DhrLang Bank - धृलांग बैंक");
            प्रिंट("===============================");
            प्रिंट("1. Create Account / खाता बनाएं");
            प्रिंट("2. Login to Account / खाते में लॉगिन करें");
            प्रिंट("3. Exit / बाहर निकलें");
            प्रिंट("Choose option / विकल्प चुनें: ");
            
            संख्या choice = scanner.nextInt();
            scanner.nextLine();
            
            switch (choice) {
                case 1: createAccount(); break;
                case 2: loginToAccount(); break;
                case 3: 
                    प्रिंट("धन्यवाद! DhrLang Bank का उपयोग करने के लिए");
                    वापसी;
                default: 
                    प्रिंट("Invalid option / अमान्य विकल्प");
            }
        }
    }
    
    निजी void createAccount() {
        प्रिंट("Enter account holder name / खाता धारक का नाम: ");
        स्ट्रिंग name = scanner.nextLine();
        
        प्रिंट("Set 4-digit PIN / 4 अंकों का PIN सेट करें: ");
        स्ट्रिंग pin = scanner.nextLine();
        
        प्रिंट("Account type / खाता प्रकार:");
        प्रिंट("1. Savings / बचत खाता");
        प्रिंट("2. Current / चालू खाता");
        संख्या type = scanner.nextInt();
        scanner.nextLine();
        
        स्ट्रिंग accountNumber;
        BankAccount account;
        
        अगर (type == 1) {
            accountNumber = "SA" + nextAccountNumber;
            account = new SavingsAccount(accountNumber, name, pin);
        } नहीं तो अगर (type == 2) {
            accountNumber = "CA" + nextAccountNumber;
            account = new CurrentAccount(accountNumber, name, pin);
        } नहीं तो {
            प्रिंट("Invalid account type / अमान्य खाता प्रकार");
            वापसी;
        }
        
        accounts.put(accountNumber, account);
        nextAccountNumber++;
        
        प्रिंट("\n✅ Account created successfully! / खाता सफलतापूर्वक बनाया गया!");
        प्रिंट("Account Number / खाता संख्या: " + accountNumber);
        प्रिंट("Please note down your account number / कृपया अपना खाता नंबर लिख लें");
    }
    
    निजी void loginToAccount() {
        प्रिंट("Enter account number / खाता संख्या: ");
        स्ट्रिंग accountNumber = scanner.nextLine();
        
        BankAccount account = accounts.get(accountNumber);
        अगर (account == null) {
            प्रिंट("Account not found / खाता नहीं मिला");
            वापसी;
        }
        
        प्रिंट("Enter PIN / PIN दर्ज करें: ");
        स्ट्रिंग pin = scanner.nextLine();
        
        अगर (!account.validatePin(pin)) {
            प्रिंट("Invalid PIN / गलत PIN");
            वापसी;
        }
        
        showAccountMenu(account);
    }
    
    निजी void showAccountMenu(BankAccount account) {
        जबकि (true) {
            प्रिंट("\n💳 Account Menu - " + account.getHolderName());
            प्रिंट("=====================================");
            प्रिंट("1. Check Balance / बैलेंस चेक करें");
            प्रिंट("2. Deposit / जमा करें");
            प्रिंट("3. Withdraw / निकालें");
            प्रिंट("4. Transfer / ट्रांसफर करें");
            प्रिंट("5. Transaction History / लेनदेन इतिहास");
            अगर (account instanceof SavingsAccount) {
                प्रिंट("6. Calculate Interest / ब्याज गणना");
            }
            प्रिंट("0. Logout / लॉगआउट");
            प्रिंट("Choose option / विकल्प चुनें: ");
            
            संख्या choice = scanner.nextInt();
            scanner.nextLine();
            
            switch (choice) {
                case 1: account.displayBalance(); break;
                case 2: depositMoney(account); break;
                case 3: withdrawMoney(account); break;
                case 4: transferMoney(account); break;
                case 5: showTransactionHistory(account); break;
                case 6: 
                    अगर (account instanceof SavingsAccount) {
                        ((SavingsAccount) account).calculateInterest();
                    }
                    break;
                case 0: 
                    प्रिंट("Logged out successfully / सफलतापूर्वक लॉगआउट");
                    वापसी;
                default: 
                    प्रिंट("Invalid option / अमान्य विकल्प");
            }
        }
    }
    
    निजी void depositMoney(BankAccount account) {
        प्रिंट("Enter amount to deposit / जमा करने की राशि: ₹");
        दशमलव amount = scanner.nextDouble();
        scanner.nextLine();
        
        प्रिंट("Enter description / विवरण: ");
        स्ट्रिंग description = scanner.nextLine();
        
        कोशिश {
            account.deposit(amount, description);
            प्रिंट("✅ Deposit successful! / जमा सफल!");
            account.displayBalance();
        } पकड़ना (Exception e) {
            प्रिंट("❌ Error: " + e.getMessage());
        }
    }
    
    निजी void withdrawMoney(BankAccount account) {
        प्रिंट("Enter amount to withdraw / निकालने की राशि: ₹");
        दशमलव amount = scanner.nextDouble();
        scanner.nextLine();
        
        प्रिंट("Enter description / विवरण: ");
        स्ट्रिंग description = scanner.nextLine();
        
        कोशिश {
            account.withdraw(amount, description);
            प्रिंट("✅ Withdrawal successful! / निकासी सफल!");
            account.displayBalance();
        } पकड़ना (Exception e) {
            प्रिंट("❌ Error: " + e.getMessage());
        }
    }
    
    निजी void transferMoney(BankAccount fromAccount) {
        प्रिंट("Enter target account number / लक्ष्य खाता संख्या: ");
        स्ट्रिंग toAccountNumber = scanner.nextLine();
        
        BankAccount toAccount = accounts.get(toAccountNumber);
        अगर (toAccount == null) {
            प्रिंट("Target account not found / लक्ष्य खाता नहीं मिला");
            वापसी;
        }
        
        प्रिंट("Transfer to: " + toAccount.getHolderName());
        प्रिंट("Enter amount to transfer / ट्रांसफर करने की राशि: ₹");
        दशमलव amount = scanner.nextDouble();
        scanner.nextLine();
        
        प्रिंट("Enter description / विवरण: ");
        स्ट्रिंग description = scanner.nextLine();
        
        कोशिश {
            fromAccount.transfer(toAccount, amount, description);
            प्रिंट("✅ Transfer successful! / ट्रांसफर सफल!");
            fromAccount.displayBalance();
        } पकड़ना (Exception e) {
            प्रिंट("❌ Error: " + e.getMessage());
        }
    }
    
    निजी void showTransactionHistory(BankAccount account) {
        प्रिंट("Enter number of recent transactions to show / दिखाने वाले हालिया लेनदेन की संख्या: ");
        संख्या limit = scanner.nextInt();
        account.displayTransactionHistory(limit);
    }
}

मुख्य() {
    Bank bank = new Bank();
    bank.showMainMenu();
}
```

**Features:**
- Multiple account types (Savings, Current)
- Complete banking operations (deposit, withdraw, transfer)
- Transaction history with timestamps
- Interest calculation for savings accounts
- Overdraft facility for current accounts
- PIN-based security
- Hindi/English bilingual interface
- Comprehensive error handling

---

## Running the Examples

### Quick Start
1. **Save** any example to a `.dhr` file
2. **Compile & Run**:
   ```bash
   java -jar DhrLang.jar your_example.dhr
   ```

### Interactive Examples
Most examples include interactive menus. Follow the on-screen prompts in Hindi/English.

### Customization
- Modify the examples to suit your needs
- Add new features using DhrLang syntax
- Combine examples to create larger applications

---

## 🎯 Learning Path

### For Beginners
1. Start with **Calculator** - Learn basic syntax
2. Try **Student Records** - Understand OOP concepts
3. Build simple modifications to existing examples

### For Intermediate
1. Study **Banking System** - Advanced OOP patterns
2. Create your own classes and inheritance hierarchies
3. Add new features to existing examples

### For Advanced
1. Combine multiple examples
2. Add database connectivity
3. Create web interfaces
4. Build GUI applications

---

## 💡 Tips for Success

1. **Read the code** - Each example is well-commented
2. **Run first** - See how it works before modifying
3. **Experiment** - Change values, add features
4. **Mix languages** - Use Hindi keywords where comfortable
5. **Ask questions** - Join our community discussions

---

## 📚 Additional Resources

- **Tutorial**: [Complete Tutorial Guide](TUTORIALS.md)
- **Reference**: [Language Specification](SPEC.md)
- **API**: [Built-in Functions](docs/api/)
- **Community**: [GitHub Discussions](https://github.com/dhruv-15-03/DhrLang/discussions)

---

**Happy Coding with DhrLang! 🚀**

*Start with simple examples and gradually move to complex applications. The best way to learn is by doing!*