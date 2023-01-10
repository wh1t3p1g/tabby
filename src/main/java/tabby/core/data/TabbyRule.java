package tabby.core.data;

import lombok.Data;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.*;

/**
 * @author wh1t3P1g
 * @since 2020/12/28
 */
@Data
public class TabbyRule {
    private String name;
    private Set<Rule> rules;
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

    public void merge(TabbyRule other){
        rules.addAll(other.getRules());
        ruleMap.putAll(other.getRuleMap());
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

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;

            if (o == null || getClass() != o.getClass()) return false;

            Rule rule = (Rule) o;

            return new EqualsBuilder().append(function, rule.function).append(type, rule.type).append(actions, rule.actions).append(vul, rule.vul).append(polluted, rule.polluted).append(signatures, rule.signatures).isEquals();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder(17, 37).append(function).append(type).append(actions).append(vul).append(polluted).append(signatures).toHashCode();
        }
    }
}
