package dhrlang.ast;

public interface ASTVisitor<R> {
    R visitProgram(Program program);
    R visitClassDecl(ClassDecl classDecl);
    R visitInterfaceDecl(InterfaceDecl interfaceDecl);
    R visitFunctionDecl(FunctionDecl functionDecl);
    R visitVarDecl(VarDecl varDecl);

    R visitBlock(Block block);
    R visitReturnStmt(ReturnStmt returnStmt);
    R visitPrintStmt(PrintStmt printStmt);
    R visitExpressionStmt(ExpressionStmt expressionStmt);
    R visitIfStmt(IfStmt ifStmt);
    R visitWhileStmt(WhileStmt whileStmt);
    R visitBreakStmt(BreakStmt breakStmt);
    R visitContinueStmt(ContinueStmt continueStmt);

    R visitTryStmt(TryStmt tryStmt);
    R visitCatchClause(CatchClause catchClause);
    R visitThrowStmt(ThrowStmt throwStmt);

    R visitBinaryExpr(BinaryExpr binaryExpr);
    R visitUnaryExpr(UnaryExpr unaryExpr);
    R visitLiteralExpr(LiteralExpr literalExpr);
    R visitVariableExpr(VariableExpr variableExpr);
    R visitAssignmentExpr(AssignmentExpr assignmentExpr);
    R visitCallExpr(CallExpr callExpr);
    R visitGetExpr(GetExpr getExpr);
    R visitSetExpr(SetExpr setExpr);
    R visitThisExpr(ThisExpr thisExpr);
    R visitSuperExpr(SuperExpr superExpr);
    R visitNewExpr(NewExpr newExpr);
    R visitNewArrayExpr(NewArrayExpr newArrayExpr);
    R visitArrayExpr(ArrayExpr arrayExpr);
    R visitIndexExpr(IndexExpr indexExpr);
    R visitIndexAssignExpr(IndexAssignExpr indexAssignExpr);
    R visitPrefixIncrementExpr(PrefixIncrementExpr prefixIncrementExpr);
    R visitPostfixIncrementExpr(PostfixIncrementExpr postfixIncrementExpr);
    R visitStaticAccessExpr(StaticAccessExpr staticAccessExpr);
    R visitStaticAssignExpr(StaticAssignExpr staticAssignExpr);
}
