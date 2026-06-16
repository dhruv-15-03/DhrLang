package dhrlang.parser;

import dhrlang.ast.*;
import dhrlang.error.ErrorReporter;
import dhrlang.lexer.Token;
import dhrlang.lexer.TokenType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.EnumSet;

/**
 * Container class for parsed modifiers and contract annotations.
 */
class ParsedModifiers {
    final Set<Modifier> modifiers;
    final Set<ContractAnnotation> contractAnnotations;
    
    ParsedModifiers(Set<Modifier> modifiers, Set<ContractAnnotation> contractAnnotations) {
        this.modifiers = modifiers;
        this.contractAnnotations = contractAnnotations;
    }
}

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
        ParsedModifiers parsed = parseAllModifiers();
        Set<Modifier> classModifiers = parsed.modifiers;
        Set<ContractAnnotation> classAnnotations = parsed.contractAnnotations;
        
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
            ParsedModifiers memberParsed = parseAllModifiers();
            Set<Modifier> modifiers = memberParsed.modifiers;
            Set<ContractAnnotation> memberAnnotations = memberParsed.contractAnnotations;
            
            if (checkType()) {
                Token typeToken = consumeType("Expected type.");
                Token nameToken = consume(TokenType.IDENTIFIER, "Expected name after type.");
                if (check(TokenType.LPAREN)) {
                    functions.add(parseFunctionDecl(typeToken, nameToken, modifiers, memberAnnotations));
                } else {
                    variables.add(parseVarDecl(typeToken, nameToken, modifiers, memberAnnotations));
                }
            } else {
                throw error(peek(), "Expected field or method declaration.");
            }
        }
        consume(TokenType.RBRACE, "Expected '}' after class body.");
        
        ClassDecl classDecl;
        if (!typeParameters.isEmpty()) {
            classDecl = new GenericClassDecl(name.getLexeme(), typeParameters, superclass, interfaces, functions, variables, classModifiers, classAnnotations);
        } else {
            classDecl = new ClassDecl(name.getLexeme(), superclass, interfaces, functions, variables, classModifiers, classAnnotations);
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
        return parseFunctionDecl(returnType, name, modifiers, EnumSet.noneOf(ContractAnnotation.class));
    }
    
    private FunctionDecl parseFunctionDecl(Token returnType, Token name, Set<Modifier> modifiers, Set<ContractAnnotation> contractAnnotations) {
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
        
        FunctionDecl functionDecl = new FunctionDecl(returnType.getLexeme(), name.getLexeme(), parameters, body, modifiers, contractAnnotations);
        functionDecl.setSourceLocation(returnType.getLocation());
        return functionDecl;
    }

    private VarDecl parseParameter() {
        boolean indexed = false;
        // Contextual 'indexed' modifier for event parameters (e.g. `indexed num from`).
        // Only treated as a modifier when a type+name still follow, so an ordinary
        // parameter whose type happens to be named "indexed" is unaffected.
        if (check(TokenType.IDENTIFIER) && "indexed".equals(peek().getLexeme())
                && current + 1 < tokens.size()
                && tokens.get(current + 1).getType() != TokenType.COMMA
                && tokens.get(current + 1).getType() != TokenType.RPAREN) {
            advance();
            indexed = true;
        }
        Token type = consumeType("Expected type in parameter declaration.");
        Token name = consume(TokenType.IDENTIFIER, "Expected parameter name.");
        VarDecl varDecl = new VarDecl(type.getLexeme(), name.getLexeme(), null);
        varDecl.setSourceLocation(type.getLocation());
        varDecl.setIndexed(indexed);
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
        return parseVarDecl(type, name, modifiers, EnumSet.noneOf(ContractAnnotation.class));
    }
    
    private VarDecl parseVarDecl(Token type, Token name, Set<Modifier> modifiers, Set<ContractAnnotation> contractAnnotations) {
        Expression initializer = null;
        if (match(TokenType.ASSIGN)) {
            initializer = parseExpression();
        }
        consume(TokenType.SEMICOLON, "Expected ';' after variable declaration.");
        VarDecl varDecl = new VarDecl(type.getLexeme(), name.getLexeme(), initializer, modifiers, contractAnnotations);
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
            String label = null;
            if (check(TokenType.IDENTIFIER)) {
                label = advance().getLexeme();
            }
            consume(TokenType.SEMICOLON, "Expected ';' after 'break'.");
            BreakStmt breakStmt = new BreakStmt(label);
            breakStmt.setSourceLocation(breakToken.getLocation());
            return breakStmt;
        }
        if (match(TokenType.CONTINUE)) {
            Token continueToken = previous();
            String label = null;
            if (check(TokenType.IDENTIFIER)) {
                label = advance().getLexeme();
            }
            consume(TokenType.SEMICOLON, "Expected ';' after 'continue'.");
            ContinueStmt continueStmt = new ContinueStmt(label);
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
        if (match(TokenType.SWITCH)) {
            return parseSwitchStmt();
        }
        if (match(TokenType.DO)) {
            return parseDoWhile();
        }
        if (match(TokenType.TRY)) {
            return parseTryStmt();
        }
        if (match(TokenType.THROW)) {
            return parseThrowStmt();
        }
        if (match(TokenType.EMIT)) {
            // emit EventName(args...) → syntactic sugar for EventName(args...)
            return parseExpressionStmt();
        }

        // Labeled loops: label: while/for/do
        if (check(TokenType.IDENTIFIER) && checkNext(TokenType.COLON)) {
            Token labelToken = advance(); // consume identifier
            advance(); // consume colon
            String label = labelToken.getLexeme();
            if (match(TokenType.WHILE)) {
                return parseLabeledWhile(label);
            } else if (match(TokenType.FOR)) {
                Token forToken = previous();
                return parseLabeledFor(label, forToken);
            } else if (match(TokenType.DO)) {
                return parseLabeledDoWhile(label);
            } else {
                throw error(peek(), "Label '" + label + "' must precede a loop statement (while, for, or do).");
            }
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
        
        // Handle array types: Type[] and multi-dimensional Type[][]...
        while (lookAhead + 1 < tokens.size()
                && tokens.get(lookAhead).getType() == TokenType.LBRACKET
                && tokens.get(lookAhead + 1).getType() == TokenType.RBRACKET) {
            lookAhead += 2;
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
        Expression expr = parseTernary();
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

    private Expression parseTernary() {
        Expression expr = parseLogicalOr();
        if (match(TokenType.QUESTION)) {
            Expression thenBranch = parseExpression();
            consume(TokenType.COLON, "Expected ':' after then-branch of ternary expression.");
            Expression elseBranch = parseTernary();
            TernaryExpr ternary = new TernaryExpr(expr, thenBranch, elseBranch);
            ternary.setSourceLocation(expr.getSourceLocation());
            return ternary;
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
        Expression expr = parseBitwiseOr();
        while (match(TokenType.AND)) {
            Token operator = previous();
            Expression right = parseBitwiseOr();
            BinaryExpr binaryExpr = new BinaryExpr(expr, operator, right);
            binaryExpr.setSourceLocation(operator.getLocation());
            expr = binaryExpr;
        }
        return expr;
    }

    private Expression parseBitwiseOr() {
        Expression expr = parseBitwiseXor();
        while (match(TokenType.BIT_OR)) {
            Token operator = previous();
            Expression right = parseBitwiseXor();
            expr = new BinaryExpr(expr, operator, right);
            expr.setSourceLocation(operator.getLocation());
        }
        return expr;
    }

    private Expression parseBitwiseXor() {
        Expression expr = parseBitwiseAnd();
        while (match(TokenType.BIT_XOR)) {
            Token operator = previous();
            Expression right = parseBitwiseAnd();
            expr = new BinaryExpr(expr, operator, right);
            expr.setSourceLocation(operator.getLocation());
        }
        return expr;
    }

    private Expression parseBitwiseAnd() {
        Expression expr = parseEquality();
        while (match(TokenType.BIT_AND)) {
            Token operator = previous();
            Expression right = parseEquality();
            expr = new BinaryExpr(expr, operator, right);
            expr.setSourceLocation(operator.getLocation());
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
        Expression expr = parseShift();
        while (match(TokenType.LESS, TokenType.LEQ, TokenType.GREATER, TokenType.GEQ)) {
            Token operator = previous();
            Expression right = parseShift();
            BinaryExpr binaryExpr = new BinaryExpr(expr, operator, right);
            binaryExpr.setSourceLocation(operator.getLocation());
            expr = binaryExpr;
        }
        // Handle 'as' cast: expr as Type → desugar to toNum(expr), toDuo(expr), toString(expr)
        if (match(TokenType.AS)) {
            Token asToken = previous();
            String typeName;
            if (match(TokenType.NUM)) {
                typeName = "num";
            } else if (match(TokenType.DUO)) {
                typeName = "duo";
            } else if (match(TokenType.SAB)) {
                typeName = "sab";
            } else if (match(TokenType.KYA)) {
                typeName = "kya";
            } else if (match(TokenType.IDENTIFIER)) {
                typeName = previous().getLexeme();
            } else {
                throw error(peek(), "Expected type name after 'as'. Supported: num, duo, sab.");
            }
            String converterFn = switch (typeName) {
                case "num" -> "toNum";
                case "duo" -> "toDuo";
                case "sab" -> "toString";
                default -> null;
            };
            if (converterFn == null) {
                throw error(asToken, "Cannot cast to '" + typeName + "'. Supported cast types: num, duo, sab.");
            }
            Token fnToken = syntheticToken(converterFn, asToken);
            VariableExpr fnRef = new VariableExpr(fnToken);
            fnRef.setSourceLocation(asToken.getLocation());
            CallExpr castCall = new CallExpr(fnRef, List.of(expr));
            castCall.setSourceLocation(asToken.getLocation());
            return castCall;
        }
        return expr;
    }

    private Expression parseShift() {
        Expression expr = parseTerm();
        while (match(TokenType.LSHIFT, TokenType.RSHIFT)) {
            Token operator = previous();
            Expression right = parseTerm();
            expr = new BinaryExpr(expr, operator, right);
            expr.setSourceLocation(operator.getLocation());
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

        if (match(TokenType.MINUS, TokenType.NOT, TokenType.BIT_NOT)) {
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
        if (match(TokenType.NULL)) {
            LiteralExpr expr = new LiteralExpr(null);
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
                // Support multi-dimensional: new num[a][b][c] and jagged: new num[a][]
                List<Expression> sizes = new ArrayList<>();
                boolean seenEmptyDim = false;
                do {
                    consume(TokenType.LBRACKET, "Expected '[' after type for array creation.");
                    if (check(TokenType.RBRACKET)) {
                        // Empty dimension for jagged arrays (e.g., new num[3][])
                        sizes.add(null);
                        seenEmptyDim = true;
                    } else {
                        if (seenEmptyDim) {
                            throw error(peek(), "Cannot specify a size after an empty dimension in array creation.");
                        }
                        Expression size = parseExpression();
                        sizes.add(size);
                    }
                    consume(TokenType.RBRACKET, "Expected ']' after array size.");
                } while (check(TokenType.LBRACKET));
                NewArrayExpr expr = new NewArrayExpr(typeToken.getLexeme(), sizes);
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
        
        // Detect for-each: for(Type name : expr)
        if (checkType()) {
            int saved = current;
            Token typeToken = consumeType("Expected variable type.");
            if (check(TokenType.IDENTIFIER)) {
                Token nameToken = advance();
                if (match(TokenType.COLON)) {
                    return parseForEach(forToken, typeToken, nameToken);
                }
            }
            // Not a for-each — reset and fall through to standard for
            current = saved;
        }

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
                loopBlock.markAsDesugaredForLoopBody();
            
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

    /**
     * Desugar for-each: for(Type name : arr) body
     * →  { Type[] $arr = arr; num $i = 0; while($i < arrayLength($arr)) { Type name = $arr[$i]; body; $i = $i + 1; } }
     * Uses synthetic variable names that can't collide with user code.
     */
    private Statement parseForEach(Token forToken, Token typeToken, Token nameToken) {
        Expression collection = parseExpression();
        consume(TokenType.RPAREN, "Expect ')' after for-each clause.");
        Statement body = parseStatement();

        // Synthetic tokens for desugared variables
        Token arrVar = syntheticToken("$forEach_arr", forToken);
        Token idxVar = syntheticToken("$forEach_i", forToken);
        Token numType = syntheticToken("num", forToken);

        // $arr = collection
        VarDecl arrDecl = new VarDecl(typeToken.getLexeme() + "[]", arrVar.getLexeme(), collection, Set.of());
        arrDecl.setSourceLocation(forToken.getLocation());

        // $i = 0
        LiteralExpr zero = new LiteralExpr(0L); zero.setSourceLocation(forToken.getLocation());
        VarDecl idxDecl = new VarDecl("num", idxVar.getLexeme(), zero, Set.of());
        idxDecl.setSourceLocation(forToken.getLocation());

        // condition: $i < arrayLength($arr)
        VariableExpr arrRef = new VariableExpr(arrVar); arrRef.setSourceLocation(forToken.getLocation());
        CallExpr lenCall = new CallExpr(new VariableExpr(syntheticToken("arrayLength", forToken)), List.of(arrRef));
        lenCall.setSourceLocation(forToken.getLocation());
        VariableExpr idxRef = new VariableExpr(idxVar); idxRef.setSourceLocation(forToken.getLocation());
        BinaryExpr cond = new BinaryExpr(idxRef, syntheticToken("<", forToken, TokenType.LESS), lenCall);
        cond.setSourceLocation(forToken.getLocation());

        // Type name = $arr[$i]
        VariableExpr arrRef2 = new VariableExpr(arrVar); arrRef2.setSourceLocation(forToken.getLocation());
        VariableExpr idxRef2 = new VariableExpr(idxVar); idxRef2.setSourceLocation(forToken.getLocation());
        IndexExpr elemAccess = new IndexExpr(arrRef2, idxRef2); elemAccess.setSourceLocation(forToken.getLocation());
        VarDecl elemDecl = new VarDecl(typeToken.getLexeme(), nameToken.getLexeme(), elemAccess, Set.of());
        elemDecl.setSourceLocation(forToken.getLocation());

        // $i = $i + 1
        VariableExpr idxRef3 = new VariableExpr(idxVar); idxRef3.setSourceLocation(forToken.getLocation());
        LiteralExpr one = new LiteralExpr(1L); one.setSourceLocation(forToken.getLocation());
        BinaryExpr incr = new BinaryExpr(idxRef3, syntheticToken("+", forToken, TokenType.PLUS), one);
        incr.setSourceLocation(forToken.getLocation());
        AssignmentExpr incrAssign = new AssignmentExpr(idxVar, incr);
        incrAssign.setSourceLocation(forToken.getLocation());
        ExpressionStmt incrStmt = new ExpressionStmt(incrAssign);
        incrStmt.setSourceLocation(forToken.getLocation());

        // loop body = { Type name = $arr[$i]; <original body>; $i = $i + 1; }
        Block loopBlock = new Block(List.of(elemDecl, body, incrStmt));
        loopBlock.setSourceLocation(forToken.getLocation());
        loopBlock.markAsDesugaredForLoopBody();

        WhileStmt whileStmt = new WhileStmt(cond, loopBlock);
        whileStmt.setSourceLocation(forToken.getLocation());

        Block outerBlock = new Block(List.of(arrDecl, idxDecl, whileStmt));
        outerBlock.setSourceLocation(forToken.getLocation());
        return outerBlock;
    }

    private Token syntheticToken(String lexeme, Token ref) {
        return new Token(TokenType.IDENTIFIER, lexeme, ref.getLine(), ref.getColumn(), ref.getStartOffset(), ref.getEndOffset());
    }

    private Token syntheticToken(String lexeme, Token ref, TokenType type) {
        return new Token(type, lexeme, ref.getLine(), ref.getColumn(), ref.getStartOffset(), ref.getEndOffset());
    }

    /**
     * Desugar switch(expr) { case v1: { ... } case v2: { ... } default: { ... } }
     * →  { sab $sw = expr; if($sw == v1) { ... } else if($sw == v2) { ... } else { ... } }
     * Uses a synthetic temp variable to evaluate the switch expression once.
     */
    private Statement parseSwitchStmt() {
        Token switchToken = previous();
        consume(TokenType.LPAREN, "Expect '(' after 'switch'.");
        Expression switchExpr = parseExpression();
        consume(TokenType.RPAREN, "Expect ')' after switch expression.");
        consume(TokenType.LBRACE, "Expect '{' after switch expression.");

        // Store switch expr in a synthetic variable
        Token swVar = syntheticToken("$switch_val", switchToken);
        // Use "any" type so any value can be compared
        VarDecl swDecl = new VarDecl("any", swVar.getLexeme(), switchExpr, Set.of());
        swDecl.setSourceLocation(switchToken.getLocation());

        List<Expression> caseValues = new ArrayList<>();
        List<Statement> caseBodies = new ArrayList<>();
        Statement defaultBody = null;

        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            if (match(TokenType.CASE)) {
                Expression caseVal = parseExpression();
                consume(TokenType.COLON, "Expect ':' after case value.");
                Statement caseBody = parseCaseBody();
                caseValues.add(caseVal);
                caseBodies.add(caseBody);
            } else if (match(TokenType.DEFAULT)) {
                consume(TokenType.COLON, "Expect ':' after 'default'.");
                defaultBody = parseCaseBody();
            } else {
                throw error(peek(), "Expected 'case' or 'default' in switch statement.");
            }
        }
        consume(TokenType.RBRACE, "Expect '}' after switch body.");

        // Build if-else chain from bottom up
        Statement result = defaultBody;
        for (int i = caseValues.size() - 1; i >= 0; i--) {
            VariableExpr swRef = new VariableExpr(swVar);
            swRef.setSourceLocation(switchToken.getLocation());
            BinaryExpr cond = new BinaryExpr(swRef,
                    syntheticToken("==", switchToken, TokenType.EQUALITY),
                    caseValues.get(i));
            cond.setSourceLocation(switchToken.getLocation());
            IfStmt ifStmt = new IfStmt(cond, caseBodies.get(i), result);
            ifStmt.setSourceLocation(switchToken.getLocation());
            result = ifStmt;
        }

        if (result == null) {
            // Empty switch — just evaluate the expression
            return new ExpressionStmt(switchExpr);
        }

        Block outerBlock = new Block(List.of(swDecl, result));
        outerBlock.setSourceLocation(switchToken.getLocation());
        return outerBlock;
    }

    private Statement parseCaseBody() {
        if (check(TokenType.LBRACE)) {
            return parseBlock();
        }
        // Allow single or multiple statements until next case/default/rbrace
        List<Statement> stmts = new ArrayList<>();
        while (!check(TokenType.CASE) && !check(TokenType.DEFAULT) && !check(TokenType.RBRACE) && !isAtEnd()) {
            stmts.add(parseStatement());
        }
        Block block = new Block(stmts);
        if (!stmts.isEmpty()) {
            block.setSourceLocation(stmts.get(0).getSourceLocation());
        }
        return block;
    }

    /**
     * do { body } while(condition);
     * Desugars to: { body; while(condition) { body } }
     * Actually desugar to a while(true) with condition check at end.
     */
    private Statement parseDoWhile() {
        Token doToken = previous();
        Statement body = parseStatement();
        consume(TokenType.WHILE, "Expect 'while' after do-block.");
        consume(TokenType.LPAREN, "Expect '(' after 'while'.");
        Expression condition = parseExpression();
        consume(TokenType.RPAREN, "Expect ')' after while condition.");
        consume(TokenType.SEMICOLON, "Expect ';' after do-while statement.");

        // Desugar: run body once, then while(condition) body
        // Use: { body; while(condition) { body } }
        // But body may have side effects from variable declarations that can't be duplicated.
        // Better approach: while(true) { body; if(!condition) break; }
        LiteralExpr trueExpr = new LiteralExpr(true);
        trueExpr.setSourceLocation(doToken.getLocation());

        UnaryExpr notCond = new UnaryExpr(syntheticToken("!", doToken, TokenType.NOT), condition);
        notCond.setSourceLocation(doToken.getLocation());
        BreakStmt breakStmt = new BreakStmt();
        breakStmt.setSourceLocation(doToken.getLocation());
        IfStmt breakIf = new IfStmt(notCond, breakStmt, null);
        breakIf.setSourceLocation(doToken.getLocation());

        Block loopBody = new Block(List.of(body, breakIf));
        loopBody.setSourceLocation(doToken.getLocation());

        WhileStmt whileStmt = new WhileStmt(trueExpr, loopBody);
        whileStmt.setSourceLocation(doToken.getLocation());
        return whileStmt;
    }

    private Statement parseLabeledWhile(String label) {
        Statement stmt = parseWhile();
        if (stmt instanceof WhileStmt ws) { ws.setLabel(label); }
        return stmt;
    }

    private Statement parseLabeledFor(String label, Token forToken) {
        Statement stmt = parseFor(forToken);
        // parseFor may return a WhileStmt directly, or a Block wrapping init + WhileStmt
        if (stmt instanceof WhileStmt ws) {
            ws.setLabel(label);
        } else if (stmt instanceof Block blk) {
            for (Statement s : blk.getStatements()) {
                if (s instanceof WhileStmt ws) { ws.setLabel(label); break; }
            }
        }
        return stmt;
    }

    private Statement parseLabeledDoWhile(String label) {
        Statement stmt = parseDoWhile();
        if (stmt instanceof WhileStmt ws) { ws.setLabel(label); }
        return stmt;
    }

    
    private TryStmt parseTryStmt() {
        Token tryToken = previous();
        Block tryBlock = parseBlock();
        
        List<CatchClause> catchClauses = new ArrayList<>();
        while (match(TokenType.CATCH)) {
            Token catchToken = previous();
            consume(TokenType.LPAREN, "Expected '(' after 'catch'.");
            
            // Parse optional exception type and parameter name
            String exceptionType = "any"; // Default to catch all exceptions
            Token parameterName;
            
            if (check(TokenType.IDENTIFIER)) {
                Token firstToken = advance();
                if (check(TokenType.IDENTIFIER)) {
                    
                    Token secondToken = peek();
                    String a = firstToken.getLexeme();
                    String b = secondToken.getLexeme();
                    boolean firstLooksParam = a.length()>0 && Character.isLowerCase(a.charAt(0));
                    boolean secondLooksType = b.length()>0 && Character.isUpperCase(b.charAt(0));
                    if (firstLooksParam && secondLooksType) {
                        parameterName = firstToken; // param first
                        exceptionType = advance().getLexeme();
                    } else {
                        exceptionType = firstToken.getLexeme();
                        parameterName = advance();
                    }
                } else {
                    // Only one identifier -> parameter name, type remains 'any'
                    parameterName = firstToken;
                }
            } else {
                throw error(peek(), "Expected parameter name in catch clause.");
            }
            
            consume(TokenType.RPAREN, "Expected ')' after catch parameter.");
            Block catchBody = parseBlock();
            CatchClause clause = new CatchClause(exceptionType, parameterName.getLexeme(), catchBody);
            clause.setSourceLocation(catchToken.getLocation());
            catchClauses.add(clause);
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
                check(TokenType.IDENTIFIER) ||
                // Blockchain types
                check(TokenType.ADDRESS) || check(TokenType.UINT256) || check(TokenType.INT256) ||
                check(TokenType.BYTES32) || check(TokenType.WEI);

        // Handle mapping type: mapping(KeyType => ValueType)
        if (!isBaseType && check(TokenType.MAPPING)) {
            return true;
        }

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

        // Handle mapping type: mapping(KeyType => ValueType)
        if (baseType.getType() == TokenType.MAPPING && check(TokenType.LPAREN)) {
            StringBuilder mappingType = new StringBuilder(baseType.getLexeme());
            mappingType.append("(");
            advance(); // consume '('
            
            // Consume everything until matching ')'
            int depth = 1;
            while (depth > 0 && !isAtEnd()) {
                Token token = advance();
                if (token.getType() == TokenType.LPAREN) {
                    depth++;
                } else if (token.getType() == TokenType.RPAREN) {
                    depth--;
                    if (depth == 0) break;
                }
                mappingType.append(token.getLexeme());
                // Add space around => for readability
                if (token.getLexeme().equals("=") && check(TokenType.GREATER)) {
                    mappingType.append(">");
                    advance(); // consume '>'
                } else {
                    mappingType.append(" ");
                }
            }
            mappingType.append(")");
            return new Token(TokenType.IDENTIFIER, mappingType.toString(), baseType.getLine());
        }

        if (check(TokenType.LBRACKET)) {
            // Support multiple [] suffixes on types: num[][]
            StringBuilder typeLex = new StringBuilder(baseType.getLexeme());
            while (check(TokenType.LBRACKET)) {
                advance();
                consume(TokenType.RBRACKET, "Expected ']' after '[' in array type.");
                typeLex.append("[]");
            }
            return new Token(baseType.getType(), typeLex.toString(), baseType.getLine());
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

    private boolean checkNext(TokenType type) {
        if (current + 1 >= tokens.size()) return false;
        return tokens.get(current + 1).getType() == type;
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
    
    /**
     * Parse both regular modifiers and contract annotations.
     * Contract annotations (@contract, @storage, @view, etc.) are parsed first,
     * followed by regular modifiers (public, private, static, etc.).
     */
    private ParsedModifiers parseAllModifiers() {
        Set<Modifier> modifiers = new HashSet<>();
        Set<ContractAnnotation> contractAnnotations = EnumSet.noneOf(ContractAnnotation.class);
        
        // Parse contract annotations first
        while (match(TokenType.CONTRACT, TokenType.STORAGE, TokenType.VIEW, TokenType.PURE, 
                     TokenType.PAYABLE, TokenType.NONREENTRANT, TokenType.CONSTRUCTOR,
                     TokenType.EVENT, TokenType.ERROR, TokenType.IMMUTABLE, TokenType.INVARIANT)) {
            TokenType tokenType = previous().getType();
            ContractAnnotation annotation = ContractAnnotation.fromTokenType(tokenType);
            if (contractAnnotations.contains(annotation)) {
                throw error(previous(), "Duplicate contract annotation: " + annotation);
            }
            contractAnnotations.add(annotation);
        }
        
        // Parse @Override annotation
        if (match(TokenType.OVERRIDE)) {
            modifiers.add(Modifier.OVERRIDE);
        }
        
        // Parse regular modifiers
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
        
        return new ParsedModifiers(modifiers, contractAnnotations);
    }
    
    /**
     * Legacy method for backward compatibility - returns only modifiers, ignores contract annotations.
     */
    private Set<Modifier> parseModifiers() {
        return parseAllModifiers().modifiers;
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