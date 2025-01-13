package tabby.common.bean.edge;

import com.google.common.base.Objects;
import com.google.common.primitives.Ints;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import tabby.common.bean.converter.IntArray2JsonStringConverter;
import tabby.common.bean.converter.ListSet2JsonStringConverter;
import tabby.common.bean.ref.MethodReference;
import tabby.common.utils.HashingUtils;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Getter
@Setter
@Entity
@Table(name = "Call", indexes = {@Index(columnList = "id")})
public class Call implements Edge {

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
    private boolean isCallerThisFieldObj = false;

    /**
     * 当前调用函数时，所填充的参数位置
     * 例如 a.b(c,d,e) 此时 c可控，则填充1，表示第一个参数可以被污染
     * a可控，则填充0
     */
    @Column(columnDefinition = "TEXT")
    @Convert(converter = IntArray2JsonStringConverter.class)
    private int[][] pollutedPosition;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = ListSet2JsonStringConverter.class)
    private List<Set<String>> types = new LinkedList<>();

    public static Call newInstance(MethodReference source, MethodReference target, List<Set<Integer>> pollutedPosition) {
        Call call = new Call();
        call.setSource(source.getId());
        call.setTarget(target.getId());
        call.setPollutedPosition(transform(pollutedPosition));
//        call.setId(SemanticHelper.hashString(call.getSource()+call.getTarget()+pollutedPosition.hashCode()));
//        call.setId(UUID.randomUUID().toString());
        return call;
    }

    public static Call newInstance(MethodReference source, MethodReference target,
                                   String invokerType, boolean isCallerThisFieldObj,
                                   List<Set<Integer>> pollutedPosition, List<Set<String>> types) {
        Call call = newInstance(source, target, pollutedPosition);
        call.setInvokerType(invokerType);
        call.setTypes(types);
        call.setCallerThisFieldObj(isCallerThisFieldObj);
        call.generateId();
        return call;
    }

    public void generateId() {
        String sb = source + target +
//                lineNum +
                isCallerThisFieldObj +
                invokerType +
                Arrays.deepToString(pollutedPosition);
        id = HashingUtils.hashString(sb);
    }

    public static int[][] transform(List<Set<Integer>> pollutedPosition) {
        int len = pollutedPosition.size();
        if (len >= 50) { // 当调用函数入参超过100个的时候，不再记录
            return new int[0][];
        }
        int[][] ret = new int[len][];
        for (int i = 0; i < len; i++) {
            Set<Integer> s = pollutedPosition.get(i);
            ret[i] = Ints.toArray(s);
        }
        return ret;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Call call = (Call) o;
        return Objects.equal(id, call.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
