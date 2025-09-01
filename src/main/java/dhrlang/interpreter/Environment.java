package dhrlang.interpreter;

import dhrlang.error.ErrorFactory;
import java.util.HashMap;
import java.util.Map;

public class Environment {

    private final Map<String, Object> values = new HashMap<>();
    private final Environment parent;

    public Environment() {
        this.parent = null;
    }

    public Environment(Environment parent) {
        this.parent = parent;
    }

    public void define(String name, Object value) {
        values.put(name, value);
    }

    public Object get(String name) {
        if (values.containsKey(name)) {
            return values.get(name);
        } else if (parent != null) {
            return parent.get(name);
        } else {
            // Check if this looks like a generic type reference or provide suggestion
            String suggestion = suggest(name);
            String errorMessage = "Undefined variable '" + name + "'." + (suggestion!=null? (" Did you mean '"+suggestion+"'?") : "");
            String hint = "Did you mean to access it with 'this." + name + "'?";
            
            if (name.contains("<") && name.contains(">")) {
                errorMessage = "Cannot use generic type '" + name + "' as a variable.";
                hint = "Generic types like '" + name + "' are used for type declarations, not as variables. " +
                       "Use 'new " + name + "(...)' to create an instance.";
            }
            
            throw ErrorFactory.accessError(errorMessage + " " + hint, (dhrlang.error.SourceLocation) null);
        }
    }

    public void assign(String name, Object value) {
        if (values.containsKey(name)) {
            values.put(name, value);
        } else if (parent != null) {
            parent.assign(name, value);
        } else {
            // Check if this looks like a generic type reference or provide suggestion
            String suggestion = suggest(name);
            String errorMessage = "Cannot assign to undefined variable '" + name + "'." + (suggestion!=null? (" Did you mean '"+suggestion+"'?") : "");
            String hint = "Did you mean to access it with 'this." + name + "'?";
            
            if (name.contains("<") && name.contains(">")) {
                errorMessage = "Cannot assign to generic type '" + name + "'.";
                hint = "Generic types like '" + name + "' are type declarations, not variables. " +
                       "Declare a variable: " + name + " myVar = new " + name + "(...);";
            }
            
            throw ErrorFactory.accessError(errorMessage + " " + hint, (dhrlang.error.SourceLocation) null);
        }
    }

    public boolean exists(String name) {
        return values.containsKey(name) || (parent != null && parent.exists(name));
    }

    private String suggest(String miss){
        int best = Integer.MAX_VALUE; String bestName = null;
        for(String k: values.keySet()){
            int d = levenshtein(miss, k);
            if(d<best){ best=d; bestName=k; }
        }
        if(parent!=null){ String enc = parent.suggest(miss); if(enc!=null){ int d=levenshtein(miss, enc); if(d<best){ best=d; bestName=enc; } } }
        return best <= 2 ? bestName : null;
    }
    private int levenshtein(String a,String b){
        int[][] dp = new int[a.length()+1][b.length()+1];
        for(int i=0;i<=a.length();i++) dp[i][0]=i;
        for(int j=0;j<=b.length();j++) dp[0][j]=j;
        for(int i=1;i<=a.length();i++) for(int j=1;j<=b.length();j++){
            int cost = a.charAt(i-1)==b.charAt(j-1)?0:1;
            dp[i][j] = Math.min(Math.min(dp[i-1][j]+1, dp[i][j-1]+1), dp[i-1][j-1]+cost);
        }
        return dp[a.length()][b.length()];
    }
}
