package tabby.db.bean.node;

import lombok.Getter;
import lombok.Setter;
import org.neo4j.ogm.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import tabby.db.bean.edge.Has;

/**
 * @author wh1t3P1g
 * @since 2021/1/6
 */
@Getter
@Setter
@Document(collection = "Has")
public class HasNode {
    @Id
    private String id;

    private String classNode;

    private String methodNode;

    public static HasNode newInstance(Has has){
        HasNode hasNode = new HasNode();
        hasNode.setId(has.getId());
        hasNode.setClassNode(has.getClassRef().getId());
        hasNode.setMethodNode(has.getMethodRef().getId());
        return hasNode;
    }
}
