package dhrlang.runtime;

import dhrlang.error.ErrorFactory;
import dhrlang.interpreter.DhrClass;
import dhrlang.interpreter.Interpreter;

/** Centralizes access modifier validation for fields & methods. */
public final class AccessController {
    private AccessController() {}

    public static void assertCanAccess(Interpreter interpreter, DhrClass declaring, String memberName, boolean isField, boolean isStatic, Object instanceContext, dhrlang.ast.Expression accessExpr) {
        String currentClass = interpreter.currentClassContext();
        boolean isPrivate = isField ? declaring.isFieldPrivate(memberName) : declaring.isMethodPrivate(memberName);
        boolean isProtected = isField ? declaring.isFieldProtected(memberName) : declaring.isMethodProtected(memberName);

        if (isPrivate && (currentClass == null || !currentClass.equals(declaring.getName()))) {
            throw ErrorFactory.accessError("Cannot access private " + (isField?"field":"method") + " '" + memberName + "' of class '" + declaring.getName() + "' from outside its declaring class.", dhrlang.error.ErrorFactory.getLocation(accessExpr));
        }
        if (isProtected) {
            boolean allowed = false;
            if (currentClass != null) {
                if (currentClass.equals(declaring.getName())) {
                    allowed = true;
                } else if (isSubclassOf(interpreter, currentClass, declaring.getName())) {
                    allowed = true;
                }
            }
            if (!allowed) {
                throw ErrorFactory.accessError("Cannot access protected " + (isField?"field":"method") + " '" + memberName + "' of class '" + declaring.getName() + "' from outside its package or subclass.", dhrlang.error.ErrorFactory.getLocation(accessExpr));
            }
        }
    }

    private static boolean isSubclassOf(Interpreter interpreter, String potentialSubclass, String potentialSuperclass) {
        Object maybeSubclass = interpreter.getGlobals().get(potentialSubclass);
        Object maybeSuperclass = interpreter.getGlobals().get(potentialSuperclass);
        if (!(maybeSubclass instanceof DhrClass sub) || !(maybeSuperclass instanceof DhrClass sup)) return false;
        return sub.isSubclassOf(sup);
    }
}
