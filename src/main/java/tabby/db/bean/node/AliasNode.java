package tabby.db.bean.node;

import lombok.Getter;
import lombok.Setter;
import org.neo4j.ogm.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import tabby.db.bean.edge.Alias;

/**
 * @author wh1t3P1g
 * @since 2021/1/6
 */
@Getter
@Setter
@Document(collection = "Alias")
public class AliasNode {
    @Id
    private String id;

    private String source;

    private String target;

    public static AliasNode newInstance(Alias alias){
        AliasNode aliasNode = new AliasNode();
        aliasNode.setId(alias.getId());
        aliasNode.setSource(alias.getSource().getId());
        aliasNode.setTarget(alias.getTarget().getId());
        return aliasNode;
    }
}
