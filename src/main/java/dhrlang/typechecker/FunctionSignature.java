package dhrlang.typechecker;

import java.util.List;
import java.util.stream.Collectors;

public class FunctionSignature {
    private final List<TypeDesc> parameterTypes;
    private final TypeDesc returnType;

    public FunctionSignature(List<String> parameterTypes, String returnType) {
        this.parameterTypes = parameterTypes.stream().map(TypeDesc::parse).collect(Collectors.toList());
        this.returnType = TypeDesc.parse(returnType);
    }

    public FunctionSignature(List<TypeDesc> parameterTypes, TypeDesc returnType, boolean alreadyTyped) {
        this.parameterTypes = parameterTypes; this.returnType = returnType; }

    public List<String> getParameterTypes() { return parameterTypes.stream().map(TypeDesc::toString).collect(Collectors.toList()); }
    public List<TypeDesc> getParameterTypeDescs(){ return parameterTypes; }
    public String getReturnType() { return returnType.toString(); }
    public TypeDesc getReturnTypeDesc(){ return returnType; }
}
