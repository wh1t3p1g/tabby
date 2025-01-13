package tabby.plugin.xml;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import tabby.common.rule.TabbyRule;
import tabby.common.rule.XmlRule;
import tabby.plugin.xml.parser.MybatisParser;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

/**
 * @author wh1t3p1g
 * @since 2023/4/27
 */
public class XmlParsePlugin {

    private static DocumentBuilderFactory builderFactory = null;
    private static DocumentBuilder builder = null;

    static {
        try {
            builderFactory = DocumentBuilderFactory.newInstance();
            builderFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            builderFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            builderFactory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            builderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            builderFactory.setXIncludeAware(false);
            builderFactory.setNamespaceAware(true);
            builderFactory.setExpandEntityReferences(false);
            builder = builderFactory.newDocumentBuilder();
        } catch (Exception e) {
            e.printStackTrace();
            builderFactory = null;
            builder = null;
        }

    }

    public static Document parse(String filepath) {
        if (builderFactory == null || builder == null) return null;
        try {
            InputStream is = Files.newInputStream(Paths.get(filepath));
            return builder.parse(is);
        } catch (Exception e) {
//            e.printStackTrace();
        }
        return null;
    }

    public static Set<String> getNodeValues(Set<String> expressions, XmlRule rule, Document document) {
        Set<String> ret = new HashSet<>();
        for (String exp : expressions) {
            XPathExpression xPathExpression = rule.getXPathExpression(exp);
            if (xPathExpression != null) {
                try {
                    ret.addAll(getNodeValue(document, xPathExpression));
                } catch (Exception ig) {
                }
            }
        }
        return ret;
    }

    public static Set<String> getNodeValues(Document document, XPathExpression expression) {
        Set<String> ret = new HashSet<>();
        try {
            NodeList nodes = (NodeList) expression.evaluate(document, XPathConstants.NODESET);
            int length = nodes.getLength();
            for (int i = 0; i < length; i++) {
                Node node = nodes.item(i);
                ret.add(node.getNodeValue());
            }
        } catch (Exception e) {
//            e.printStackTrace();
        }
        return ret;
    }

    public static Set<String> getNodeValue(Document document, XPathExpression expression) {
        Set<String> ret = new HashSet<>();
        try {
            NodeList nodes = (NodeList) expression.evaluate(document, XPathConstants.NODESET);
            int length = nodes.getLength();
            for (int i = 0; i < length; i++) {
                Node node = nodes.item(i);
                ret.add(node.getNodeValue());
            }
        } catch (Exception e) {
//            e.printStackTrace();
        }
        return ret;
    }

    public static TabbyRule parseMybatisRule(Document document) {
        MybatisParser parser = new MybatisParser();
        return parser.parse(document);
    }

}
