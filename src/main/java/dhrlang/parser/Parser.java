package dhrlang.parser;

import dhrlang.ast.*;
import dhrlang.lexer.Token;
import dhrlang.lexer.TokenType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

public class Parser {

    private final List<Token> tokens;
    private int current = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public Program parse() {
        System.out.println("Parser: Starting to parse program");
        System.out.println("Parser: Current token: " + peek().getType() + " '" + peek().getLexeme() + "'");
        List<ClassDecl> classes = new ArrayList<>();

        try {
            while (!isAtEnd()) {
                classes.add(parseClassDecl());
            }
            return new Program(classes);
        } catch (ParseException e) {
            System.err.println("Parse error at line " + e.getLine() + ": " + e.getMessage());
            System.err.println("Current token: " + peek().getType() + " '" + peek().getLexeme() + "'");
            throw e;
        }

    }

    private ClassDecl parseClassDecl() {
        consume(TokenType.CLASS, "Expected 'class' keyword to start a class declaration.");
        Token name = consume(TokenType.IDENTIFIER, "Expected class name after 'class'.");
        VariableExpr superclass = null;
        if (match(TokenType.EXTENDS)) {
            consume(TokenType.IDENTIFIER, "Expected superclass name.");
            superclass = new VariableExpr(previous());
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
        return new ClassDecl(name.getLexeme(), superclass, functions, variables);
    }

    private Expression parseCallDot() {
        Expression expr = parsePrimary();
        while (true) {
            if (match(TokenType.LPAREN)) {
                expr = parseCallArguments(expr);
            } else if (match(TokenType.DOT)) {
                Token name = consume(TokenType.IDENTIFIER, "Expect property name after '.'.");
                
                if (expr instanceof VariableExpr && isClassName(((VariableExpr) expr).getName().getLexeme())) {
                    expr = new StaticAccessExpr((VariableExpr) expr, name);
                } else {
                    expr = new GetExpr(expr, name);
                }
            }
            else if (match(TokenType.LBRACKET)) {
                Expression index = parseExpression();
                consume(TokenType.RBRACKET, "Expected ']' after array index.");
                expr = new IndexExpr(expr, index);
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
        Block body = parseBlock();
        return new FunctionDecl(returnType.getLexeme(), name.getLexeme(), parameters, body, modifiers);
    }

    private VarDecl parseParameter() {
        Token type = consumeType("Expected type in parameter declaration.");
        Token name = consume(TokenType.IDENTIFIER, "Expected parameter name.");
        return new VarDecl(type.getLexeme(), name.getLexeme(), null);
    }

    private VarDecl parseVarDecl(Token type, Token name) {
        Expression initializer = null;
        if (match(TokenType.ASSIGN)) {
            initializer = parseExpression();
        }
        consume(TokenType.SEMICOLON, "Expected ';' after variable declaration.");
        return new VarDecl(type.getLexeme(), name.getLexeme(), initializer);
    }
    
    private VarDecl parseVarDecl(Token type, Token name, Set<Modifier> modifiers) {
        Expression initializer = null;
        if (match(TokenType.ASSIGN)) {
            initializer = parseExpression();
        }
        consume(TokenType.SEMICOLON, "Expected ';' after variable declaration.");
        return new VarDecl(type.getLexeme(), name.getLexeme(), initializer, modifiers);
    }

    private Block parseBlock() {
        consume(TokenType.LBRACE, "Expected '{' to start block.");
        List<Statement> statements = new ArrayList<>();
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            statements.add(parseStatement());
        }
        consume(TokenType.RBRACE, "Expected '}' after block.");
        return new Block(statements);
    }

    private Expression parseCallArguments(Expression callee) {
        List<Expression> arguments = new ArrayList<>();
        if (!check(TokenType.RPAREN)) {
            do {
                arguments.add(parseExpression());
            } while (match(TokenType.COMMA));
        }
        consume(TokenType.RPAREN, "Expect ')' after arguments.");
        return new CallExpr(callee, arguments);
    }

    private Statement parseStatement() {
        if (match(TokenType.BREAK)) {
            consume(TokenType.SEMICOLON, "Expected ';' after 'break'.");
            return new BreakStmt();
        }
        if (match(TokenType.CONTINUE)) {
            consume(TokenType.SEMICOLON, "Expected ';' after 'continue'.");
            return new ContinueStmt();
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
            return parseFor();
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
        if (lookAhead < tokens.size() && tokens.get(lookAhead).getType() == TokenType.LBRACKET) {
            lookAhead++;
            if (lookAhead < tokens.size() && tokens.get(lookAhead).getType() == TokenType.RBRACKET) {
                lookAhead++;
            } else {
                return false;
            }
        }


        if (lookAhead < tokens.size() && tokens.get(lookAhead).getType() == TokenType.IDENTIFIER) {
            return true;
        }

        return false;

    }

    private ReturnStmt parseReturnStmt() {
        Expression value = null;
        if (!check(TokenType.SEMICOLON)) {
            value = parseExpression();
        }
        consume(TokenType.SEMICOLON, "Expected ';' after return statement.");
        return new ReturnStmt(value);
    }



    private Statement parseExpressionStmt() {
        Expression expr = parseExpression();
        consume(TokenType.SEMICOLON, "Expected ';' after expression.");
        return new ExpressionStmt(expr);
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
                return new AssignmentExpr(name, value);
            } else if (expr instanceof GetExpr getExpr) {
                return new SetExpr(getExpr.getObject(), getExpr.getName(), value);
            } else if (expr instanceof IndexExpr indexExpr) {
                return new IndexAssignExpr(indexExpr.getObject(), indexExpr.getIndex(), value);
            } else if (expr instanceof StaticAccessExpr staticExpr) {
                return new StaticAssignExpr(staticExpr.className, staticExpr.memberName, value);
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
            expr = new BinaryExpr(expr, operator, right);
        }
        return expr;
    }

    private Expression parseLogicalAnd() {
        Expression expr = parseEquality();
        while (match(TokenType.AND)) {
            Token operator = previous();
            Expression right = parseEquality();
            expr = new BinaryExpr(expr, operator, right);
        }
        return expr;
    }

    private Expression parseEquality() {
        Expression expr = parseComparison();
        while (match(TokenType.EQUALITY, TokenType.NEQ)) {
            Token operator = previous();
            Expression right = parseComparison();
            expr = new BinaryExpr(expr, operator, right);
        }
        return expr;
    }

    private Expression parseComparison() {
        Expression expr = parseTerm();
        while (match(TokenType.LESS, TokenType.LEQ, TokenType.GREATER, TokenType.GEQ)) {
            Token operator = previous();
            Expression right = parseTerm();
            expr = new BinaryExpr(expr, operator, right);
        }
        return expr;
    }

    private Expression parseTerm() {
        Expression expr = parseFactor();
        while (match(TokenType.PLUS, TokenType.MINUS)) {
            Token operator = previous();
            Expression right = parseFactor();
            expr = new BinaryExpr(expr, operator, right);
        }
        return expr;
    }

    private Expression parseFactor() {
        Expression expr = parseUnary();
        while (match(TokenType.STAR, TokenType.SLASH, TokenType.MOD)) {
            Token operator = previous();
            Expression right = parseUnary();
            expr = new BinaryExpr(expr, operator, right);
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

            return new PrefixIncrementExpr(operator, target);
        }

        if (match(TokenType.MINUS, TokenType.NOT)) {
            Token operator = previous();
            Expression right = parseUnary();
            return new UnaryExpr(operator, right);
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

            return new PostfixIncrementExpr(expr, operator);
        }

        return expr;

    }
    private Expression arrayLiteral() {
        List<Expression> elements = new ArrayList<>();

        if (!check(TokenType.RBRACKET)) {
            do {
                elements.add(parseExpression());
            } while (match(TokenType.COMMA));
        }

        consume(TokenType.RBRACKET, "Expected ']' after array elements.");
        return new ArrayExpr(elements);
    }


    private Expression parsePrimary() {
        if (match(TokenType.NUMBER)) {
            String numberString = previous().getLexeme();
            if (numberString.contains(".")) {
                return new LiteralExpr(Double.parseDouble(numberString));
            } else {
                return new LiteralExpr(Long.parseLong(numberString));
            }
        }
        if (match(TokenType.STRING)) {
            return new LiteralExpr(previous().getLexeme());
        }
        if (match(TokenType.CHAR)) {
            return new LiteralExpr(previous().getLexeme().charAt(0));
        }
        if (match(TokenType.BOOLEAN)) {
            return new LiteralExpr(Boolean.parseBoolean(previous().getLexeme()));
        }
        if (match(TokenType.LBRACKET)) {
            return arrayLiteral();
        }

        if (match(TokenType.NEW)) {
            if (check(TokenType.NUM) || check(TokenType.DUO) || check(TokenType.EK) || 
                check(TokenType.SAB) || check(TokenType.KYA)) {
                Token typeToken = advance(); 
                consume(TokenType.LBRACKET, "Expected '[' after type for array creation.");
                Expression size = parseExpression();
                consume(TokenType.RBRACKET, "Expected ']' after array size.");
                return new NewArrayExpr(typeToken.getLexeme(), size);
            } else {
                Token classNameToken = consume(TokenType.IDENTIFIER, "Expect class name after 'new'.");
                consume(TokenType.LPAREN, "Expect '(' after class name.");
                List<Expression> arguments = new ArrayList<>();
                if (!check(TokenType.RPAREN)) {
                    do {
                        arguments.add(parseExpression());
                    } while (match(TokenType.COMMA));
                }
                consume(TokenType.RPAREN, "Expect ')' after arguments.");
                return new NewExpr(classNameToken.getLexeme(), arguments);
            }
        }
        if (match(TokenType.IDENTIFIER)) {
            return new VariableExpr(previous());
        }
        if (match(TokenType.SUPER)) {
            Token keyword = previous();
            consume(TokenType.DOT, "Expect '.' after 'super'.");
            Token method = consume(TokenType.IDENTIFIER, "Expect superclass method name.");
            return new SuperExpr(keyword, method);
        }
        if (match(TokenType.THIS)) {
            return new ThisExpr(previous());
        }
        if (match(TokenType.LPAREN)) {
            Expression expr = parseExpression();
            consume(TokenType.RPAREN, "Expected ')' after expression.");
            return expr;
        }

        throw error(peek(), "Expected expression.");
    }

    private Statement parseIf() {
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
        return new IfStmt(condition, thenBranch, elseBranch);
    }

    private Statement parseWhile() {
        consume(TokenType.LPAREN, "Expect '(' after 'while'.");
        Expression condition = parseExpression();
        consume(TokenType.RPAREN, "Expect ')' after while condition.");
        Statement body = parseStatement();
        return new WhileStmt(condition, body);
    }

    private Statement parseFor() {
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
        }
        
        if (increment != null) {
            List<Statement> loopStatements = new ArrayList<>();
            loopStatements.add(body);
            loopStatements.add(new ExpressionStmt(increment));
            Block loopBlock = new Block(loopStatements);
            
            Statement whileStmt = new WhileStmt(condition, loopBlock);
            if (initializer != null) {
                return new Block(List.of(initializer, whileStmt));
            }
            return whileStmt;
        } else {
            // Simple while loop without increment
            Statement whileStmt = new WhileStmt(condition, body);
            if (initializer != null) {
                return new Block(List.of(initializer, whileStmt));
            }
            return whileStmt;
        }
    }

    
    private TryStmt parseTryStmt() {
        Block tryBlock = parseBlock();
        
        List<CatchClause> catchClauses = new ArrayList<>();
        while (match(TokenType.CATCH)) {
            consume(TokenType.LPAREN, "Expected '(' after 'catch'.");
            Token parameterName = consume(TokenType.IDENTIFIER, "Expected parameter name in catch clause.");
            consume(TokenType.RPAREN, "Expected ')' after catch parameter.");
            Block catchBody = parseBlock();
            catchClauses.add(new CatchClause(parameterName.getLexeme(), catchBody));
        }
        
        Block finallyBlock = null;
        if (match(TokenType.FINALLY)) {
            finallyBlock = parseBlock();
        }
        
        if (catchClauses.isEmpty() && finallyBlock == null) {
            throw error(previous(), "A try statement must have at least one catch or finally clause.");
        }
        
        return new TryStmt(tryBlock, catchClauses, finallyBlock);
    }
    
    private ThrowStmt parseThrowStmt() {
        Token throwToken = previous(); // Capture the 'throw' token for location
        Expression value = parseExpression();
        consume(TokenType.SEMICOLON, "Expected ';' after throw statement.");
        return new ThrowStmt(value, throwToken);
    }

    private boolean checkType() {
        boolean isBaseType = check(TokenType.NUM) || check(TokenType.DUO) || check(TokenType.EK) ||
                check(TokenType.SAB) || check(TokenType.KYA) || check(TokenType.KAAM) ||
                check(TokenType.IDENTIFIER);

        if (!isBaseType) return false;
        if (current + 1 < tokens.size() && tokens.get(current + 1).getType() == TokenType.LBRACKET) {
            return current + 2 < tokens.size() && tokens.get(current + 2).getType() == TokenType.RBRACKET;
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
        while (match(TokenType.PUBLIC, TokenType.PRIVATE, TokenType.PROTECTED, TokenType.STATIC)) {
            TokenType tokenType = previous().getType();
            Modifier modifier = Modifier.fromTokenType(tokenType);
            if (modifiers.contains(modifier)) {
                throw error(previous(), "Duplicate modifier: " + modifier);
            }
            modifiers.add(modifier);
        }
        return modifiers;
    }

    private ParseException error(Token token, String message) {
        return new ParseException(message, token);
    }
}