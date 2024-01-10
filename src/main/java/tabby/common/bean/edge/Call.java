package tabby.common.bean.edge;

import com.google.common.base.Objects;
import lombok.Getter;
import lombok.Setter;
import tabby.common.bean.converter.ListInteger2JsonStringConverter;
import tabby.common.bean.converter.ListSet2JsonStringConverter;
import tabby.common.bean.ref.MethodReference;
import tabby.common.utils.HashingUtils;

import javax.persistence.*;
import java.util.*;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Getter
@Setter
@Entity
@Table(name = "Call")
public class Call {

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

    private boolean isCallerThisFieldObj = false;

    /**
     * 当前调用函数时，所填充的参数位置
     * 例如 a.b(c,d,e) 此时 c可控，则填充1，表示第一个参数可以被污染
     *                  a可控，则填充0
     */
    @Column(columnDefinition = "TEXT")
    @Convert(converter = ListInteger2JsonStringConverter.class)
    private List<Integer> pollutedPosition = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    @Convert(converter = ListSet2JsonStringConverter.class)
    private List<Set<String>> types = new LinkedList<>();

    public static Call newInstance(MethodReference source, MethodReference target){
        Call call = new Call();
        call.setSource(source.getId());
        call.setTarget(target.getId());
        return call;
    }

    public void generateId(){
        String sb = source + target +
                lineNum + isCallerThisFieldObj +
                invokerType + realCallType +
                Arrays.deepToString(pollutedPosition.toArray());
        id = HashingUtils.hashString(sb);
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
