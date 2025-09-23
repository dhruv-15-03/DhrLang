package dhrlang.typechecker;
import java.util.*;
public final class TypeDesc {
    public final TypeKind kind;
    public final String name;
    public final TypeDesc element;
    // For generic/class types with type arguments: base name in name, args stored separately
    public final List<TypeDesc> typeArgs;
    private TypeDesc(TypeKind k,String n,TypeDesc e){this(k,n,e,Collections.emptyList());}
    private TypeDesc(TypeKind k,String n,TypeDesc e,List<TypeDesc> args){this.kind=k;this.name=n;this.element=e;this.typeArgs=args;}
    public static TypeDesc num(){return new TypeDesc(TypeKind.NUM,"num",null);}    
    public static TypeDesc duo(){return new TypeDesc(TypeKind.DUO,"duo",null);}    
    public static TypeDesc sab(){return new TypeDesc(TypeKind.SAB,"sab",null);}    
    public static TypeDesc kya(){return new TypeDesc(TypeKind.KYA,"kya",null);}    
    public static TypeDesc kaam(){return new TypeDesc(TypeKind.KAAM,"kaam",null);}    
    public static TypeDesc any(){return new TypeDesc(TypeKind.ANY,"any",null);}    
    public static TypeDesc nul(){return new TypeDesc(TypeKind.NULL,"null",null);}    
    public static TypeDesc cls(String n){return new TypeDesc(TypeKind.CLASS,n,null);}    
    public static TypeDesc cls(String n,List<TypeDesc> args){return new TypeDesc(TypeKind.CLASS,n,null,args);}    
    public static TypeDesc generic(String n){return new TypeDesc(TypeKind.GENERIC,n,null);}    
    public static TypeDesc array(TypeDesc el){return new TypeDesc(TypeKind.ARRAY,el.name+"[]",el);}    
    public static TypeDesc unknown(){return new TypeDesc(TypeKind.UNKNOWN,"unknown",null);}    
    public static TypeDesc parse(String raw){
        if(raw==null) return unknown();
        raw = raw.trim();
    if(raw.equals("_")) return new TypeDesc(TypeKind.WILDCARD,"_",null);
        if(raw.endsWith("[]")) return array(parse(raw.substring(0,raw.length()-2)));
        int lt = raw.indexOf('<');
        if(lt>0 && raw.endsWith(">")){
            String base = raw.substring(0,lt);
            String inner = raw.substring(lt+1, raw.length()-1).trim();
            List<TypeDesc> args = new ArrayList<>();
            if(!inner.isEmpty()){
                int depth=0; StringBuilder current=new StringBuilder();
                for(char c: inner.toCharArray()){
                    if(c=='<' ) { depth++; current.append(c); }
                    else if(c=='>' ){ depth--; current.append(c); }
                    else if(c==',' && depth==0){ args.add(parse(current.toString().trim())); current.setLength(0); }
                    else current.append(c);
                }
                if(current.length()>0) args.add(parse(current.toString().trim()));
            }
            if(base.matches("[A-Z][A-Za-z0-9_]*")) return cls(base,args);
        }
        return switch(raw){
            case "num"->num(); case "duo"->duo(); case "sab"->sab(); case "kya"->kya(); case "kaam"->kaam(); case "any"->any(); case "null"->nul();
            default -> { if(raw.matches("[A-Z][A-Za-z0-9_]*")) yield cls(raw); if(raw.matches("[A-Z]")) yield generic(raw); yield unknown(); }
        };
    }
    public boolean isNumeric(){ return kind==TypeKind.NUM || kind==TypeKind.DUO; }
    public boolean isArray(){ return kind==TypeKind.ARRAY; }
    public static boolean assignable(TypeDesc from, TypeDesc to){
        if(from==null||to==null) return false;
        if(from==to || from.equals(to)) return true;
        if(from.kind==TypeKind.ANY || to.kind==TypeKind.ANY) return true;
        if(from.kind==TypeKind.NUM && to.kind==TypeKind.DUO) return true;
        if(from.isArray() && from.element!=null && from.element.kind==TypeKind.UNKNOWN && to.isArray()) return true;
        if(from.isArray() && to.isArray()) return assignable(from.element, to.element);
        if(from.kind==TypeKind.CLASS && to.kind==TypeKind.CLASS){
            if(!from.name.equals(to.name)) return false;
            if(from.typeArgs.size()!=to.typeArgs.size()) return false;
            for(int i=0;i<from.typeArgs.size();i++){
                TypeDesc fa=from.typeArgs.get(i); TypeDesc ta=to.typeArgs.get(i);
                if(ta.kind==TypeKind.WILDCARD || fa.kind==TypeKind.WILDCARD) continue; // wildcard matches anything at this position
                if(!fa.equals(ta)) return false; 
            }
            return true;
        }
        if(from.kind==TypeKind.GENERIC && to.kind==TypeKind.GENERIC){
            return from.name.equals(to.name);
        }
        // Wildcard assignment at top-level generic or class position
        if(to.kind==TypeKind.WILDCARD || from.kind==TypeKind.WILDCARD) return true;
        return false;
    }
    @Override public String toString(){
        // Reconstruct full type name including generics and arrays
        if(kind==TypeKind.ARRAY){
            return (element!=null? element.toString(): "unknown") + "[]";
        }
        if(kind==TypeKind.CLASS && typeArgs!=null && !typeArgs.isEmpty()){
            StringBuilder sb = new StringBuilder(name);
            sb.append("<");
            for(int i=0;i<typeArgs.size();i++){
                if(i>0) sb.append(", ");
                sb.append(typeArgs.get(i).toString());
            }
            sb.append(">");
            return sb.toString();
        }
        return name;
    }
    @Override public boolean equals(Object o){ if(this==o)return true; if(!(o instanceof TypeDesc t))return false; return kind==t.kind && Objects.equals(name,t.name) && Objects.equals(element,t.element) && Objects.equals(typeArgs,t.typeArgs);} 
    @Override public int hashCode(){return Objects.hash(kind,name,element,typeArgs);} 
}
