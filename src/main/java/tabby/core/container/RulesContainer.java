package tabby.core.container;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import tabby.common.bean.ref.ClassReference;
import tabby.common.bean.ref.MethodReference;
import tabby.common.rule.TabbyRule;
import tabby.common.rule.TagRule;
import tabby.common.rule.XmlRule;
import tabby.common.utils.FileUtils;
import tabby.common.utils.SemanticUtils;
import tabby.config.GlobalConfiguration;
import tabby.plugin.xml.XmlParsePlugin;

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
    private List<String> basicClasses; // 用于处理 soot 报错
    private List<String> commonJars; // 用于剔除不分析的 jar 包

    private List<String> packageList; // 业务代码的包名列表
    private Map<String, Map<String, Object>> XmlRuleData; // 用于存储 XML 规则的数据
    private Map<String, TagRule> tagRuleMap = new HashMap<>();
    private TagRule[] tagRules;
    private XmlRule[] xmlRules;

    public RulesContainer() throws FileNotFoundException {
        loadRules();
        loadTagRules();
        loadIgnore();
        loadBasicClasses();
        loadCommonJars();
        if (GlobalConfiguration.IS_NEED_PROCESS_XML) {
            loadXmlRule();
        }
    }

    public TabbyRule.Rule getRule(String classname, String method, String subSignature, int parameterSize, Set<String> relatedClassnames, boolean isNeedFromRelated) {
        TabbyRule.Rule ret = null;
        if (rules.containsKey(classname)) {
            TabbyRule rule = rules.get(classname);
            if (rule.contains(subSignature)) { // sub_signature 是最精确的匹配，优先匹配
                ret = rule.getRule(subSignature);
            }
            if (ret == null && rule.contains(method)) { // 使用method名为模糊匹配，只要符合提供的函数名都使用这个规则
                ret = rule.getRule(method);
            }
        }
        // 对于ignore类型，需要递归获取父类的rule
        if (ret == null && isNeedFromRelated) {
            for (String relatedClassname : relatedClassnames) {
                TabbyRule.Rule tmpRule = getRule(relatedClassname, method, subSignature, parameterSize, relatedClassnames, false);
                if (tmpRule != null && tmpRule.isIgnore()) {
                    ret = tmpRule;
                    break;
                }
            }
        }

        if (ret != null && parameterSize > ret.getMax()) {
            return ret;
        }

        return null;
    }

    private void loadRules() {
        List<TabbyRule> tabbyRules = new ArrayList<>();
        TabbyRule[] sinkRules = FileUtils.getJsonContent(GlobalConfiguration.SINK_RULE_PATH, TabbyRule[].class);
        TabbyRule[] systemRules = FileUtils.getJsonContent(GlobalConfiguration.SYSTEM_RULE_PATH, TabbyRule[].class);
        TabbyRule[] incRules = FileUtils.getJsonContent(GlobalConfiguration.INC_RULE_PATH, TabbyRule[].class);

        if (sinkRules != null) {
            Collections.addAll(tabbyRules, sinkRules);
        }

        if (systemRules != null) {
            Collections.addAll(tabbyRules, systemRules);
        }

        if (incRules != null) {
            Collections.addAll(tabbyRules, incRules);
        }

        for (TabbyRule rule : tabbyRules) {
            rule.init();
            if (rules.containsKey(rule.getName())) {
                TabbyRule existRule = rules.get(rule.getName());
                existRule.merge(rule);
            } else {
                rules.put(rule.getName(), rule);
            }
        }
        log.info("load " + rules.size() + " rules success!");
    }

    private void loadTagRules() {
        tagRules = FileUtils.getJsonContent(GlobalConfiguration.TAG_RULE_PATH, TagRule[].class);
        if (tagRules == null) {
            tagRules = new TagRule[0];
        }

        for (TagRule tagRule : tagRules) {
            tagRuleMap.put(tagRule.getName(), tagRule);
        }
    }

    private void loadIgnore() {
        ignored = FileUtils.getJsonContent(GlobalConfiguration.IGNORE_PATH, List.class);
        if (ignored == null) {
            ignored = new ArrayList<>();
        }
    }

    private void loadBasicClasses() {
        basicClasses = FileUtils.getJsonContent(GlobalConfiguration.BASIC_CLASSES_PATH, List.class);
        if (basicClasses == null) {
            basicClasses = new ArrayList<>();
        }
    }

    private void loadCommonJars() {
        commonJars = FileUtils.getJsonContent(GlobalConfiguration.COMMON_JARS_PATH, List.class);
        if (commonJars == null) {
            commonJars = new ArrayList<>();
        }
    }

    private void loadXmlRule() {
        xmlRules = FileUtils.getJsonContent(GlobalConfiguration.XML_RULE_PATH, XmlRule[].class);
        if (xmlRules == null) {
            xmlRules = new XmlRule[0];
        }

        for (XmlRule rule : xmlRules) {
            rule.init();
        }
    }

    public void initXmlRules(Set<String> xmlFiles) {
        XmlRuleData = new HashMap<>();

        for (String xmlPath : xmlFiles) {
            if (xmlPath.endsWith("pom.xml")
                    || xmlPath.endsWith("-logback.xml")
                    || xmlPath.endsWith("log-conf.xml")
                    || xmlPath.endsWith(".aegis.xml")
                    || xmlPath.endsWith("log4j2.xml")
                    || xmlPath.endsWith("log4j2-spring.xml")
                    || xmlPath.endsWith("log4j2-async.xml")
                    || xmlPath.endsWith("log4j.xml")
            ) continue;

            Document document = XmlParsePlugin.parse(xmlPath);

            if (document == null) continue;

            for (XmlRule rule : xmlRules) {
                TagRule tagRule = tagRuleMap.get(rule.getName());
                if (tagRule == null) continue;

                Set<String> classes = XmlParsePlugin.getNodeValues(rule.getClasses(), rule, document);
                Set<String> methods = XmlParsePlugin.getNodeValues(rule.getMethods(), rule, document);
                tagRule.addClasses(classes);
                tagRule.addMethods(methods);

                if (!classes.isEmpty() && !methods.isEmpty() && "mybatis-dto".equals(tagRule.getName())) {
                    // 转化sql
                    TabbyRule tabbyRule = XmlParsePlugin.parseMybatisRule(document);
                    if (tabbyRule == null) continue;
                    tabbyRule.init();
                    if (rules.containsKey(tabbyRule.getName())) {
                        TabbyRule existRule = rules.get(tabbyRule.getName());
                        existRule.merge(tabbyRule);
                    } else {
                        rules.put(tabbyRule.getName(), tabbyRule);
                    }
                }
            }
        }
    }

    public void saveStatus() {
        if (GlobalConfiguration.IS_NEED_TO_CREATE_IGNORE_LIST) {
            FileUtils.putJsonContent(GlobalConfiguration.IGNORE_PATH, ignored); // 存储当前以分析的jar包
        }
    }

    public boolean isInCommonJarList(String filename) {
        for (String common : commonJars) {
            if (filename.startsWith(common)) {
                return true;
            }
        }
        return false;
    }

    public boolean isIgnore(String jar) {
        return ignored.contains(jar);
    }


    public void apply(ClassReference clsRef, MethodReference methodRef) {
        String className = clsRef.getName();
        String methodName = methodRef.getName();
        String subSignature = methodRef.getSubSignature();
        int parameterSize = methodRef.getParameterSize();
        Set<String> relatedClassnames = SemanticUtils.getAllFatherNodes(className);
        TabbyRule.Rule rule = getRule(className, methodName, subSignature, parameterSize, relatedClassnames, true);

        boolean isSink = false;
        boolean isIgnore = false;

        if (rule != null) {
            isSink = rule.isSink() || rule.isAuto();
            isIgnore = rule.isIgnore();

            Map<String, Set<String>> actions = rule.getActions();
            if (isSink) {
                methodRef.setVul(rule.getVul());
            }
            methodRef.setActions(actions != null ? actions : new HashMap<>());
            if (rule.isAuto()) {
                int[][] pollutedPosition = new int[1][parameterSize];
                for (int i = 0; i < parameterSize; i++) {
                    pollutedPosition[0][i] = i;
                }
                methodRef.setPollutedPosition(pollutedPosition);
            } else {
                methodRef.setPollutedPosition(rule.getPolluted());
                methodRef.setActionInitialed(true);
            }
            if (isIgnore) {// 不构建ignore的类型
                methodRef.setInitialed(true);
            }
        }

        methodRef.setSink(isSink);
        methodRef.setIgnore(isIgnore);
        methodRef.setGetter(isGetter(methodRef));
        methodRef.setSetter(isSetter(methodRef));
        methodRef.setSerializable(relatedClassnames.contains("java.io.Serializable"));
        methodRef.setHasDefaultConstructor(clsRef.isHasDefaultConstructor());
        methodRef.setFromAbstractClass(clsRef.isAbstract());
    }

    public void applyTagRule(ClassReference clsRef, MethodReference methodRef) {
        // 根据tags规则处理
        String classname = clsRef.getName();
        for (TagRule tagRule : tagRules) {
            boolean flag = false;
            if (tagRule.isInWhitelist(classname)) {
                break;
            }

            if (tagRule.isAnnotationType()) {
                Set<String> annotations = methodRef.getAnnotations().keySet();
                for (String annotation : annotations) {
                    flag = check(annotation, tagRule.getAnnotations());
                    if (flag) {
                        break;
                    }
                }
                if (!flag) continue;
            }

            if (tagRule.isClassType()) {
                flag = check(classname, tagRule.getClasses());
                if (!flag) {
                    Set<String> relatedClassnames = SemanticUtils.getAllFatherNodes(classname);
                    for (String related : relatedClassnames) {
                        flag = check(related, tagRule.getClasses());
                        if (flag) break;
                    }
                }
                if (!flag) continue;
            }

            if (tagRule.isMethodType()) {
                flag = check(methodRef.getName(), tagRule.getMethods());
                if (!flag) continue;
            }

            if (flag) { // annotation && class && method
                methodRef.setType(tagRule.getValue()); // 可能不止命中一次规则

                if (methodRef.isEndpoint()) {
                    Set<String> baseUrlPaths = SemanticUtils.getHttpUrlPaths(clsRef.getAnnotations());
                    String urlPath = SemanticUtils.getHttpUrlPathWithBaseURLPaths(methodRef.getAnnotations(), baseUrlPaths);
                    methodRef.setUrlPath(urlPath);
                }

                if (methodRef.isMRpc()) {
                    Set<String> operationTypes = SemanticUtils.getMRPCUrlPaths(methodRef.getAnnotations());
                    if (!operationTypes.isEmpty()) {
                        methodRef.setUrlPath(String.join(",", operationTypes));
                    }
                }
            }
        }

    }

    public boolean check(String data, Set<String> rules) {
        boolean flag = false;
        for (String rule : rules) {
            if (rule.startsWith("%")) {
                String tmp = rule.substring(1);
                flag = data.endsWith(tmp);
            } else if (rule.endsWith("%")) {
                String tmp = rule.substring(0, rule.length() - 1);
                flag = data.startsWith(tmp);
            } else {
                flag = data.equals(rule);
            }
            if (flag) return true;
        }
        return false;
    }

    public static boolean isGetter(MethodReference methodRef) {
        String methodName = methodRef.getName();
        Set<String> returnTypes = methodRef.getReturnType();
        boolean noParameter = methodRef.getParameterSize() == 0;
        boolean isPublic = methodRef.isPublic();

        if (!noParameter || !isPublic) return false;

        if (methodName.startsWith("get") && methodName.length() > 3) {
            return !returnTypes.contains("void");
        } else if (methodName.startsWith("is") && methodName.length() > 2) {
            return returnTypes.contains("boolean");
        }

        return false;
    }

    public static boolean isSetter(MethodReference methodRef) {
        String methodName = methodRef.getName();
        Set<String> returnTypes = methodRef.getReturnType();
        boolean singleParameter = methodRef.getParameterSize() == 1;
        boolean isPublic = methodRef.isPublic();

        if (!isPublic || !singleParameter) return false;

        if (methodName.startsWith("set") && methodName.length() > 3) {
            return returnTypes.contains("void");
        }

        return false;
    }
}
