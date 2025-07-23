package dhrlang.parser;

import dhrlang.ast.*;
import dhrlang.error.ErrorReporter;
import dhrlang.lexer.Token;
import dhrlang.lexer.TokenType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class Parser {

    private final List<Token> tokens;
    private int current = 0;
    private ErrorReporter errorReporter;

    public Parser(List<Token> tokens) {
        this(tokens, null);
    }
    
    public Parser(List<Token> tokens, ErrorReporter errorReporter) {
        this.tokens = tokens;
        this.errorReporter = errorReporter;
    }

    public Program parse() {
        List<ClassDecl> classes = new ArrayList<>();
        List<InterfaceDecl> interfaces = new ArrayList<>();

        try {
            while (!isAtEnd()) {
                if (check(TokenType.INTERFACE)) {
                    interfaces.add(parseInterfaceDecl());
                } else {
                    classes.add(parseClassDecl());
                }
            }
            return new Program(classes, interfaces);
        } catch (ParseException e) {
            throw e;
        }
    }

    private ClassDecl parseClassDecl() {
        Set<Modifier> classModifiers = parseModifiers();
        
        consume(TokenType.CLASS, "Expected 'class' keyword to start a class declaration.");
        Token name = consume(TokenType.IDENTIFIER, "Expected class name after 'class'.");
        
        List<TypeParameter> typeParameters = new ArrayList<>();
        if (check(TokenType.LESS)) {
            typeParameters = parseTypeParameters();
        }
        
        VariableExpr superclass = null;
        if (match(TokenType.EXTENDS)) {
            Token superclassName = consume(TokenType.IDENTIFIER, "Expected superclass name.");
            String fullSuperclassName = superclassName.getLexeme();
            
            if (check(TokenType.LESS)) {
                StringBuilder genericSuperclassName = new StringBuilder(fullSuperclassName);
                genericSuperclassName.append("<");
                
                advance();
                int depth = 1;
                while (depth > 0 && !isAtEnd()) {
                    Token token = advance();
                    genericSuperclassName.append(token.getLexeme());
                    
                    if (token.getType() == TokenType.LESS) {
                        depth++;
                    } else if (token.getType() == TokenType.GREATER) {
                        depth--;
                    }
                    
                    if (token.getType() == TokenType.COMMA && depth == 1) {
                        genericSuperclassName.append(" ");
                    }
                }
                fullSuperclassName = genericSuperclassName.toString();
            }
            
            Token superclassToken = new Token(superclassName.getType(), fullSuperclassName, superclassName.getLine());
            superclass = new VariableExpr(superclassToken);
        }
        
        List<VariableExpr> interfaces = new ArrayList<>();
        if (match(TokenType.IMPLEMENTS)) {
            do {
                Token interfaceName = consume(TokenType.IDENTIFIER, "Expected interface name.");
                String fullInterfaceName = interfaceName.getLexeme();
                
                if (check(TokenType.LESS)) {
                    StringBuilder genericInterfaceName = new StringBuilder(fullInterfaceName);
                    genericInterfaceName.append("<");
                    
                    advance(); 
                    int depth = 1;
                    while (depth > 0 && !isAtEnd()) {
                        Token token = advance();
                        genericInterfaceName.append(token.getLexeme());
                        
                        if (token.getType() == TokenType.LESS) {
                            depth++;
                        } else if (token.getType() == TokenType.GREATER) {
                            depth--;
                        }
                        
                        if (token.getType() == TokenType.COMMA && depth == 1) {
                            genericInterfaceName.append(" ");
                        }
                    }
                    fullInterfaceName = genericInterfaceName.toString();
                }
                
                Token interfaceToken = new Token(interfaceName.getType(), fullInterfaceName, interfaceName.getLine());
                interfaces.add(new VariableExpr(interfaceToken));
            } while (match(TokenType.COMMA));
        }
        
        consume(TokenType.LBRACE, "Expected '{' before class body.");
        List<FunctionDecl> functions = new ArrayList<>();
        List<VarDecl> variables = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            Set<Modifier> modifiers = parseModifiers();
            
            if (checkType()) {
                Token typeToken = consumeType("Expected type.");
                Token nameToken = consume(TokenType.IDENTIFIER, "Expected name after type.");
                if (check(TokenType.LPAREN)) {
                    functions.add(parseFunctionDecl(typeToken, nameToken, modifiers));
                } else {
                    variables.add(parseVarDecl(typeToken, nameToken, modifiers));
                }
            } else {
                throw error(peek(), "Expected field or method declaration.");
            }
        }
        consume(TokenType.RBRACE, "Expected '}' after class body.");
        
        ClassDecl classDecl;
        if (!typeParameters.isEmpty()) {
            classDecl = new GenericClassDecl(name.getLexeme(), typeParameters, superclass, interfaces, functions, variables, classModifiers);
        } else {
            classDecl = new ClassDecl(name.getLexeme(), superclass, interfaces, functions, variables, classModifiers);
        }
        
        classDecl.setSourceLocation(name.getLocation());
        return classDecl;
    }
    
    private InterfaceDecl parseInterfaceDecl() {
        Set<Modifier> interfaceModifiers = parseModifiers();
        
        consume(TokenType.INTERFACE, "Expected 'interface' keyword to start an interface declaration.");
        Token name = consume(TokenType.IDENTIFIER, "Expected interface name after 'interface'.");
        
        List<TypeParameter> typeParameters = new ArrayList<>();
        if (check(TokenType.LESS)) {
            typeParameters = parseTypeParameters();
        }
        
        List<VariableExpr> parentInterfaces = new ArrayList<>();
        if (match(TokenType.EXTENDS)) {
            do {
                consume(TokenType.IDENTIFIER, "Expected parent interface name.");
                parentInterfaces.add(new VariableExpr(previous()));
            } while (match(TokenType.COMMA));
        }
        
        consume(TokenType.LBRACE, "Expected '{' before interface body.");
        
        List<FunctionDecl> methods = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            Set<Modifier> methodModifiers = parseModifiers();
            
            if (checkType()) {
                Token typeToken = consumeType("Expected return type for interface method.");
                Token nameToken = consume(TokenType.IDENTIFIER, "Expected method name after return type.");
                
                if (!check(TokenType.LPAREN)) {
                    throw error(peek(), "Interface can only contain method declarations, not fields.");
                }
                consume(TokenType.LPAREN, "Expected '(' after method name.");
                List<VarDecl> parameters = new ArrayList<>();
                if (!check(TokenType.RPAREN)) {
                    do {
                        parameters.add(parseParameter());
                    } while (match(TokenType.COMMA));
                }
                consume(TokenType.RPAREN, "Expected ')' after method parameters.");
                consume(TokenType.SEMICOLON, "Expected ';' after interface method declaration.");

                FunctionDecl method = new FunctionDecl(typeToken.getLexeme(), nameToken.getLexeme(), parameters, null, methodModifiers);
                method.setSourceLocation(nameToken.getLocation());
                methods.add(method);
            } else {
                throw error(peek(), "Expected method declaration in interface.");
            }
        }
        
        consume(TokenType.RBRACE, "Expected '}' after interface body.");
        
        InterfaceDecl interfaceDecl;
        if (!typeParameters.isEmpty()) {
            interfaceDecl = new GenericInterfaceDecl(name.getLexeme(), typeParameters, parentInterfaces, methods, interfaceModifiers);
        } else {
            interfaceDecl = new InterfaceDecl(name.getLexeme(), parentInterfaces, methods, interfaceModifiers);
        }
        
        interfaceDecl.setSourceLocation(name.getLocation());
        return interfaceDecl;
    }

    private Expression parseCallDot() {
        Expression expr = parsePrimary();
        while (true) {
            if (match(TokenType.LPAREN)) {
                expr = parseCallArguments(expr);
            } else if (match(TokenType.DOT)) {
                Token name = consume(TokenType.IDENTIFIER, "Expect property name after '.'.");
                
                if (expr instanceof VariableExpr && isClassName(((VariableExpr) expr).getName().getLexeme())) {
                    StaticAccessExpr staticExpr = new StaticAccessExpr((VariableExpr) expr, name);
                    staticExpr.setSourceLocation(name.getLocation());
                    expr = staticExpr;
                } else {
                    GetExpr getExpr = new GetExpr(expr, name);
                    getExpr.setSourceLocation(name.getLocation());
                    expr = getExpr;
                }
            }
            else if (match(TokenType.LBRACKET)) {
                Token lBracket = previous(); 
                Expression index = parseExpression();
                consume(TokenType.RBRACKET, "Expected ']' after array index.");
                IndexExpr indexExpr = new IndexExpr(expr, index);
                indexExpr.setSourceLocation(lBracket.getLocation());
                expr = indexExpr;
            }
            else {
                break;
            }
        }
        return expr;
    }
    
    private boolean isClassName(String name) {
        return name.length() > 0 && Character.isUpperCase(name.charAt(0));
    }

    
    
    private FunctionDecl parseFunctionDecl(Token returnType, Token name, Set<Modifier> modifiers) {
        consume(TokenType.LPAREN, "Expected '(' after function name.");
        List<VarDecl> parameters = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            do {
                parameters.add(parseParameter());
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RPAREN, "Expected ')' after function parameters.");
        
        Block body = null;
        if (modifiers.contains(Modifier.ABSTRACT)) {
            consume(TokenType.SEMICOLON, "Expected ';' after abstract method declaration.");
        } else {
            body = parseBlock();
        }
        
        FunctionDecl functionDecl = new FunctionDecl(returnType.getLexeme(), name.getLexeme(), parameters, body, modifiers);
        functionDecl.setSourceLocation(returnType.getLocation());
        return functionDecl;
    }

    private VarDecl parseParameter() {
        Token type = consumeType("Expected type in parameter declaration.");
        Token name = consume(TokenType.IDENTIFIER, "Expected parameter name.");
        VarDecl varDecl = new VarDecl(type.getLexeme(), name.getLexeme(), null);
        varDecl.setSourceLocation(type.getLocation());
        return varDecl;
    }

    private VarDecl parseVarDecl(Token type, Token name) {
        Expression initializer = null;
        if (match(TokenType.ASSIGN)) {
            initializer = parseExpression();
        }
        consume(TokenType.SEMICOLON, "Expected ';' after variable declaration.");
        VarDecl varDecl = new VarDecl(type.getLexeme(), name.getLexeme(), initializer);
        varDecl.setSourceLocation(name.getLocation());
        return varDecl;
    }
    
    private VarDecl parseVarDecl(Token type, Token name, Set<Modifier> modifiers) {
        Expression initializer = null;
        if (match(TokenType.ASSIGN)) {
            initializer = parseExpression();
        }
        consume(TokenType.SEMICOLON, "Expected ';' after variable declaration.");
        VarDecl varDecl = new VarDecl(type.getLexeme(), name.getLexeme(), initializer, modifiers);
        varDecl.setSourceLocation(name.getLocation());
        return varDecl;
    }

    private Block parseBlock() {
        Token lbrace = consume(TokenType.LBRACE, "Expected '{' to start block.");
        List<Statement> statements = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            statements.add(parseStatement());
        }
        consume(TokenType.RBRACE, "Expected '}' after block.");
        Block block = new Block(statements);
        block.setSourceLocation(lbrace.getLocation());
        return block;
    }

    private Expression parseCallArguments(Expression callee) {
        Token lParen = previous(); 
        List<Expression> arguments = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            do {
                arguments.add(parseExpression());
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RPAREN, "Expect ')' after arguments.");
        CallExpr callExpr = new CallExpr(callee, arguments);
        callExpr.setSourceLocation(lParen.getLocation());
        return callExpr;
    }

    private Statement parseStatement() {
        if (match(TokenType.BREAK)) {
            Token breakToken = previous();
            consume(TokenType.SEMICOLON, "Expected ';' after 'break'.");
            BreakStmt breakStmt = new BreakStmt();
            breakStmt.setSourceLocation(breakToken.getLocation());
            return breakStmt;
        }
        if (match(TokenType.CONTINUE)) {
            Token continueToken = previous();
            consume(TokenType.SEMICOLON, "Expected ';' after 'continue'.");
            ContinueStmt continueStmt = new ContinueStmt();
            continueStmt.setSourceLocation(continueToken.getLocation());
            return continueStmt;
        }
        if (match(TokenType.RETURN)) {
            return parseReturnStmt();
        }
        if (check(TokenType.LBRACE)) {
            return parseBlock();
        }
        if (match(TokenType.IF)) {
            return parseIf();
        }
        if (match(TokenType.WHILE)) {
            return parseWhile();
        }
        if (match(TokenType.FOR)) {
            Token forToken = previous();
            return parseFor(forToken);
        }
        if (match(TokenType.TRY)) {
            return parseTryStmt();
        }
        if (match(TokenType.THROW)) {
            return parseThrowStmt();
        }

        if (isVariableDeclaration()) {
            Token typeToken = consumeType("Expected variable type.");
            Token nameToken = consume(TokenType.IDENTIFIER, "Expected variable name.");
            return parseVarDecl(typeToken, nameToken);
        }
        return parseExpressionStmt();
    }

    private boolean isVariableDeclaration() {
        if (tokens.size() <= current + 1) {
            return false;
        }

        if (!checkType()) {
            return false;
        }
        int lookAhead = current + 1;
        
        // Handle array types: Type[]
        if (lookAhead < tokens.size() && tokens.get(lookAhead).getType() == TokenType.LBRACKET) {
            lookAhead++;
            if (lookAhead < tokens.size() && tokens.get(lookAhead).getType() == TokenType.RBRACKET) {
                lookAhead++;
            } else {
                return false;
            }
        }
        
        // Handle generic types: Type<T, U>
        if (lookAhead < tokens.size() && tokens.get(lookAhead).getType() == TokenType.LESS) {
            int depth = 1;
            lookAhead++;
            while (lookAhead < tokens.size() && depth > 0) {
                TokenType type = tokens.get(lookAhead).getType();
                if (type == TokenType.LESS) {
                    depth++;
                } else if (type == TokenType.GREATER) {
                    depth--;
                }
                lookAhead++;
            }
            if (depth != 0) return false;
        }

        if (lookAhead < tokens.size() && tokens.get(lookAhead).getType() == TokenType.IDENTIFIER) {
            return true;
        }

        return false;
    }

    private ReturnStmt parseReturnStmt() {
        Token returnToken = previous();
        Expression value = null;
        if (!check(TokenType.SEMICOLON)) {
            value = parseExpression();
        }
        consume(TokenType.SEMICOLON, "Expected ';' after return statement.");
        ReturnStmt returnStmt = new ReturnStmt(value);
        returnStmt.setSourceLocation(returnToken.getLocation());
        return returnStmt;
    }



    private Statement parseExpressionStmt() {
        Expression expr = parseExpression();
        consume(TokenType.SEMICOLON, "Expected ';' after expression.");
        ExpressionStmt stmt = new ExpressionStmt(expr);
        stmt.setSourceLocation(expr.getSourceLocation());
        return stmt;
    }

    private Expression parseExpression() {
        return parseAssignment();
    }

    private Expression parseAssignment() {
        Expression expr = parseLogicalOr();
        if (match(TokenType.ASSIGN)) {
            Token equals = previous();
            Expression value = parseAssignment();
            if (expr instanceof VariableExpr varExpr) {
                Token name = varExpr.getName();
                AssignmentExpr assignExpr = new AssignmentExpr(name, value);
                assignExpr.setSourceLocation(varExpr.getSourceLocation());
                return assignExpr;
            } else if (expr instanceof GetExpr getExpr) {
                SetExpr setExpr = new SetExpr(getExpr.getObject(), getExpr.getName(), value);
                setExpr.setSourceLocation(getExpr.getSourceLocation());
                return setExpr;
            } else if (expr instanceof IndexExpr indexExpr) {
                IndexAssignExpr indexAssignExpr = new IndexAssignExpr(indexExpr.getObject(), indexExpr.getIndex(), value);
                indexAssignExpr.setSourceLocation(indexExpr.getSourceLocation());
                return indexAssignExpr;
            } else if (expr instanceof StaticAccessExpr staticExpr) {
                StaticAssignExpr staticAssignExpr = new StaticAssignExpr(staticExpr.className, staticExpr.memberName, value);
                staticAssignExpr.setSourceLocation(staticExpr.getSourceLocation());
                return staticAssignExpr;
            }

            throw error(equals, "Invalid assignment target.");
        }
        return expr;
    }

    private Expression parseLogicalOr() {
        Expression expr = parseLogicalAnd();
        while (match(TokenType.OR)) {
            Token operator = previous();
            Expression right = parseLogicalAnd();
            BinaryExpr binaryExpr = new BinaryExpr(expr, operator, right);
            binaryExpr.setSourceLocation(operator.getLocation());
            expr = binaryExpr;
        }
        return expr;
    }

    private Expression parseLogicalAnd() {
        Expression expr = parseEquality();
        while (match(TokenType.AND)) {
            Token operator = previous();
            Expression right = parseEquality();
            BinaryExpr binaryExpr = new BinaryExpr(expr, operator, right);
            binaryExpr.setSourceLocation(operator.getLocation());
            expr = binaryExpr;
        }
        return expr;
    }

    private Expression parseEquality() {
        Expression expr = parseComparison();
        while (match(TokenType.EQUALITY, TokenType.NEQ)) {
            Token operator = previous();
            Expression right = parseComparison();
            BinaryExpr binaryExpr = new BinaryExpr(expr, operator, right);
            binaryExpr.setSourceLocation(operator.getLocation());
            expr = binaryExpr;
        }
        return expr;
    }

    private Expression parseComparison() {
        Expression expr = parseTerm();
        while (match(TokenType.LESS, TokenType.LEQ, TokenType.GREATER, TokenType.GEQ)) {
            Token operator = previous();
            Expression right = parseTerm();
            BinaryExpr binaryExpr = new BinaryExpr(expr, operator, right);
            binaryExpr.setSourceLocation(operator.getLocation());
            expr = binaryExpr;
        }
        return expr;
    }

    private Expression parseTerm() {
        Expression expr = parseFactor();
        while (match(TokenType.PLUS, TokenType.MINUS)) {
            Token operator = previous();
            Expression right = parseFactor();
            BinaryExpr binaryExpr = new BinaryExpr(expr, operator, right);
            binaryExpr.setSourceLocation(operator.getLocation());
            expr = binaryExpr;
        }
        return expr;
    }

    private Expression parseFactor() {
        Expression expr = parseUnary();
        while (match(TokenType.STAR, TokenType.SLASH, TokenType.MOD)) {
            Token operator = previous();
            Expression right = parseUnary();
            BinaryExpr binaryExpr = new BinaryExpr(expr, operator, right);
            binaryExpr.setSourceLocation(operator.getLocation());
            expr = binaryExpr;
        }
        return expr;
    }

    private Expression parseUnary() {
        if (match(TokenType.INCREMENT, TokenType.DECREMENT)) {
            Token operator = previous();
            Expression target = parseUnary();

            if (!(target instanceof VariableExpr || target instanceof GetExpr || target instanceof IndexExpr)) {
                throw error(operator, "Invalid prefix " + (operator.getType() == TokenType.INCREMENT ? "increment" : "decrement") + " target.");
            }

            PrefixIncrementExpr prefixExpr = new PrefixIncrementExpr(operator, target);
            prefixExpr.setSourceLocation(operator.getLocation());
            return prefixExpr;
        }

        if (match(TokenType.MINUS, TokenType.NOT)) {
            Token operator = previous();
            Expression right = parseUnary();
            UnaryExpr unaryExpr = new UnaryExpr(operator, right);
            unaryExpr.setSourceLocation(operator.getLocation());
            return unaryExpr;
        }

        return parsePostfix();

    }

    private Expression parsePostfix() {
        Expression expr = parseCallDot();

        if (match(TokenType.INCREMENT, TokenType.DECREMENT)) {
            Token operator = previous();

            if (!(expr instanceof VariableExpr || expr instanceof GetExpr || expr instanceof IndexExpr)) {
                throw error(operator, "Invalid " + (operator.getType() == TokenType.INCREMENT ? "increment" : "decrement") + " target.");
            }

            PostfixIncrementExpr postfixExpr = new PostfixIncrementExpr(expr, operator);
            postfixExpr.setSourceLocation(operator.getLocation());
            return postfixExpr;
        }

        return expr;

    }
    private Expression arrayLiteral() {
        Token lBracket = previous(); 
        List<Expression> elements = new ArrayList<>();

        if (!check(TokenType.RBRACKET)) {
            do {
                elements.add(parseExpression());
            } while (match(TokenType.COMMA));
        }

        consume(TokenType.RBRACKET, "Expected ']' after array elements.");
        ArrayExpr arrayExpr = new ArrayExpr(elements);
        arrayExpr.setSourceLocation(lBracket.getLocation());
        return arrayExpr;
    }


    private Expression parsePrimary() {
        if (match(TokenType.NUMBER)) {
            String numberString = previous().getLexeme();
            LiteralExpr expr;
            if (numberString.contains(".")) {
                expr = new LiteralExpr(Double.parseDouble(numberString));
            } else {
                expr = new LiteralExpr(Long.parseLong(numberString));
            }
            expr.setSourceLocation(previous().getLocation());
            return expr;
        }
        if (match(TokenType.STRING)) {
            LiteralExpr expr = new LiteralExpr(previous().getLexeme());
            expr.setSourceLocation(previous().getLocation());
            return expr;
        }
        if (match(TokenType.CHAR)) {
            LiteralExpr expr = new LiteralExpr(previous().getLexeme().charAt(0));
            expr.setSourceLocation(previous().getLocation());
            return expr;
        }
        if (match(TokenType.BOOLEAN)) {
            LiteralExpr expr = new LiteralExpr(Boolean.parseBoolean(previous().getLexeme()));
            expr.setSourceLocation(previous().getLocation());
            return expr;
        }
        if (match(TokenType.LBRACKET)) {
            return arrayLiteral();
        }

        if (match(TokenType.NEW)) {
            Token newToken = previous();
            if (check(TokenType.NUM) || check(TokenType.DUO) || check(TokenType.EK) || 
                check(TokenType.SAB) || check(TokenType.KYA)) {
                Token typeToken = advance(); 
                consume(TokenType.LBRACKET, "Expected '[' after type for array creation.");
                Expression size = parseExpression();
                consume(TokenType.RBRACKET, "Expected ']' after array size.");
                NewArrayExpr expr = new NewArrayExpr(typeToken.getLexeme(), size);
                expr.setSourceLocation(newToken.getLocation());
                return expr;
            } else {
                Token classNameToken = consume(TokenType.IDENTIFIER, "Expect class name after 'new'.");
                String className = classNameToken.getLexeme();
                
                if (check(TokenType.LESS)) {
                    StringBuilder genericClassName = new StringBuilder(className);
                    genericClassName.append("<");
                    
                    advance(); 
                    int depth = 1;
                    while (depth > 0 && !isAtEnd()) {
                        Token token = advance();
                        genericClassName.append(token.getLexeme());
                        
                        if (token.getType() == TokenType.LESS) {
                            depth++;
                        } else if (token.getType() == TokenType.GREATER) {
                            depth--;
                        }
                        
                        if (token.getType() == TokenType.COMMA && depth == 1) {
                            genericClassName.append(" ");
                        }
                    }
                    className = genericClassName.toString();
                }
                
                consume(TokenType.LPAREN, "Expect '(' after class name.");
                List<Expression> arguments = new ArrayList<>();
                if (!check(TokenType.RPAREN)) {
                    do {
                        arguments.add(parseExpression());
                    } while (match(TokenType.COMMA));
                }
                consume(TokenType.RPAREN, "Expect ')' after arguments.");
                NewExpr expr = new NewExpr(className, arguments);
                expr.setSourceLocation(newToken.getLocation());
                return expr;
            }
        }
        if (match(TokenType.IDENTIFIER)) {
            Token identifierToken = previous();
            VariableExpr expr = new VariableExpr(identifierToken);
            expr.setSourceLocation(identifierToken.getLocation());
            return expr;
        }
        if (match(TokenType.SUPER)) {
            Token keyword = previous();
            consume(TokenType.DOT, "Expect '.' after 'super'.");
            Token method = consume(TokenType.IDENTIFIER, "Expect superclass method name.");
            SuperExpr superExpr = new SuperExpr(keyword, method);
            superExpr.setSourceLocation(keyword.getLocation());
            return superExpr;
        }
        if (match(TokenType.THIS)) {
            Token thisToken = previous();
            ThisExpr thisExpr = new ThisExpr(thisToken);
            thisExpr.setSourceLocation(thisToken.getLocation());
            return thisExpr;
        }
        if (match(TokenType.LPAREN)) {
            Expression expr = parseExpression();
            consume(TokenType.RPAREN, "Expected ')' after expression.");
            return expr;
        }

        throw error(peek(), "Expected expression.");
    }

    private Statement parseIf() {
        Token ifToken = previous();
        consume(TokenType.LPAREN, "Expect '(' after 'if'.");
        Expression condition = parseExpression();
        consume(TokenType.RPAREN, "Expect ')' after if condition.");
        Statement thenBranch = parseStatement();
        Statement elseBranch = null;
        if (match(TokenType.ELSE)) {
            if (match(TokenType.IF)) {
                elseBranch = parseIf();
            } else {
                elseBranch = parseStatement();
            }
        }
        IfStmt ifStmt = new IfStmt(condition, thenBranch, elseBranch);
        ifStmt.setSourceLocation(ifToken.getLocation());
        return ifStmt;
    }

    private Statement parseWhile() {
        Token whileToken = previous();
        consume(TokenType.LPAREN, "Expect '(' after 'while'.");
        Expression condition = parseExpression();
        consume(TokenType.RPAREN, "Expect ')' after while condition.");
        Statement body = parseStatement();
        WhileStmt whileStmt = new WhileStmt(condition, body);
        whileStmt.setSourceLocation(whileToken.getLocation());
        return whileStmt;
    }

    private Statement parseFor(Token forToken) {
        consume(TokenType.LPAREN, "Expect '(' after 'for'.");
        
        Statement initializer = null;
        if (match(TokenType.SEMICOLON)) {
        } else if (checkType()) {
            Token typeToken = consumeType("Expected variable type.");
            Token nameToken = consume(TokenType.IDENTIFIER, "Expected variable name.");
            initializer = parseVarDecl(typeToken, nameToken);
        } else {
            initializer = parseExpressionStmt();
        }
        
        Expression condition = null;
        if (!check(TokenType.SEMICOLON)) {
            condition = parseExpression();
        }
        consume(TokenType.SEMICOLON, "Expect ';' after loop condition.");
        
        Expression increment = null;
        if (!check(TokenType.RPAREN)) {
            increment = parseExpression();
        }
        consume(TokenType.RPAREN, "Expect ')' after for clauses.");
        
        Statement body = parseStatement();
        if (condition == null) {
            condition = new LiteralExpr(true);
            condition.setSourceLocation(forToken.getLocation());
        }
        
        if (increment != null) {
            List<Statement> loopStatements = new ArrayList<>();
            loopStatements.add(body);
            ExpressionStmt incrementStmt = new ExpressionStmt(increment);
            incrementStmt.setSourceLocation(increment.getSourceLocation());
            loopStatements.add(incrementStmt);
            Block loopBlock = new Block(loopStatements);
            loopBlock.setSourceLocation(body.getSourceLocation()); // Use body location for synthetic block
            
            Statement whileStmt = new WhileStmt(condition, loopBlock);
            whileStmt.setSourceLocation(forToken.getLocation());
            if (initializer != null) {
                Block outerBlock = new Block(List.of(initializer, whileStmt));
                outerBlock.setSourceLocation(forToken.getLocation());
                return outerBlock;
            }
            return whileStmt;
        } else {
            Statement whileStmt = new WhileStmt(condition, body);
            whileStmt.setSourceLocation(forToken.getLocation());
            if (initializer != null) {
                Block outerBlock = new Block(List.of(initializer, whileStmt));
                outerBlock.setSourceLocation(forToken.getLocation());
                return outerBlock;
            }
            return whileStmt;
        }
    }

    
    private TryStmt parseTryStmt() {
        Token tryToken = previous();
        Block tryBlock = parseBlock();
        
        List<CatchClause> catchClauses = new ArrayList<>();
        while (match(TokenType.CATCH)) {
            consume(TokenType.LPAREN, "Expected '(' after 'catch'.");
            
            // Parse optional exception type and parameter name
            String exceptionType = "any"; // Default to catch all exceptions
            Token parameterName;
            
            if (check(TokenType.IDENTIFIER)) {
                Token firstToken = advance(); // Could be type or parameter name
                
                if (check(TokenType.IDENTIFIER)) {
                    // Two identifiers: first is type, second is parameter name
                    exceptionType = firstToken.getLexeme();
                    parameterName = advance();
                } else {
                    // Single identifier: parameter name, type defaults to "any"
                    parameterName = firstToken;
                }
            } else {
                throw error(peek(), "Expected parameter name in catch clause.");
            }
            
            consume(TokenType.RPAREN, "Expected ')' after catch parameter.");
            Block catchBody = parseBlock();
            catchClauses.add(new CatchClause(exceptionType, parameterName.getLexeme(), catchBody));
        }
        
        Block finallyBlock = null;
        if (match(TokenType.FINALLY)) {
            finallyBlock = parseBlock();
        }
        
        if (catchClauses.isEmpty() && finallyBlock == null) {
            throw error(previous(), "A try statement must have at least one catch or finally clause.");
        }
        
        TryStmt tryStmt = new TryStmt(tryBlock, catchClauses, finallyBlock);
        tryStmt.setSourceLocation(tryToken.getLocation());
        return tryStmt;
    }
    
    private ThrowStmt parseThrowStmt() {
        Token throwToken = previous(); 
        Expression value = parseExpression();
        consume(TokenType.SEMICOLON, "Expected ';' after throw statement.");
        return new ThrowStmt(value, throwToken);
    }

    private boolean checkType() {
        boolean isBaseType = check(TokenType.NUM) || check(TokenType.DUO) || check(TokenType.EK) ||
                check(TokenType.SAB) || check(TokenType.KYA) || check(TokenType.KAAM) ||
                check(TokenType.IDENTIFIER);

        if (!isBaseType) return false;
        
        int lookahead = current + 1;
        if (lookahead < tokens.size() && tokens.get(lookahead).getType() == TokenType.LBRACKET) {
            return lookahead + 1 < tokens.size() && tokens.get(lookahead + 1).getType() == TokenType.RBRACKET;
        }
        
        if (lookahead < tokens.size() && tokens.get(lookahead).getType() == TokenType.LESS) {
            int depth = 1;
            lookahead++;
            while (lookahead < tokens.size() && depth > 0) {
                TokenType type = tokens.get(lookahead).getType();
                if (type == TokenType.LESS) {
                    depth++;
                } else if (type == TokenType.GREATER) {
                    depth--;
                }
                lookahead++;
            }
            return depth == 0; 
        }

        return true;
    }


    private Token consumeType(String message) {
        if (!checkType()) {
            throw error(peek(), message);
        }

        Token baseType = advance();

        if (check(TokenType.LBRACKET)) {
            advance();
            consume(TokenType.RBRACKET, "Expected ']' after '[' in array type.");
            return new Token(baseType.getType(), baseType.getLexeme() + "[]", baseType.getLine());
        }
        
        if (check(TokenType.LESS)) {
            StringBuilder genericTypeName = new StringBuilder(baseType.getLexeme());
            genericTypeName.append("<");
            
            advance(); 
            int depth = 1;
            while (depth > 0 && !isAtEnd()) {
                Token token = advance();
                genericTypeName.append(token.getLexeme());
                
                if (token.getType() == TokenType.LESS) {
                    depth++;
                } else if (token.getType() == TokenType.GREATER) {
                    depth--;
                }
                
                if (token.getType() == TokenType.COMMA && depth == 1) {
                    genericTypeName.append(" ");
                }
            }
            
            return new Token(TokenType.IDENTIFIER, genericTypeName.toString(), baseType.getLine());
        }

        return baseType;
    }


    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().getType() == type;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();
        throw error(peek(), message);
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private boolean isAtEnd() {
        return peek().getType() == TokenType.EOF;
    }
    
    private Set<Modifier> parseModifiers() {
        Set<Modifier> modifiers = new HashSet<>();
        
        if (match(TokenType.OVERRIDE)) {
            modifiers.add(Modifier.OVERRIDE);
        }
        
        while (match(TokenType.PUBLIC, TokenType.PRIVATE, TokenType.PROTECTED, TokenType.STATIC, TokenType.ABSTRACT, TokenType.FINAL)) {
            TokenType tokenType = previous().getType();
            Modifier modifier = Modifier.fromTokenType(tokenType);
            if (modifiers.contains(modifier)) {
                throw error(previous(), "Duplicate modifier: " + modifier);
            }
            modifiers.add(modifier);
        }
        
        if (!modifiers.contains(Modifier.PUBLIC) && !modifiers.contains(Modifier.PRIVATE) && !modifiers.contains(Modifier.PROTECTED)) {
            modifiers.add(Modifier.PUBLIC);
        }
        
        return modifiers;
    }
    
    private List<TypeParameter> parseTypeParameters() {
        List<TypeParameter> typeParameters = new ArrayList<>();
        
        consume(TokenType.LESS, "Expected '<' to start type parameters.");
        
        do {
            Token nameToken = consume(TokenType.IDENTIFIER, "Expected type parameter name.");
            
            List<GenericType> bounds = new ArrayList<>();
            if (match(TokenType.EXTENDS)) {
                do {
                    bounds.add(parseGenericType());
                } while (match(TokenType.AND)); 
            }
            
            TypeParameter param = new TypeParameter(nameToken, bounds);
            typeParameters.add(param);
            
        } while (match(TokenType.COMMA));
        
        consume(TokenType.GREATER, "Expected '>' to close type parameters.");
        
        return typeParameters;
    }
    
    
    private GenericType parseGenericType() {
        if (match(TokenType.QUESTION)) {
            GenericType.WildcardType wildcardType = null;
            Token boundType = null;
            
            if (match(TokenType.EXTENDS)) {
                wildcardType = GenericType.WildcardType.EXTENDS;
                boundType = consume(TokenType.IDENTIFIER, "Expected type after 'extends' in wildcard.");
            } else if (match(TokenType.SUPER)) {
                wildcardType = GenericType.WildcardType.SUPER;
                boundType = consume(TokenType.IDENTIFIER, "Expected type after 'super' in wildcard.");
            }
            
            return new GenericType(boundType, new ArrayList<>(), wildcardType);
        }
        
        Token baseType = consume(TokenType.IDENTIFIER, "Expected type name.");
        List<GenericType> typeArguments = new ArrayList<>();
        
        if (check(TokenType.LESS)) {
            advance(); 
            
            do {
                typeArguments.add(parseGenericType());
            } while (match(TokenType.COMMA));
            
            consume(TokenType.GREATER, "Expected '>' to close type arguments.");
        }
        
        return new GenericType(baseType, typeArguments);
    }

    private ParseException error(Token token, String message) {
        if (errorReporter != null) {
            String hint = dhrlang.error.ErrorMessages.getParseErrorHint(message, token);
            if (hint != null) {
                errorReporter.error(token.getLocation(), message, hint);
            } else {
                errorReporter.error(token.getLocation(), message);
            }
        }
        return new ParseException(message, token);
    }
}