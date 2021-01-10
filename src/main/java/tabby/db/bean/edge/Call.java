package tabby.db.bean.edge;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import soot.Unit;
import soot.Value;
import tabby.db.bean.ref.MethodReference;
import tabby.db.converter.MethodRef2StringConverter;
import tabby.db.converter.Set2JsonStringConverter;

import javax.persistence.*;
import java.util.*;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Getter
@Setter
//@RelationshipEntity(type="CALL")
@Entity
@Table(name = "Call")
public class Call {

    @Id
    private String id;

//    @StartNode
    @Convert(converter = MethodRef2StringConverter.class)
    private MethodReference source;

    /**
     * 第一个遇到的函数
     * 如当前realCallType 存在这个函数，则直接指向当前这个函数
     * 或者直接指向当前的父类的第一个函数
     * 在进行实际检索过程中，可适当进行横向纵向的查找
     */
//    @EndNode
    @Convert(converter = MethodRef2StringConverter.class)
    private MethodReference target;

    // 以下信息 保存调用现场
    private int lineNum = 0;
    private String invokerType;

    /**
     * 记录当前真实的调用类型
     * 比如 A.hashCode() 实际存的target为 object的hashCode函数，但是 realCallType应记录为A类
     */
    private String realCallType;

    private transient Value base;
    private transient List<Value> params = new ArrayList<>();
    private transient Unit unit;

    /**
     * 当前调用函数时，所填充的参数位置
     * 例如 a.b(c,d,e) 此时 c可控，则填充1，表示第一个参数可以被污染
     *                  a可控，则填充0
     */
    @Column(length = 1000)
    @Convert(converter = Set2JsonStringConverter.class)
    private Set<Integer> pollutedPosition = new HashSet<>();
    private boolean isPolluted = false;

    public static Call newInstance(MethodReference source, MethodReference target){
        Call call = new Call();
        call.setId(UUID.randomUUID().toString());
        call.setSource(source);
        call.setTarget(target);
        return call;
    }

    public List<String> toCSV(){
        List<String> csv = new ArrayList<>();
        csv.add(id);
        csv.add(source.getId());
        csv.add(target.getId());
        csv.add(lineNum+"");
        csv.add(Boolean.toString(isPolluted));
        csv.add(String.join("|", ArrayUtils.toStringArray(pollutedPosition.toArray())));
        csv.add(realCallType);
        csv.add(invokerType);
        return csv;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        Call call = (Call) o;

        return new EqualsBuilder().append(lineNum, call.lineNum).append(source, call.source).append(target, call.target).append(invokerType, call.invokerType).append(realCallType, call.realCallType).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(source).append(target).append(lineNum).append(invokerType).append(realCallType).toHashCode();
    }

    //    @Override
//    public boolean equals(Object o) {
//        if (this == o) return true;
//        if (o == null || getClass() != o.getClass()) return false;
//
//        Call call = (Call) o;
//
//        return source.getHandle().equals(call.getSource().getHandle())
//                && target.getHandle().equals(call.getTarget().getHandle())
//                && ((realCallType == null && call.getRealCallType() == null) || (realCallType != null && realCallType.equals(call.getRealCallType())))
//                && ((invokerType == null && call.getInvokerType() == null) || (invokerType != null && invokerType.equals(call.getInvokerType())));
//    }
//
//    @Override
//    public int hashCode() {
//        return new HashCodeBuilder()
//                .append(source)
//                .append(target)
//                .append(realCallType)
//                .append(invokerType)
//                .toHashCode();
//    }
}
