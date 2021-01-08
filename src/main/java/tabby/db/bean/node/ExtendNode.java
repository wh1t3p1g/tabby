package tabby.db.bean.node;

import lombok.Getter;
import lombok.Setter;
import org.neo4j.ogm.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import tabby.db.bean.edge.Extend;

/**
 * @author wh1t3P1g
 * @since 2021/1/6
 */
@Getter
@Setter
@Document(collection = "Extend")
public class ExtendNode {
    @Id
    private String id;

    private String source;

    private String target;

    public static ExtendNode newInstance(Extend extend){
        ExtendNode extendNode = new ExtendNode();
        extendNode.setId(extend.getId());
        extendNode.setSource(extend.getSource().getId());
        extendNode.setTarget(extend.getTarget().getId());
        return extendNode;
    }
}
