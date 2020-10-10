package tabby.bean.edge;

import lombok.Data;
import org.neo4j.ogm.annotation.*;
import tabby.bean.ref.MethodReference;

import java.util.Set;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Data
@RelationshipEntity(type="CALL")
public class Call {

    @Id
    @GeneratedValue
    private Long id;

    @StartNode
    private MethodReference source;

    @EndNode
    private MethodReference target;

    // 以下信息 保存调用现场
    private int lineNum;

    /**
     * 当前调用函数时，所填充的参数位置
     * 例如 a.b(c,d,e) 此时 c可控，则填充1，表示第一个参数可以被污染
     */
    private Set<Integer> pollutedPosition;

    public static Call newInstance(MethodReference source, MethodReference target){
        Call call = new Call();
        call.setSource(source);
        call.setTarget(target);
        return call;
    }
}
