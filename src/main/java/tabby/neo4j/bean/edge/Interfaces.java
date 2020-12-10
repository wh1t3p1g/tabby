package tabby.neo4j.bean.edge;

import lombok.Getter;
import lombok.Setter;
import org.neo4j.ogm.annotation.EndNode;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.RelationshipEntity;
import org.neo4j.ogm.annotation.StartNode;
import org.neo4j.ogm.annotation.typeconversion.Convert;
import org.neo4j.ogm.typeconversion.UuidStringConverter;
import tabby.neo4j.bean.ref.ClassReference;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Getter
@Setter
@RelationshipEntity(type="INTERFACE")
public class Interfaces {

    @Id
    @Convert(UuidStringConverter.class)
    private UUID uuid;

    @StartNode
    private ClassReference source;

    @EndNode
    private ClassReference target;

    public static Interfaces newInstance(ClassReference source, ClassReference target){
        Interfaces interfaces = new Interfaces();
        interfaces.setUuid(UUID.randomUUID());
        interfaces.setSource(source);
        interfaces.setTarget(target);
        return interfaces;
    }

    public List<String> toCSV(){
        List<String> csv = new ArrayList<>();
        csv.add(uuid.toString());
        csv.add(source.getUuid().toString());
        csv.add(target.getUuid().toString());
        return csv;
    }
}
