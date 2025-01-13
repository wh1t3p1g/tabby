package tabby.analysis.data;

import com.google.common.collect.ImmutableSet;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import soot.*;
import soot.jimple.Constant;
import soot.jimple.InstanceFieldRef;
import soot.jimple.NullConstant;
import soot.jimple.StaticFieldRef;
import tabby.analysis.container.ValueContainer;
import tabby.common.bean.ref.ClassReference;
import tabby.common.utils.PositionUtils;
import tabby.common.utils.SemanticUtils;

import java.util.*;

/**
 * @author wh1t3p1g
 * @since 2021/11/30
 */
@Getter
@Setter
@Slf4j
public class SimpleObject {
    // 基础信息，在分析过程中不会变化
    private String name;
    /**
     * 当前Object的类型
     * 为固定值，仅解析assign左值时生成
     */
    private String type;
    /**
     * 当前变量是否是static，这意味着正常非static函数是无法修改该变量的，属于不可控对象
     */
    private boolean isStatic = false;
    /**
     * 当前变量是否是接口类型
     */
    private boolean isInterface = false;
    /**
     * 当前变量是否常量类型，包括String、基础数据类型
     */
    private boolean isConstant = false;
    private boolean isConstantArray = false;
    /**
     * 当前变量为invoke的return值
     */
    private boolean isInvoke = false;
    private boolean isSimplifyInvoke = false;
    private Map<String, Set<String>> invokeTargets = new HashMap<>();
    /**
     * 当前变量是否为array类型，包括正常的int[]，也包括java的一些list set等array类型
     * array 类型 对于添加到array里的对象，也类似field一样处理
     */
    private boolean isArray = false;
    private boolean isCollection = false;
    private boolean isNull = false;
    private boolean isSource = false;
    /**
     * 当前域下的this对象绑定
     */
    private boolean isThis = false;
    private boolean isField = false;
    /**
     * 当前域下的param绑定
     */
    private boolean isParam = false;
    private int index = -3; // param index
    private String declaringClass; // field 才有

    // 指向信息
    /**
     * 指向value的唯一句柄，仅在赋值的情况改变 uuid
     */
    private Set<String> handle = new HashSet<>();

    /**
     * 防止递归
     */
    private boolean isLocked = false;


    public SimpleObject clone() {
        SimpleObject obj = new SimpleObject();

        obj.setArray(isArray);
        obj.setStatic(isStatic);
        obj.setCollection(isCollection);
        obj.setConstant(isConstant);
        obj.setConstantArray(isConstantArray);
        obj.setInterface(isInterface);

        obj.setField(isField);

        obj.setThis(isThis);
        obj.setParam(isParam);
        obj.setIndex(index);
        obj.setNull(isNull);
        obj.setSimplifyInvoke(isSimplifyInvoke);
        obj.setSource(isSource);

        obj.setName(name);
        obj.setType(type);
        obj.setHandle(new HashSet<>(handle));
        obj.setInvoke(isInvoke);
        obj.setInvokeTargets(new HashMap<>(invokeTargets));
        obj.setDeclaringClass(declaringClass);
        return obj;
    }

    @Deprecated
    public static SimpleObject newInstance(Value value) {
        SimpleObject obj = new SimpleObject();
        obj.setName(SemanticUtils.extractValueName(value));
        return obj;
    }

    public static SimpleObject makeLocalObject(Local local, boolean isArray, ValueContainer container) {
        SimpleObject obj = new SimpleObject();
        SimpleValue val = SimpleValue.makeLocalValue(local, isArray);

        obj.setName(local.getName());
        obj.setArray(isArray || val.isArray);
        obj.setCollection(val.isCollection());
        obj.setInterface(val.isInterface());
        obj.setConstantArray(val.isConstantArray());
        obj.setConstant(val.isConstant());
        obj.setType(local.getType().toString());

        container.store(obj, val);
        return obj;
    }

    public static SimpleObject makeFieldObject(SimpleObject baseObj, SootField field,
                                               boolean isArray, boolean isNeedToAdd, boolean isAllowDuplicationCreation,
                                               ValueContainer container) {
        SimpleObject fieldObj = new SimpleObject();
        SimpleValue fieldVal = SimpleValue.makeFieldValue(field, isArray);
        String name = baseObj.getName() + "." + field.getSignature();
        fieldObj.setField(true);
        fieldObj.setName(name);
        fieldObj.setType(field.getType().toString());
        fieldObj.setConstant(fieldVal.isConstant());
        fieldObj.setConstantArray(fieldVal.isConstantArray());
        fieldObj.setInterface(fieldVal.isInterface());
        fieldObj.setArray(isArray || fieldVal.isArray());
        fieldObj.setCollection(fieldVal.isCollection());
        fieldObj.setSource(baseObj.isSource());
        fieldObj.setParam(baseObj.isParam());
        fieldObj.setDeclaringClass(field.getDeclaringClass().toString());

        if (isAllowDuplicationCreation) {
            container.modifyUuid(fieldObj);
            container.modifyUuid(fieldVal);
        } else if (container.checkUuid(fieldVal)) {
            fieldVal.setUuid(baseObj.getName() + "." + fieldVal.getUuid());
            if (container.checkUuid(fieldVal)) {
                return SimpleObject.makeNullObject(container);
            }
        }

        fieldVal.getPosition().addAll(container.getPositionsOnObj(baseObj)); // 只继承当前baseObj的状态，包括本身和array，但不包括field本身
        fieldVal.setStatus(container.getObjectStatus(baseObj)); // 继承状态
        fieldVal.setSource(baseObj.isSource());
        fieldVal.setStatic(baseObj.isStatic());
        fieldVal.setThis(baseObj.isThis());
        fieldObj.setThis(baseObj.isThis());
        fieldObj.setStatic(baseObj.isStatic());
        if (isNeedToAdd) {
            container.addObjectField(baseObj, field.getName(), fieldObj.getName());
        }

        container.store(fieldObj, fieldVal);

        return fieldObj;
    }


    public static SimpleObject tryToMakeInstanceField(SimpleObject baseObj, String classname, String fieldName, ValueContainer container) {
        if (classname == null) return null;

        if (classname.endsWith("[]")) {
            classname = classname.substring(0, classname.length() - 2);
        }
        SootClass cls = SemanticUtils.getSootClass(classname);
        SootField field = SemanticUtils.getField(cls, fieldName);

        if (field == null && (cls.isInterface() || cls.isAbstract())) {
            // try to fetch from child classes
            ClassReference clsRef = container.getDataContainer().getClassRefByName(classname, true);
            if (clsRef != null && clsRef.getChildClassnames().size() > 0) {
                Set<String> classnames = ImmutableSet.copyOf(clsRef.getChildClassnames());
                for (String child : classnames) {
                    field = SemanticUtils.getField(child, fieldName);
                    if (field != null) break;
                }
            }
        }

        if (field != null) {
            return SimpleObject.makeFieldObject(baseObj, field, false, false, false, container);
        }
        return null;
    }

    public static SimpleObject makeObjectInstanceField(String fieldName, SimpleValue value, ValueContainer container) {
        SimpleObject fieldObj = new SimpleObject();
        SimpleValue fieldVal = SimpleValue.makePhantomFieldValue(fieldName, value);
        String name = value.getUuid() + "." + fieldName;
        fieldObj.setField(true);
        fieldObj.setName(name);
        fieldObj.setType("java.lang.Object");
        fieldObj.setDeclaringClass("java.lang.Object");
        container.store(fieldObj, fieldVal);
        return fieldObj;
    }

    /**
     * @param ifr
     * @param isArray
     * @param container
     * @return
     */
    public static SimpleObject makeInstanceFieldObject(InstanceFieldRef ifr,
                                                       boolean isArray, boolean isAllowDuplicationCreation,
                                                       ValueContainer container) {
        Value base = ifr.getBase();
        SimpleObject baseObj = container.getObject(base);
        if (baseObj == null && base instanceof Local) { // 不考虑base为其他类型的情况，一般不会出现这个问题
            // 这里代码跟makeLocalObject一样，只是为了减少value的查询，重复写这部分代码
            baseObj = new SimpleObject();
            SimpleValue baseVal = SimpleValue.makeLocalValue((Local) base, isArray);
            baseObj.setName(((Local) base).getName());
            baseObj.setArray(isArray || baseVal.isArray());
            baseObj.setCollection(baseVal.isCollection());
            baseObj.setInterface(baseVal.isInterface());
            baseObj.setConstant(baseVal.isConstant());
            baseObj.setConstantArray(baseVal.isConstantArray());
            baseObj.setType(base.getType().toString());

            container.store(baseObj, baseVal);
        }

        if (baseObj != null) {
            SootField field = null;
            try {
                field = Objects.requireNonNull(SemanticUtils.getField(ifr));
                return makeFieldObject(baseObj, field, isArray, true, isAllowDuplicationCreation, container);
            } catch (NullPointerException e) {
                log.error(ifr + " field not found");
                log.error("make instance field object error!");
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        return null; // 一般不会出现这个null的返回
    }

    public static SimpleObject makeNullObject(ValueContainer container) {
        SimpleObject obj = new SimpleObject();
        obj.setName(PositionUtils.NULL_STRING);
        SimpleValue val = SimpleValue.makeNullValue();
        obj.setConstant(true);
        obj.setNull(true);
        container.store(obj, val);
        return obj;
    }

    public static SimpleObject makeGotoObject(Unit u, ValueContainer container){
        SimpleObject obj = container.getObject(u.toString());

        if(obj == null){
            obj = new SimpleObject();
            obj.setName(u.toString());
            container.addObjectToContainer(obj);
        }

        return obj;
    }

    public static SimpleObject makeSourceObject(ValueContainer container) {
        SimpleObject obj = new SimpleObject();
        obj.setName("source-object");
        SimpleValue val = SimpleValue.makeSourceValue();
        obj.setSource(true);
        val.setStatus(Domain.POLLUTED);
        val.getType().add("source");
        obj.setType("source");
        container.store(obj, val);
        return obj;
    }

    public static SimpleObject makeObjectByString(ValueContainer container, String value, String type, int position) {
        SimpleObject obj = new SimpleObject();
        obj.setName(value);
        SimpleValue val = SimpleValue.makeValueByString(value, position);
        obj.setSource(true);
        val.setStatus(Domain.POLLUTED);
        val.getType().add(type);
        obj.setType(type);
        container.store(obj, val);
        return obj;
    }

    public static SimpleObject makeRandomArrayObject() {
        SimpleObject obj = new SimpleObject();
        obj.setName(UUID.randomUUID().toString());
        return obj;
    }

    public static SimpleObject makeStaticFieldObject(StaticFieldRef sfr, SootField field,
                                                     boolean isArray, boolean isAllowDuplicationCreation,
                                                     ValueContainer container) {
        // 创建 field value
        SimpleObject obj = new SimpleObject();
        obj.setName(sfr.toString());
        obj.setStatic(true);
        obj.setType(field.getType().toString());
        SimpleValue fieldVal = SimpleValue.makeFieldValue(field, isArray);
        obj.setConstant(fieldVal.isConstant());
        obj.setConstantArray(fieldVal.isConstantArray());
        obj.setInterface(fieldVal.isInterface());
        obj.setArray(isArray || fieldVal.isArray());
        obj.setCollection(fieldVal.isCollection());

        if (isAllowDuplicationCreation) {
            container.modifyUuid(obj);
            container.modifyUuid(fieldVal);
        } else if (container.checkUuid(fieldVal)) {
            // 对于静态属性 只能创建一次
            return SimpleObject.makeNullObject(container);
        }

        container.store(obj, fieldVal);
        return obj;
    }

    public static SimpleObject makeConstantObject(Constant constant, ValueContainer container) {
        if (constant instanceof NullConstant) {
            return makeNullObject(container);
        }
        SimpleObject obj = new SimpleObject();
        obj.setName(constant.toString());
        obj.setConstant(true);
        obj.setType(constant.getType().toString());
        SimpleValue val = SimpleValue.makeConstantValue(constant);

        container.store(obj, val);
        return obj;
    }

    public static SimpleObject makeInvokeReturnObject(String name) {
        SimpleObject obj = new SimpleObject();
        obj.setName(name);
        obj.setInvoke(true);
        return obj;
    }

    public void addInvokeTargets(String key, String value) {
        if (invokeTargets.containsKey(key)) {
            invokeTargets.get(key).add(value);
        } else {
            Set<String> set = new HashSet<>();
            set.add(value);
            invokeTargets.put(key, set);
        }
    }

    public void addInvokeTargets(String key, Set<String> value) {
        if (invokeTargets.containsKey(key)) {
            invokeTargets.get(key).addAll(value);
        } else if (value.size() > 0) {
            Set<String> set = new HashSet<>(value);
            invokeTargets.put(key, set);
        }
    }

    @Override
    public String toString() {
        return "SimpleObject{" +
                "name='" + name + '\'' +
                ", handle='" + handle + '\'' +
                '}';
    }

    public static boolean isConstantObject(SimpleObject obj) {
        if (obj == null) return false;
        if (obj instanceof MultiObject) {
            for (SimpleObject o : ((MultiObject) obj).getObjects()) {
                if (o.isConstant()) {
                    return true;
                }
            }
            return false;
        } else {
            return obj.isConstant();
        }
    }

    public static boolean isConstantArrayObject(SimpleObject obj) {
        if (obj instanceof MultiObject) {
            for (SimpleObject o : ((MultiObject) obj).getObjects()) {
                if (o.isConstantArray()) {
                    return true;
                }
            }
            return false;
        } else {
            return obj.isConstantArray();
        }
    }

    public static SimpleObject makeSpecialObject(int pos, ValueContainer container) {
        if (pos == PositionUtils.SOURCE) {
            return SimpleObject.makeSourceObject(container);
        } else if (pos == PositionUtils.DAO) {
            return SimpleObject.makeObjectByString(container, "dao-object", PositionUtils.DAO_STRING, PositionUtils.DAO);
        } else if (pos == PositionUtils.RPC) {
            return SimpleObject.makeObjectByString(container, "rpc-object", PositionUtils.RPC_STRING, PositionUtils.RPC);
        } else if (pos == PositionUtils.AUTH) {
            return SimpleObject.makeObjectByString(container, "auth-object", PositionUtils.AUTH_STRING, PositionUtils.AUTH);
        }else if(pos == PositionUtils.NULL_TYPE){
            SimpleObject nullObj = container.getObject(PositionUtils.NULL_STRING);
            if(nullObj == null){
                nullObj = SimpleObject.makeNullObject(container);
            }
            return nullObj;
        } else {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        SimpleObject object = (SimpleObject) o;

        return new EqualsBuilder().append(isStatic, object.isStatic)
                .append(isInterface, object.isInterface)
                .append(isConstant, object.isConstant)
                .append(isConstantArray, object.isConstantArray)
                .append(isInvoke, object.isInvoke)
                .append(isArray, object.isArray)
                .append(isCollection, object.isCollection)
                .append(isThis, object.isThis).append(isField, object.isField)
                .append(isParam, object.isParam).append(index, object.index)
//                .append(isLocked, object.isLocked)
                .append(isSource, object.isSource)
                .append(name, object.name)
                .append(type, object.type).append(invokeTargets, object.invokeTargets)
                .append(handle, object.handle).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(name).append(type).append(isStatic).append(isInterface)
                .append(isConstant).append(isConstantArray).append(isInvoke).append(invokeTargets)
                .append(isArray).append(isCollection).append(isThis).append(isField)
                .append(isParam).append(index).append(handle)
//                .append(isLocked)
                .toHashCode();
    }

    public boolean isNotEmpty() {
        return true;
    }
}
