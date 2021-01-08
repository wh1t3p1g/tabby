package tabby.db.bean.node;

import lombok.Getter;
import lombok.Setter;
import org.neo4j.ogm.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import tabby.db.bean.edge.Call;

import java.util.HashSet;
import java.util.Set;

/**
 * @author wh1t3P1g
 * @since 2021/1/6
 */
@Getter
@Setter
@Document(collection = "Call")
public class CallNode {

    @Id
    private String id;

    private String source;

    /**
     * 第一个遇到的函数
     * 如当前realCallType 存在这个函数，则直接指向当前这个函数
     * 或者直接指向当前的父类的第一个函数
     * 在进行实际检索过程中，可适当进行横向纵向的查找
     */
    private String target;

    // 以下信息 保存调用现场
    private int lineNum = 0;

    private String invokerType;

    /**
     * 记录当前真实的调用类型
     * 比如 A.hashCode() 实际存的target为 object的hashCode函数，但是 realCallType应记录为A类
     */
    private String realCallType;

    /**
     * 当前调用函数时，所填充的参数位置
     * 例如 a.b(c,d,e) 此时 c可控，则填充1，表示第一个参数可以被污染
     *                  a可控，则填充0
     */
    private Set<Integer> pollutedPosition = new HashSet<>();
    private boolean isPolluted = false;

    public static CallNode newInstance(Call call){
        CallNode callNode = new CallNode();
        callNode.setId(call.getId());
        callNode.setPolluted(call.isPolluted());
        callNode.setInvokerType(call.getInvokerType());
        callNode.setRealCallType(call.getRealCallType());
        callNode.setLineNum(call.getLineNum());
        callNode.setPollutedPosition(new HashSet<>(call.getPollutedPosition()));
        callNode.setSource(call.getSource().getId());
        callNode.setTarget(call.getTarget().getId());
        return callNode;
    }
}
