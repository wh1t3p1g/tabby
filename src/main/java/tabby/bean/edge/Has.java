package tabby.bean.edge;

import lombok.Data;
import org.neo4j.ogm.annotation.*;
import tabby.bean.ref.ClassReference;
import tabby.bean.ref.MethodReference;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Data
@RelationshipEntity(type="HAS")
public class Has {

    @Id
    @GeneratedValue
    private Long id;

    @StartNode
    private ClassReference classRef;

    @EndNode
    private MethodReference methodRef;

    public static Has newInstance(ClassReference classRef, MethodReference methodRef){
        Has has = new Has();
        has.setClassRef(classRef);
        has.setMethodRef(methodRef);
        return has;
    }
}
