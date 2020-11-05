package tabby.dal.bean.edge;

import lombok.Getter;
import lombok.Setter;
import org.neo4j.ogm.annotation.EndNode;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.RelationshipEntity;
import org.neo4j.ogm.annotation.StartNode;
import org.neo4j.ogm.annotation.typeconversion.Convert;
import org.neo4j.ogm.typeconversion.UuidStringConverter;
import tabby.dal.bean.ref.MethodReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Getter
@Setter
@RelationshipEntity(type="CALL")
public class Call {

    @Id
    @Convert(UuidStringConverter.class)
    private UUID uuid;

    @StartNode
    private MethodReference source;

    @EndNode
    private MethodReference target;

    // 以下信息 保存调用现场
    private int lineNum = 0;

    /**
     * 当前调用函数时，所填充的参数位置
     * 例如 a.b(c,d,e) 此时 c可控，则填充1，表示第一个参数可以被污染
     */
    private Set<Integer> pollutedPosition;

    public static Call newInstance(MethodReference source, MethodReference target){
        Call call = new Call();
        call.setUuid(UUID.randomUUID());
        call.setSource(source);
        call.setTarget(target);
        return call;
    }

    public List<String> toCSV(){
        List<String> csv = new ArrayList<>();
        csv.add(uuid.toString());
        csv.add(source.getUuid().toString());
        csv.add(target.getUuid().toString());
        csv.add(lineNum+"");
        return csv;
    }
}
