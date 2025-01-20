package tabby.common.rule;

import lombok.Data;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Data
public class XmlRule {
    private String name;
    private Set<String> classes = new HashSet<>();
    private Set<String> methods = new HashSet<>();

    private transient Map<String, XPathExpression> xPaths = new HashMap<>();

    public void init() {
        Set<String> all = new HashSet<>(classes);
        all.addAll(methods);
        XPath xPath = XPathFactory.newInstance().newXPath();
        for (String exp : all) {
            try {
                XPathExpression xPathExpression = xPath.compile(exp);
                xPaths.put(exp, xPathExpression);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public XPathExpression getXPathExpression(String expression) {
        return xPaths.getOrDefault(expression, null);
    }

}
