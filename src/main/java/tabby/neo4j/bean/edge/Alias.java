package tabby.neo4j.bean.edge;

import lombok.Getter;
import lombok.Setter;
import org.neo4j.ogm.annotation.EndNode;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.RelationshipEntity;
import org.neo4j.ogm.annotation.StartNode;
import org.neo4j.ogm.annotation.typeconversion.Convert;
import org.neo4j.ogm.typeconversion.UuidStringConverter;
import tabby.neo4j.bean.ref.MethodReference;

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
    @Convert(UuidStringConverter.class)
    private UUID uuid;

    @StartNode
    private MethodReference source;

    @EndNode
    private MethodReference target;

    private boolean isPolluted = true;

    public static Alias newInstance(MethodReference source, MethodReference target){
        Alias alias = new Alias();
        alias.setUuid(UUID.randomUUID());
        alias.setSource(source);
        alias.setTarget(target);
        return alias;
    }

    public List<String> toCSV(){
        List<String> csv = new ArrayList<>();
        csv.add(uuid.toString());
        csv.add(source.getUuid().toString());
        csv.add(target.getUuid().toString());
        csv.add(Boolean.toString(isPolluted));
        return csv;
    }
}
