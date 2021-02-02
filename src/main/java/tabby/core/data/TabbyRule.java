package tabby.core.data;

import lombok.Data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author wh1t3P1g
 * @since 2020/12/28
 */
@Data
public class TabbyRule {
    private String name;
    private List<Rule> rules;
    private transient Map<String, Rule> ruleMap;

    public TabbyRule() {
    }

    public void init(){
        ruleMap = new HashMap<>();
        if(rules != null){
            rules.forEach(rule -> {
                ruleMap.put(rule.function, rule);
            });
        }
    }

    public boolean contains(String key){
        if(ruleMap == null) init();
        return ruleMap.containsKey(key);
    }

    public Rule getRule(String key){
        if(ruleMap == null) init();
        return ruleMap.get(key);
    }

    public class Rule {
        private String function;
        private String type;
        private Map<String, String> actions;
        private List<Integer> polluted;
        private List<String> signatures;

        public Rule() {
        }

        public boolean isSink(){
            return "sink".equals(type);
        }

        public boolean isKnow(){
            return "know".equals(type);
        }

        public boolean isIgnore(){
            return "ignore".equals(type);
        }

        public boolean isSource(){
            return "source".equals(type);
        }

        public Map<String, String> getActions(){
            return actions;
        }

        public List<Integer> getPolluted(){
            return polluted;
        }

        public String getFunction() {
            return function;
        }

        public String getType() {
            return type;
        }

        public List<String> getSignatures(){
            return signatures;
        }

    }
}
