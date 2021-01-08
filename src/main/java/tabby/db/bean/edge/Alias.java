package tabby.db.bean.edge;

import lombok.Getter;
import lombok.Setter;
import org.neo4j.ogm.annotation.EndNode;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.RelationshipEntity;
import org.neo4j.ogm.annotation.StartNode;
import tabby.db.bean.ref.MethodReference;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author wh1t3P1g
 * @since 2020/11/22
 */
@Getter
@Setter
@RelationshipEntity(type="ALIAS")
public class Alias {

    @Id
    private String id;

    @StartNode
    private MethodReference source;

    @EndNode
    private MethodReference target;

    public static Alias newInstance(MethodReference source, MethodReference target){
        Alias alias = new Alias();
        alias.setId(UUID.randomUUID().toString());
        alias.setSource(source);
        alias.setTarget(target);
        return alias;
    }

    public List<String> toCSV(){
        List<String> csv = new ArrayList<>();
        csv.add(id);
        csv.add(source.getId());
        csv.add(target.getId());
        return csv;
    }
}
