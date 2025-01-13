package tabby.common.rule;

import lombok.Data;

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

    public void init() {
        ruleMap = new HashMap<>();
        if (rules != null) {
            rules.forEach(rule -> {
                ruleMap.put(rule.function, rule);
            });
        }
    }

    public void addRule(Rule rule) {
        if (rules == null) {
            rules = new HashSet<>();
        }
        rules.add(rule);
    }

    public void merge(TabbyRule other) {
        rules.addAll(other.getRules());
        ruleMap.putAll(other.getRuleMap());
    }

    public boolean contains(String key) {
        if (ruleMap == null) init();
        return ruleMap.containsKey(key);
    }

    public Rule getRule(String key) {
        if (ruleMap == null) init();
        return ruleMap.get(key);
    }

    @Data
    public static class Rule {
        private String function;
        private String type;
        private Map<String, Set<String>> actions;
        private String vul;
        private int[][] polluted;
        private int max;

        public Rule() {
            actions = new HashMap<>();
        }

        public boolean isSink() {
            return "sink".equals(type);
        }

        public boolean isKnow() {
            return "know".equals(type);
        }

        public boolean isIgnore() {
            return "ignore".equals(type);
        }

        public boolean isAuto() {
            return "auto".equals(type);
        }

        public Map<String, Set<String>> getActions() {
            return actions;
        }

        public int[][] getPolluted() {
            return polluted;
        }

        public String getFunction() {
            return function;
        }

        public String getType() {
            return type;
        }

        public String getVul() {
            return vul;
        }

        public int getMax() {
            return max;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Rule rule = (Rule) o;

            if (max != rule.max) return false;
            if (!Objects.equals(function, rule.function)) return false;
            if (!Objects.equals(type, rule.type)) return false;
            if (!Objects.equals(actions, rule.actions)) return false;
            if (!Objects.equals(vul, rule.vul)) return false;
            return Arrays.deepEquals(polluted, rule.polluted);
        }

        @Override
        public int hashCode() {
            int result = function != null ? function.hashCode() : 0;
            result = 31 * result + (type != null ? type.hashCode() : 0);
            result = 31 * result + (actions != null ? actions.hashCode() : 0);
            result = 31 * result + (vul != null ? vul.hashCode() : 0);
            result = 31 * result + Arrays.deepHashCode(polluted);
            result = 31 * result + max;
            return result;
        }
    }
}
