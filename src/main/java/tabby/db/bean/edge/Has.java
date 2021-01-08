package tabby.db.bean.edge;

import lombok.Getter;
import lombok.Setter;
import org.neo4j.ogm.annotation.EndNode;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.RelationshipEntity;
import org.neo4j.ogm.annotation.StartNode;
import tabby.db.bean.ref.ClassReference;
import tabby.db.bean.ref.MethodReference;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Getter
@Setter
@RelationshipEntity(type="HAS")
public class Has {

    @Id
    private String id;

    @StartNode
    private ClassReference classRef;

    @EndNode
    private MethodReference methodRef;

    public static Has newInstance(ClassReference classRef, MethodReference methodRef){
        Has has = new Has();
        has.setId(UUID.randomUUID().toString());
        has.setClassRef(classRef);
        has.setMethodRef(methodRef);
        return has;
    }

    public List<String> toCSV(){
        List<String> csv = new ArrayList<>();
        csv.add(id);
        csv.add(classRef.getId());
        csv.add(methodRef.getId());
        return csv;
    }
}
