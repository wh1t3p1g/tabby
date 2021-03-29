package tabby.core.data;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tabby.config.GlobalConfiguration;
import tabby.util.FileUtils;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private List<String> basicClasses; // 已经分析过的jar包

    public RulesContainer() throws FileNotFoundException {
        load();
        loadIgnore();
        loadBasicClasses();
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
        TabbyRule[] tempRules = (TabbyRule[]) FileUtils.getJsonContent(GlobalConfiguration.KNOWLEDGE_PATH, TabbyRule[].class);
        if(tempRules == null){
            throw new FileNotFoundException("Sink File Not Found");
        }
        for(TabbyRule rule:tempRules){
            rule.init();
            rules.put(rule.getName(), rule);
        }
        log.info("load "+ rules.size() +" rules success!");
    }

    private void loadIgnore(){
        ignored = (List<String>) FileUtils.getJsonContent(GlobalConfiguration.IGNORE_PATH, List.class);
        if(ignored == null){
            ignored = new ArrayList<>();
        }
    }

    private void loadBasicClasses(){
        basicClasses = (List<String>) FileUtils.getJsonContent(GlobalConfiguration.BASIC_CLASSES_PATH, List.class);
        if(basicClasses == null){
            basicClasses = new ArrayList<>();
        }
    }

    public void saveStatus(){
        FileUtils.putJsonContent(GlobalConfiguration.IGNORE_PATH, ignored); // 存储当前以分析的jar包
    }
}
