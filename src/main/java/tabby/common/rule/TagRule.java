package tabby.common.rule;

import lombok.Data;

import java.util.Set;

/**
 * @author wh1t3p1g
 * @since 2023/3/15
 */
@Data
public class TagRule {

    private String name;
    private String type;
    private String value;
    private Set<String> annotations;
    private Set<String> classes;
    private Set<String> methods; // sub_signature
    private Set<String> whitelist; // class 用于排除某些特殊的class

    public boolean isAnnotationType(){
        return type.contains("annotation");
    }

    public boolean isClassType(){
        return type.contains("class");
    }

    public boolean isMethodType(){
        return type.contains("method");
    }

    public boolean isInWhitelist(String classname){
        return whitelist != null && whitelist.contains(classname);
    }

    public void addClasses(Set<String> data){
        classes.addAll(data);
    }

    public void addMethods(Set<String> data){
        methods.addAll(data);
    }
}
