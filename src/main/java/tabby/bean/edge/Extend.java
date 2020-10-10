package tabby.bean.edge;

import lombok.Data;
import org.neo4j.ogm.annotation.*;
import tabby.bean.ref.ClassReference;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Data
@RelationshipEntity(type="EXTEND")
public class Extend {

    @Id
    @GeneratedValue
    private Long id;

    @StartNode
    private ClassReference source;

    @EndNode
    private ClassReference target;

    public static Extend newInstance(ClassReference source, ClassReference target){
        Extend extend = new Extend();
        extend.setSource(source);
        extend.setTarget(target);
        return extend;
    }
}
