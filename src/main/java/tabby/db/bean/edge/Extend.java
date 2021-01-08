package tabby.db.bean.edge;

import lombok.Getter;
import lombok.Setter;
import org.neo4j.ogm.annotation.EndNode;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.RelationshipEntity;
import org.neo4j.ogm.annotation.StartNode;
import tabby.db.bean.ref.ClassReference;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Getter
@Setter
@RelationshipEntity(type="EXTEND")
public class Extend {

    @Id
    private String id;

    @StartNode
    private ClassReference source;

    @EndNode
    private ClassReference target;

    public static Extend newInstance(ClassReference source, ClassReference target){
        Extend extend = new Extend();
        extend.setId(UUID.randomUUID().toString());
        extend.setSource(source);
        extend.setTarget(target);
        return extend;
    }

    public List<String> toCSV(){
        List<String> csv = new ArrayList<>();
        csv.add(id);
        csv.add(source.getId());
        csv.add(target.getId());
        return csv;
    }
}
