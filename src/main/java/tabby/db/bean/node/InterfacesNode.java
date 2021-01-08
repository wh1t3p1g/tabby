package tabby.db.bean.node;

import lombok.Getter;
import lombok.Setter;
import org.neo4j.ogm.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import tabby.db.bean.edge.Interfaces;

/**
 * @author wh1t3P1g
 * @since 2021/1/6
 */
@Getter
@Setter
@Document(collection = "Interfaces")
public class InterfacesNode {
    @Id
    private String id;

    private String source;

    private String target;

    public static InterfacesNode newInstance(Interfaces interfaces){
        InterfacesNode hasNode = new InterfacesNode();
        hasNode.setId(interfaces.getId());
        hasNode.setSource(interfaces.getSource().getId());
        hasNode.setTarget(interfaces.getTarget().getId());
        return hasNode;
    }
}
