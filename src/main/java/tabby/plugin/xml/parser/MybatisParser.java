package tabby.plugin.xml.parser;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import tabby.common.rule.TabbyRule;
import tabby.plugin.xml.XmlParsePlugin;

import javax.xml.xpath.*;
import java.util.*;

/**
 * @author wh1t3p1g
 * @since 2023/5/16
 */
public class MybatisParser implements Parser {

    private static List<String> classExpressions = Arrays.asList("//mapper/@namespace", "//mapper/@class");
    private static List<String> methodsExpressions = Arrays.asList("//mapper/insert", "//mapper/select", "//mapper/update", "//mapper/delete");
    private Map<String, String> sqlClosure = new HashMap<>();

    @Override
    public TabbyRule parse(Document document) {
        // get classname
        String classname = getClassname(document);
        if (classname == null || classname.isEmpty()) return null;
        // get sql
        initSqlClosures(document);
        // get methods
        Set<String> methods = getSinkMethods(document);
        if (methods.isEmpty()) return null;
        // generate tabby rule
        TabbyRule tabbyRule = new TabbyRule();
        tabbyRule.setName(classname);
        for (String method : methods) {
            TabbyRule.Rule rule = new TabbyRule.Rule();
            rule.setFunction(method);
            rule.setType("auto");
            rule.setVul("SQLI");
            rule.setMax(-2);
            tabbyRule.addRule(rule);
        }
        return tabbyRule;
    }

    public void initSqlClosures(Document document) {
        XPath xPath = XPathFactory.newInstance().newXPath();
        try {
            XPathExpression expression = xPath.compile("//sql");
            NodeList nodeList = (NodeList) expression.evaluate(document, XPathConstants.NODESET);
            int length = nodeList.getLength();
            for (int i = 0; i < length; i++) {
                Element element = (Element) nodeList.item(i);
                String key = element.getAttribute("id");
                String value = getNodeListText(element.getChildNodes());
                if (value != null) {
                    sqlClosure.put(key, value);
                }
            }
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }

    public String getClassname(Document document) {
        XPath xPath = XPathFactory.newInstance().newXPath();
        Set<String> classes = null;
        XPathExpression expression = null;
        for (String exp : classExpressions) {
            try {
                expression = xPath.compile(exp);
                classes = XmlParsePlugin.getNodeValues(document, expression);
                if (classes.size() > 0) {
                    return (String) classes.toArray()[0];
                }
            } catch (XPathExpressionException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public Set<String> getSinkMethods(Document document) {
        XPath xPath = XPathFactory.newInstance().newXPath();
        Set<String> methods = new HashSet<>();
        XPathExpression expression = null;
        for (String exp : methodsExpressions) {
            try {
                expression = xPath.compile(exp + "/@id");
                Set<String> values = XmlParsePlugin.getNodeValues(document, expression);
                for (String value : values) {
                    expression = xPath.compile(String.format("%s[@id='%s']", exp, value));
                    String sql = getSql(document, expression);
                    if (sql != null && (sql.contains("${") || sql.contains("<bind name=\""))) {
                        methods.add(value);
                    }
                }
            } catch (XPathExpressionException e) {
                e.printStackTrace();
            }
        }
        return methods;
    }

    public String getSql(Document document, XPathExpression expression) {
        try {
            Node node = (Node) expression.evaluate(document, XPathConstants.NODE);
            NodeList childNodes = node.getChildNodes();
            return getNodeListText(childNodes);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }


    public String getNodeListText(NodeList nodes) {
        StringBuilder sb = new StringBuilder();
        int length = nodes.getLength();
        for (int i = 0; i < length; i++) {
            sb.append(getNodeText(nodes.item(i)));
        }
        return sb.toString().trim();
    }

    public String getNodeText(Node node) {
        StringBuilder sb = new StringBuilder();
        short nodeType = node.getNodeType();
        if (nodeType == Node.TEXT_NODE) {
            sb.append(" ");
            sb.append(node.getTextContent().trim());
        } else if (nodeType == Node.ELEMENT_NODE) {
            Element element = (Element) node;
            String name = node.getNodeName();
            if ("trim".equals(name)) {
                String prefix = element.getAttribute("prefix");
                String suffix = element.getAttribute("suffix");
//                String suffixOverrides = element.getAttribute("suffixOverrides");
                sb.append(" ");
                sb.append(prefix);
                sb.append(getNodeListText(element.getChildNodes()));
                sb.append(suffix);
            } else if ("bind".equals(name)) {
                String bindName = element.getAttribute("name");
                String bindValue = element.getAttribute("value");
                sb.append(" ");
                sb.append(String.format("<bind name=\"%s\" value=\"%s\"", bindName, bindValue));
            } else if ("include".equals(name)) {
                String id = element.getAttribute("refid");
                if (sqlClosure.containsKey(id)) {
                    sb.append(" ");
                    sb.append(sqlClosure.getOrDefault(id, ""));
                }
            } else if ("where".equals(name)) {
                sb.append(" ");
                sb.append("where");
                NodeList nodeList = element.getChildNodes();
                sb.append(" ");
                sb.append(getNodeListText(nodeList));
            } else {
                NodeList nodeList = element.getChildNodes();
                sb.append(" ");
                sb.append(getNodeListText(nodeList));
            }
        }
        return sb.toString();
    }
}
