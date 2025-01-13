package tabby.plugin.xml.parser;

import org.w3c.dom.Document;
import tabby.common.rule.TabbyRule;

/**
 * @author wh1t3p1g
 * @since 2023/5/16
 */
public interface Parser {

    TabbyRule parse(Document document);
}
