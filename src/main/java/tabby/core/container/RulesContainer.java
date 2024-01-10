package tabby.core.container;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
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
