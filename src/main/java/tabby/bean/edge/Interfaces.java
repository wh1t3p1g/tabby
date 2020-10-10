package tabby.bean.edge;

import lombok.Data;
import org.neo4j.ogm.annotation.*;
import tabby.bean.ref.ClassReference;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Data
@RelationshipEntity(type="INTERFACE")
public class Interfaces {

    @Id
    @GeneratedValue
    private Long id;

    @StartNode
    private ClassReference source;

    @EndNode
    private ClassReference target;

    public static Interfaces newInstance(ClassReference source, ClassReference target){
        Interfaces interfaces = new Interfaces();
        interfaces.setSource(source);
        interfaces.setTarget(target);
        return interfaces;
    }
}
