package tabby.core.container;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tabby.common.bean.ref.MethodReference;
import tabby.config.GlobalConfiguration;
import tabby.common.rule.TabbyRule;
import tabby.common.utils.FileUtils;

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

    public RulesContainer() throws FileNotFoundException {
        load();
        loadIgnore();
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

    public void applyRule(String classname, MethodReference methodRef, Set<String> relatedClassnames){
        TabbyRule.Rule rule = getRule(classname, methodRef.getName());

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
