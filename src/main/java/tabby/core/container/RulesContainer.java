package tabby.core.container;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tabby.common.bean.ref.ClassReference;
import tabby.common.bean.ref.MethodReference;
import tabby.common.rule.TabbyRule;
import tabby.common.rule.TagRule;
import tabby.common.utils.FileUtils;
import tabby.common.utils.SemanticUtils;
import tabby.config.GlobalConfiguration;

import java.io.FileNotFoundException;
import java.util.*;

/**
 * @author wh1t3P1g
 * @since 2020/11/20
 */
@Slf4j
@Data
@Component
public class RulesContainer {

    private Map<String, TabbyRule> rules = new HashMap<>();
    private List<String> ignored; // 已经分析过的jar包
    private List<String> excludedClasses; // 不进行分析的类
    private List<String> basicClasses; // 已经分析过的jar包
    private List<String> commonJars;
    private TagRule[] tagRules;
    private Map<String, TagRule> tagRuleMap = new HashMap<>();


    public RulesContainer() throws FileNotFoundException {
        load();
        loadIgnore();
        loadTagRules();
        loadBasicClasses();
        loadCommonJars();
    }

    public TabbyRule.Rule getRule(String classname, String method){
        if(rules.containsKey(classname)){
            TabbyRule rule = rules.get(classname);
            if(rule.contains(method)){
                return rule.getRule(method);
            }
        }
        return null;
    }

    public void applyRule(ClassReference clsRef, MethodReference methodRef){
        String classname = clsRef.getName();
        TabbyRule.Rule rule = getRule(classname, methodRef.getName());
        Set<String> relatedClassnames = SemanticUtils.getAllFatherNodes(classname);

        if (rule == null) { // 对于ignore类型，支持多级父类和接口的规则查找
            for (String relatedClassname : relatedClassnames) {
                TabbyRule.Rule tmpRule = getRule(relatedClassname, methodRef.getName());
                if (tmpRule != null && tmpRule.isIgnore()) {
                    rule = tmpRule;
                    break;
                }
            }
        }
        boolean isSink = false;
        boolean isIgnore = false;
        boolean isSource = false;
        if(rule != null && (rule.isEmptySignaturesList() || rule.isContainsSignature(methodRef.getSignature()))){
            // 当rule存在signatures时，该rule为精确匹配，否则为模糊匹配，仅匹配函数名是否符合
            isSink = rule.isSink();
            isIgnore = rule.isIgnore();
            isSource = rule.isSource();

            // 此处，对于sink、know、ignore类型的规则，直接选取先验知识
            // 对于source类型 不赋予其actions和polluted
            if (!isSource) {
                Map<String, String> actions = rule.getActions();
                List<Integer> polluted = rule.getPolluted();
                if(isSink){
                    methodRef.setVul(rule.getVul());
                }
                methodRef.setActions(actions!=null?actions:new HashMap<>());
                methodRef.setPollutedPosition(polluted!=null?polluted:new ArrayList<>());
                methodRef.setActionInitialed(true);
                if(isIgnore){// 不构建ignore的类型
                    methodRef.setInitialed(true);
                }
            }
        }

        methodRef.setSink(isSink);
        methodRef.setIgnore(isIgnore);
        methodRef.setSource(isSource);
        methodRef.setGetter(isGetter(methodRef));
        methodRef.setSetter(isSetter(methodRef));
        methodRef.setSerializable(relatedClassnames.contains("java.io.Serializable"));
        methodRef.setHasDefaultConstructor(clsRef.isHasDefaultConstructor());
        methodRef.setFromAbstractClass(clsRef.isAbstract());
    }

    public void applyTagRule(ClassReference clsRef, MethodReference methodRef){
        // 根据tags规则处理
        String classname = clsRef.getName();
        for(TagRule tagRule:tagRules){
            boolean flag = false;
            if(tagRule.isInWhitelist(classname)){
                break;
            }

            if(tagRule.isAnnotationType()){
                Set<String> annotations = methodRef.getAnnotations().keySet();
                for(String annotation:annotations){
                    flag = check(annotation, tagRule.getAnnotations());
                    if(flag){
                        break;
                    }
                }
                if(!flag) continue;
            }

            if(tagRule.isClassType()){
                flag = check(classname, tagRule.getClasses());
                if(!flag){
                    Set<String> relatedClassnames = SemanticUtils.getAllFatherNodes(classname);
                    for(String related:relatedClassnames){
                        flag = check(related, tagRule.getClasses());
                        if(flag) break;
                    }
                }
                if(!flag) continue;
            }

            if(tagRule.isMethodType()){
                flag = check(methodRef.getName(), tagRule.getMethods());
                if(!flag) continue;
            }

            if(flag){ // annotation && class && method
                methodRef.setType(tagRule.getValue()); // 可能不止命中一次规则

                if(methodRef.isEndpoint()){
                    Set<String> baseUrlPaths = SemanticUtils.getHttpUrlPaths(clsRef.getAnnotations());
                    String urlPath = SemanticUtils.getHttpUrlPathWithBaseURLPaths(methodRef.getAnnotations(), baseUrlPaths);
                    methodRef.setUrlPath(urlPath);
                }
            }
        }
    }

    public static boolean isGetter(MethodReference methodRef) {
        String methodName = methodRef.getName();
        String returnType = methodRef.getReturnType();
        boolean noParameter = methodRef.getParameterSize() == 0;
        boolean isPublic = methodRef.isPublic();

        if (!noParameter || !isPublic) return false;

        if (methodName.startsWith("get") && methodName.length() > 3) {
            return !returnType.contains("void");
        } else if (methodName.startsWith("is") && methodName.length() > 2) {
            return returnType.contains("boolean");
        }

        return false;
    }

    public static boolean isSetter(MethodReference methodRef) {
        String methodName = methodRef.getName();
        String returnType = methodRef.getReturnType();
        boolean singleParameter = methodRef.getParameterSize() == 1;
        boolean isPublic = methodRef.isPublic();

        if (!isPublic || !singleParameter) return false;

        if (methodName.startsWith("set") && methodName.length() > 3) {
            return returnType.contains("void");
        }

        return false;
    }

    public boolean check(String data, Set<String> rules){
        boolean flag = false;
        for(String rule:rules){
            if(rule.startsWith("%")){
                String tmp = rule.substring(1);
                flag = data.endsWith(tmp);
            }else if(rule.endsWith("%")){
                String tmp = rule.substring(0, rule.length()-1);
                flag = data.startsWith(tmp);
            }else{
                flag = data.equals(rule);
            }
            if(flag) return true;
        }
        return false;
    }

    public TabbyRule getRule(String classname){
        return rules.getOrDefault(classname, null);
    }

    public boolean isIgnore(String jar){
        return ignored.contains(jar);
    }

    public boolean isType(String classname, String method, String type){
        if(rules.containsKey(classname)){
            TabbyRule rule = rules.get(classname);
            if(rule.contains(method)){
                TabbyRule.Rule tr = rule.getRule(method);
                return type.equals(tr.getType());
            }
        }
        return false;
    }

    @SuppressWarnings({"unchecked"})
    private void load() throws FileNotFoundException {
        List<TabbyRule> tabbyRules = new ArrayList<>();
        TabbyRule[] sinkRules = (TabbyRule[]) FileUtils.getJsonContent(GlobalConfiguration.SINK_RULE_PATH, TabbyRule[].class);
        TabbyRule[] systemRules = (TabbyRule[]) FileUtils.getJsonContent(GlobalConfiguration.SYSTEM_RULE_PATH, TabbyRule[].class);
        if(sinkRules == null || systemRules == null){
            throw new FileNotFoundException("File sinks.json or system.json Not Found");
        }
        Collections.addAll(tabbyRules, sinkRules);
        if(systemRules != null){
            Collections.addAll(tabbyRules, systemRules);
        }
        for(TabbyRule rule:tabbyRules){
            rule.init();
            if(rules.containsKey(rule.getName())){
                TabbyRule existRule = rules.get(rule.getName());
                existRule.merge(rule);
            }else{
                rules.put(rule.getName(), rule);
            }
        }
        log.info("load "+ rules.size() +" rules success!");
    }

    private void loadTagRules(){
        tagRules = (TagRule[]) FileUtils.getJsonContent(GlobalConfiguration.TAG_RULE_PATH, TagRule[].class);
        if(tagRules == null){
            tagRules = new TagRule[0];
        }

        for(TagRule tagRule:tagRules){
            tagRuleMap.put(tagRule.getName(), tagRule);
        }
    }


    @SuppressWarnings({"unchecked"})
    private void loadIgnore(){
        ignored = (List<String>) FileUtils.getJsonContent(GlobalConfiguration.IGNORE_PATH, List.class);
        if(ignored == null){
            ignored = new ArrayList<>();
        }
    }
    @SuppressWarnings({"unchecked"})
    private void loadBasicClasses(){
        basicClasses = (List<String>) FileUtils.getJsonContent(GlobalConfiguration.BASIC_CLASSES_PATH, List.class);
        if(basicClasses == null){
            basicClasses = new ArrayList<>();
        }
    }

    @SuppressWarnings({"unchecked"})
    private void loadCommonJars(){
        commonJars = (List<String>) FileUtils.getJsonContent(GlobalConfiguration.COMMON_JARS_PATH, List.class);
        if(commonJars == null){
            commonJars = new ArrayList<>();
        }
    }

    public void saveStatus(){
        if(GlobalConfiguration.IS_NEED_TO_CREATE_IGNORE_LIST){
            FileUtils.putJsonContent(GlobalConfiguration.IGNORE_PATH, ignored); // 存储当前以分析的jar包
        }
    }

    public boolean isInCommonJarList(String filename){
        for(String common:commonJars){
            if(filename.startsWith(common)){
                return true;
            }
        }
        return false;
    }
}
