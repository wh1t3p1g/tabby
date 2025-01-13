package tabby.analysis.data;

import com.google.common.hash.Hashing;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import soot.*;
import soot.jimple.Constant;
import soot.jimple.NullConstant;
import soot.jimple.StringConstant;
import tabby.analysis.helper.SemanticHelper;
import tabby.common.utils.HashingUtils;
import tabby.common.utils.PositionUtils;
import tabby.common.utils.SemanticUtils;
import tabby.config.GlobalConfiguration;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author wh1t3p1g
 * @since 2021/11/26
 */
@Getter
@Setter
public class SimpleValue {

    /**
     * Local SootValue.toString.md5
     * Field Field.toString.md5
     */
    private String uuid;
    /**
     * position主要用于标记来源于param / this
     * 不会改变，是固定的
     * -2 来源于source
     * -1 来源于this
     * 0-n 来源于param
     * 一般情况，position只有一个值，除非是模糊处理后的情况，可能有多个position
     */
    private Set<Integer> position = new HashSet<>();
    private String constantValue = null;
    private String transfer = "";// 用于传递value，不是value的正式值
    private boolean isLocked = false;

    private boolean isConstant = false;
    private boolean isConstantArray = false;
    private boolean isCollection = false;
    public boolean isArray = false;
    private boolean isClass = false;
    private boolean isStatic = false;
    private boolean isInterface = false;
    private boolean isEmpty = false;// 用于传递value，不是value的正式值
    private boolean isNull = false;
    private boolean isSource = false;
    private boolean isThis = false;
    private boolean isField = false;

    // 以下信息会发生变化
    /**
     * 解析右值时确认下来的type
     */
    private Set<String> type = new HashSet<>();
    /**
     * field 本身没有Object对象，仅有Value对象
     * 这里
     */
    // fieldName : fieldObjName
    // field 不添加的localObjects 和 globalObjects
    // 寻址要先找到baseObject，再找field本身
    private Map<String, Set<String>> fields = new HashMap<>();
    private Domain status = Domain.NOT_POLLUTED;
    private Set<String> arrays = new HashSet<>();
    private Set<String> tempFieldValues = new HashSet<>();

    public boolean isArray() {
        return isArray || isCollection;
    }

    /**
     * 创建一个带有uuid的SimpleValue
     * 其他信息都没有
     *
     * @param value
     * @return
     */
    public static SimpleValue newInstance(Object value) {
        SimpleValue retVal = new SimpleValue();
        retVal.setUuid(SemanticUtils.calculateUuid(value));
        return retVal;
    }

    public static SimpleValue makeLocalValue(Local local, boolean isArray) {
        SimpleValue retVal = newInstance(local);
        Type type = local.getType();
        SemanticHelper.judgeType(retVal, type, isArray);
        return retVal;
    }

    public static SimpleValue makeFieldValue(SootField field, boolean isArray) {
        Type type = field.getType();
        SimpleValue retVal = newInstance(field);
        retVal.setStatic(field.isStatic());
        retVal.setField(true);
        SemanticHelper.judgeType(retVal, type, isArray);
        return retVal;
    }

    public static SimpleValue makeConstantValue(Constant constant) {
        SimpleValue retVal = newInstance(constant);
        retVal.getType().add(constant.getType().toString());
        retVal.setConstant(true);
        if (constant instanceof StringConstant) {
            retVal.setConstantValue(((StringConstant) constant).value);
        } else if (constant instanceof NullConstant) {
            retVal.setConstantValue("null");
        }

        return retVal;
    }

    public static SimpleValue makeNullValue() {
        SimpleValue retVal = new SimpleValue();
        retVal.setUuid(HashingUtils.hashString(PositionUtils.NULL_STRING));
        retVal.getType().add(NullType.v().toString());
        retVal.setConstantValue("null");
        retVal.setNull(true);
        retVal.setConstant(true);
        return retVal;
    }

    public static SimpleValue makeSourceValue() {
        SimpleValue retVal = new SimpleValue();
        retVal.setUuid(HashingUtils.hashString("source-value"));
        retVal.getPosition().add(PositionUtils.SOURCE);
        retVal.setSource(true);
        return retVal;
    }

    public static SimpleValue makeValueByString(String value, int position) {
        SimpleValue retVal = new SimpleValue();
        retVal.setUuid(HashingUtils.hashString(value));
        retVal.getPosition().add(position);
        return retVal;
    }

    // return 传递东西
    public static SimpleValue makeTransferValue(Type type) {
        SimpleValue retVal = new SimpleValue();
        retVal.setStatus(Domain.NOT_POLLUTED);
        retVal.getType().add(type.toString());
        retVal.setTransfer("type");
        return retVal;
    }

    public static SimpleValue makeArrayValueWithType(String name, SimpleValue val, Type type) {
        SimpleValue retVal = new SimpleValue();
        retVal.setUuid(Hashing.md5()
                .hashString(name + type + "-array-value", StandardCharsets.UTF_8)
                .toString());
        retVal.getType().add(type.toString());
        SemanticHelper.judgeType(retVal, type, false);
        retVal.setStatus(val.getStatus().copy());
        retVal.setPosition(new HashSet<>(val.getPosition()));
        return retVal;
    }

    public static SimpleValue makeArrayValueWithType(String name, SimpleValue val) {
        SimpleValue retVal = new SimpleValue();
        String type = "java.lang.Object";
        for (String t : val.getType()) {
            if (t.endsWith("[]")) {
                type = t.substring(0, t.length() - 2);
            }
        }

        SootClass cls = SemanticUtils.getSootClass(type);

        retVal.setUuid(Hashing.md5()
                .hashString(name + type + "-array-value", StandardCharsets.UTF_8)
                .toString());
        retVal.getType().add(type);
        if (cls != null) {
            SemanticHelper.judgeType(retVal, cls.getType(), false);
        }
        retVal.setStatus(val.getStatus().copy());
        retVal.setPosition(new HashSet<>(val.getPosition()));
        return retVal;
    }

    public static SimpleValue makeReturnValue(Type type) {
        SimpleValue retVal = new SimpleValue();
        retVal.setUuid(Hashing.md5()
                .hashString(type + "-return-value", StandardCharsets.UTF_8)
                .toString());
        retVal.getType().add(type.toString());
        SemanticHelper.judgeType(retVal, type, false);
        return retVal;
    }

    public static SimpleValue makePhantomArrayValue(String rName, SimpleValue baseVal, SimpleValue val) {
        SimpleValue phantomVal = val.clone();
        phantomVal.setUuid(Hashing.md5()
                .hashString(rName + "-phantom-array-value", StandardCharsets.UTF_8)
                .toString());
        phantomVal.setStatus(baseVal.getStatus().copy());
        phantomVal.setPosition(new HashSet<>(baseVal.getPosition()));

        return phantomVal;
    }

    /**
     * 因为不清楚当前field的类型是什么，所以直接给java.lang.Object类型
     *
     * @param fieldName
     * @return
     */
    public static SimpleValue makePhantomFieldValue(String fieldName, SimpleValue baseVal) {
        SimpleValue retVal = new SimpleValue();
        retVal.setUuid(Hashing.md5()
                .hashString(baseVal.getUuid() + fieldName, StandardCharsets.UTF_8)
                .toString());
        retVal.getType().add("java.lang.Object");
        retVal.setField(true);
        retVal.setStatus(baseVal.getStatus().copy());
        retVal.setPosition(new HashSet<>(baseVal.getPosition()));
        return retVal;
    }

    public void clear() {
        position.clear();
        status = Domain.NOT_POLLUTED;
        constantValue = "";
        fields.clear();
        arrays.clear();
    }

    public SimpleValue clone() {
        SimpleValue value = new SimpleValue();
        value.setArray(isArray);
        value.setArrays(new HashSet<>(arrays));

        value.setConstant(isConstant);
        value.setConstantArray(isConstantArray);
        value.setCollection(isCollection);
        value.setConstantValue(constantValue);

        value.setEmpty(isEmpty);
        value.setTransfer(transfer);
        value.setInterface(isInterface);
        value.setStatic(isStatic);
        value.setClass(isClass);
//        value.setLocked(isLocked);
        value.setNull(isNull);
        value.setThis(isThis);
        value.setField(isField);
        value.setSource(isSource);

        value.setUuid(uuid);
        value.setPosition(new HashSet<>(position));
        value.setStatus(status == null ? Domain.NOT_POLLUTED : status.copy());
        value.setType(new HashSet<>(type));
        value.setFields(new HashMap<>(fields));
        return value;
    }

    public void addField(String fieldName, String handle) {
        Set<String> handles = new HashSet<>();
        handles.add(handle);
        addField(fieldName, handles);
    }

    public void addFieldWithClean(String fieldName, String handle) {
        Set<String> handles = new HashSet<>();
        handles.add(handle);
        fields.put(fieldName, handles);
    }

    public Set<String> getFieldHandle(String fieldName) {
        return fields.getOrDefault(fieldName, new HashSet<>());
    }

    public void addArray(String arrayHandle) {
        if (arrays.size() < GlobalConfiguration.ARRAY_MAX_LENGTH) {
            arrays.add(arrayHandle);
        }
    }

    public void addArray(Set<String> arrayHandles) {
        if (arrays.size() < GlobalConfiguration.ARRAY_MAX_LENGTH) {
            arrays.addAll(arrayHandles);
        }
    }

    public void addField(String field, Set<String> handles) {
        if (fields.containsKey(field)) {
            fields.get(field).addAll(handles);
        } else {
            fields.put(field, handles);
        }
    }

    /**
     * union 处理两个uuid相等的value
     */
    public void union(SimpleValue other) {
        if (this == other || this.equals(other)) return;

        if (other != null) {
            type.addAll(other.getType());

//            fields.putAll(other.getFields());
            for (Map.Entry<String, Set<String>> entry : other.getFields().entrySet()) {
                if (fields.containsKey(entry.getKey())) {
                    fields.get(entry.getKey()).addAll(entry.getValue());
                } else {
                    fields.put(entry.getKey(), new HashSet<>(entry.getValue()));
                }
            }

            position.addAll(other.position);

            arrays.addAll(other.getArrays());

            status = Domain.merge(getStatus(), other.getStatus());
        }
    }

    public boolean isPolluted() {
        return status != null && status.isPolluted();
    }

    @Override
    public String toString() {
        return "SimpleValue{" +
                "uuid='" + uuid + '\'' +
                ", type=" + type +
                ", position=" + position +
                ", fields=" + fields +
                ", status=" + status +
                ", arrays=" + arrays +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        SimpleValue value = (SimpleValue) o;

        return new EqualsBuilder()
                .append(position, value.position)
//                .append(isConstant, value.isConstant)
//                .append(isConstantArray, value.isConstantArray)
//                .append(isArray, value.isArray)
//                .append(isCollection, value.isCollection)
//                .append(isLocked, value.isLocked)
//                .append(isClass, value.isClass)
//                .append(isThis, value.isThis)
//                .append(isField, value.isField)
//                .append(isStatic, value.isStatic)
//                .append(isInterface, value.isInterface)
//                .append(isEmpty, value.isEmpty)
                .append(uuid, value.uuid).append(type, value.type)
                .append(fields, value.fields).append(status, value.status)
                .append(constantValue, value.constantValue).append(arrays, value.arrays).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(uuid).append(type).append(position).append(fields)
                .append(status)
//                .append(isConstant).append(isConstantArray).append(isArray)
                .append(constantValue).append(arrays)
//                .append(isThis).append(isField)
//                .append(isLocked)
//                .append(isClass)
//                .append(isCollection).append(isStatic).append(isInterface).append(isEmpty)
                .toHashCode();
    }
}
