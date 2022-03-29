package tabby.core.data;

import lombok.Data;

import java.util.*;

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

    public static class Rule {
        private String function;
        private String type;
        private Map<String, String> actions;
        private String vul;
        private List<Integer> polluted;
        private List<String> signatures;

        public Rule() {
            signatures = new ArrayList<>();
            polluted = new ArrayList<>();
            actions = new HashMap<>();
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

        public String getVul(){
            return vul;
        }

        public boolean isEmptySignaturesList(){
            return signatures == null || signatures.isEmpty();
        }

        public boolean isContainsSignature(String sig){
            return signatures != null && signatures.contains(sig);
        }
    }
}
