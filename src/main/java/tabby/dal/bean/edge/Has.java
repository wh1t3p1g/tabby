package tabby.dal.bean.edge;

import lombok.Getter;
import lombok.Setter;
import org.neo4j.ogm.annotation.EndNode;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.RelationshipEntity;
import org.neo4j.ogm.annotation.StartNode;
import org.neo4j.ogm.annotation.typeconversion.Convert;
import org.neo4j.ogm.typeconversion.UuidStringConverter;
import tabby.dal.bean.ref.ClassReference;
import tabby.dal.bean.ref.MethodReference;

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
    @Convert(UuidStringConverter.class)
    private UUID uuid;

    @StartNode
    private ClassReference classRef;

    @EndNode
    private MethodReference methodRef;

    public static Has newInstance(ClassReference classRef, MethodReference methodRef){
        Has has = new Has();
        has.setUuid(UUID.randomUUID());
        has.setClassRef(classRef);
        has.setMethodRef(methodRef);
        return has;
    }

    public List<String> toCSV(){
        List<String> csv = new ArrayList<>();
        csv.add(uuid.toString());
        csv.add(classRef.getUuid().toString());
        csv.add(methodRef.getUuid().toString());
        return csv;
    }
}
