package tabby.analysis.container;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import soot.*;
import soot.jimple.ArrayRef;
import soot.jimple.Constant;
import soot.jimple.InstanceFieldRef;
import soot.jimple.StaticFieldRef;
import tabby.analysis.data.*;
import tabby.analysis.switcher.ValueSwitcher;
import tabby.common.utils.PositionUtils;
import tabby.common.utils.SemanticUtils;
import tabby.config.GlobalConfiguration;
import tabby.core.container.DataContainer;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

import static tabby.analysis.ActionWorker.*;

/**
 * @author wh1t3p1g
 * @since 2021/11/26
 */
@Getter
@Setter
@Slf4j
public class ValueContainer implements AutoCloseable {

    private Map<String, SimpleValue> localValues = null; // uuid:value
    private Map<String, SimpleObject> localObjects = null; // name:object

    private Map<String, SimpleValue> globalValues = null; // uuid:value
    private Map<String, SimpleObject> globalObjects = null; // name:object 静态变量，用于传递给下一层
    private SimpleObject[] objects = null; // 临时的列表
    private DataContainer dataContainer;
    /**
     * 变量的直接指向集
     */
    private Map<String, Set<String>> pointToSet = null;
    private ValueSwitcher.ValueParser valueParser = null;

    public ValueContainer() {
        this.localObjects = new HashMap<>();
        this.localValues = new HashMap<>();
        this.globalObjects = new HashMap<>();
        this.globalValues = new HashMap<>();
        this.pointToSet = new HashMap<>();
        this.valueParser = new ValueSwitcher.ValueParser(this, true);

        this.valueParser.setPrimTypeNeedToCreate(GlobalConfiguration.IS_PRIM_TYPE_NEED_TO_CREATE);
    }

    public ValueContainer(DataContainer dataContainer) {
        this();
        this.dataContainer = dataContainer;
    }

    public ValueContainer createSubContainer() {
        ValueContainer container = new ValueContainer();
        container.setGlobalObjects(globalObjects);
        container.setGlobalValues(globalValues);
        container.setDataContainer(dataContainer);
        return container;
    }

    public void store(SimpleObject obj, SimpleValue value) {
        obj.getHandle().add(value.getUuid());
        if (obj.isStatic()) {
            globalObjects.put(obj.getName(), obj);
            globalValues.put(value.getUuid(), value);
        } else {
            localObjects.put(obj.getName(), obj);
            localValues.put(value.getUuid(), value);
        }
    }

    public boolean checkUuid(Object obj) {
        if (obj instanceof SimpleObject) {
            String handle = ((SimpleObject) obj).getName();
            if (localObjects.containsKey(handle)
                    || (((SimpleObject) obj).isStatic() && globalObjects.containsKey(handle))) {
                return true;
            }
        } else if (obj instanceof SimpleValue) {
            String uuid = ((SimpleValue) obj).getUuid();
            if (localValues.containsKey(uuid) || globalValues.containsKey(uuid)) {
                return true;
            }
        }
        return false;
    }

    public void modifyUuid(Object obj) { // 仅允许新增一次
        if (obj instanceof SimpleObject) {
            String handle = ((SimpleObject) obj).getName();
            if (localObjects.containsKey(handle)
                    || (((SimpleObject) obj).isStatic() && globalObjects.containsKey(handle))) {
                ((SimpleObject) obj).setName(handle + "_1");
            }
        } else if (obj instanceof SimpleValue) {
            String uuid = ((SimpleValue) obj).getUuid();
            if (localValues.containsKey(uuid) || globalValues.containsKey(uuid)) {
                ((SimpleValue) obj).setUuid(uuid + "_1");
            }
        }
    }

    public void removeObject(String handle) {
        if (localObjects.containsKey(handle)) {
            localObjects.remove(handle);
        } else {
            globalObjects.remove(handle);
        }
    }

    public SimpleObject getOrAdd(Value value, boolean isAllowDuplicationCreation) {
        valueParser.setContainer(this);
        valueParser.setResult(null);
        valueParser.setAllowDuplicationCreation(isAllowDuplicationCreation);
        value.apply(valueParser);
        valueParser.setAllowDuplicationCreation(false);
        return (SimpleObject) valueParser.getResult();
    }

    public MultiObject getInstanceFieldObject(InstanceFieldRef ifr) {
        SootFieldRef fieldRef = ifr.getFieldRef();
        Value base = ifr.getBase();
        SimpleObject baseObj = getObject(base); // 正常情况不存在 A.b.c 会被拆成 d = A.b; d.c; 所以return的要不是null就是SimpleObject
        return getInstanceFieldObject(baseObj, fieldRef.name(), false);
    }

    private MultiObject getInstanceFieldObject(SimpleObject baseObj, String fieldName, boolean isNeedFromArray) {
        MultiObject objs = new MultiObject();
        if (baseObj != null && !isNull(baseObj)) {
            Set<String> fieldHandles = new HashSet<>();

            for (String handle : baseObj.getHandle()) {
                SimpleValue val = getValue(handle);
                if (val != null) {
                    if (val.isNull()) {
                        fieldHandles.add(PositionUtils.NULL_STRING);
                        continue;
                    }
                    if (isNeedFromArray) {
                        for (String arrayHandle : val.getArrays()) {
                            SimpleValue arrayVal = getValue(arrayHandle);

                            if (arrayVal == null || arrayVal.isNull()) continue;

                            if (arrayVal.getFields().containsKey(fieldName)) {
                                fieldHandles.addAll(arrayVal.getFieldHandle(fieldName));
                            }
                        }
                    } else if (val.getFields().containsKey(fieldName)) {
                        fieldHandles.addAll(val.getFieldHandle(fieldName));
                    }
                }
            }

            for (String handle : fieldHandles) {
                SimpleObject fieldObj = getObject(handle);
                if (fieldObj != null) {
                    objs.addObject(fieldObj);
                }
            }
        }
        return objs.size() > 0 ? objs : null;
    }

    /**
     * 可以处理简单的
     * 也可以处理多层的
     *
     * @param name
     * @return
     */
    public MultiObject getInstanceFieldObjects(String name, boolean isNeedCreate) {
        String cleanedName = SemanticUtils.cleanTag(name, STATUS_FEATURE, MERGE_FEATURE);
        String[] pos = cleanedName.split(FIELD_FEATURE);
        String basePos = pos[0].replace(ARRAY_FEATURE, "");
        SimpleObject baseObj = getObject(basePos);
        if (baseObj == null || isNull(baseObj)) return null;

        MultiObject fieldObjs = new MultiObject();
        MultiObject lastFieldObjs = null;
        fieldObjs.addObject(baseObj);

        if ("source-object".equals(baseObj.getName())) {
            // source object 有且仅有一个
            return fieldObjs;
        }
        boolean isNeedFromArray = pos[0].endsWith(ARRAY_FEATURE);

        for (int i = 1; i < pos.length; i++) {
            String fieldName = pos[i].replace(ARRAY_FEATURE, "");
            MultiObject temp = new MultiObject();
            lastFieldObjs = fieldObjs;
            // try to find
            for (SimpleObject obj : fieldObjs.getObjects()) {
                MultiObject fields = getInstanceFieldObject(obj, fieldName, isNeedFromArray);
                temp.addObjects(fields);
            }
            if (temp.size() == 0 && isNeedCreate) {
                // try to add new field obj
                SimpleObject newFieldObj = null;
                for (SimpleObject obj : lastFieldObjs.getObjects()) {
                    if (obj == null || isNull(obj)) continue;
                    if (newFieldObj == null) {
                        String type = obj.getType();
                        newFieldObj = SimpleObject.tryToMakeInstanceField(obj, type, fieldName, this);

                        if (newFieldObj == null) {
                            for (String handle : obj.getHandle()) {
                                SimpleValue val = getValue(handle);
                                if (val != null) {
                                    Set<String> types = val.getType();
                                    for (String t : types) {
                                        newFieldObj = SimpleObject.tryToMakeInstanceField(obj, t, fieldName, this);
                                        if (newFieldObj != null) break;
                                    }
                                }
                            }
                        }
                    }
                    if (newFieldObj != null) {
                        if (isNeedFromArray) {
                            // 添加到array数组上
                            addObjectArrayField(obj, fieldName, newFieldObj.getName());
                        } else {// 添加到obj上
                            addObjectField(obj, fieldName, newFieldObj.getName());
                        }
                    }
                }
                if (newFieldObj != null) {
                    temp.addObject(newFieldObj);
                }
            }
            fieldObjs = temp;
            isNeedFromArray = pos[i].endsWith(ARRAY_FEATURE);
        }
        return fieldObjs;
    }

    public Set<String> getObjectTypes(SimpleObject obj) {
        if (obj == null) return new HashSet<>();

        String left = obj.getType();
        Set<String> rights = new HashSet<>();


        for (String handle : obj.getHandle()) {
            SimpleValue value = getValue(handle);
            if (value != null) {
                rights.addAll(value.getType());
            }
        }

        if (rights.size() > 1) {
            rights.remove("java.lang.Object");
        }

        if (rights.isEmpty()) { // 优先信任右值类型
            rights.add(left);
        }

        return rights;
    }

    public SimpleObject getObject(Value value) {
        if (value instanceof Local) {
            return getObject(((Local) value).getName());
        } else if (value instanceof StaticFieldRef) {
            return getObject(value.toString());
        } else if (value instanceof InstanceFieldRef) {
            return getInstanceFieldObject((InstanceFieldRef) value);
        } else if (value instanceof ArrayRef) {
            ArrayRef ar = (ArrayRef) value;
            Value base = ar.getBase();
            return getObject(base);
        } else if (value instanceof Constant) {
            return getObject(value.toString());
        }
        return null;
    }

    public SimpleObject getObject(String name) {
        if ("source".equals(name)) {
            name = "source-object";
        }
        if (localObjects.containsKey(name)) {
            return localObjects.getOrDefault(name, null);
        } else if (globalObjects.containsKey(name)) {
            return globalObjects.getOrDefault(name, null);
        }
        return null;
    }

    public Domain getObjectStatus(SimpleObject obj) {
        Domain domain = null;
        if (obj instanceof MultiObject) {
            for (SimpleObject o : ((MultiObject) obj).getObjects()) {
                Domain ret = getObjectStatus(o);
                if (domain == null) {
                    domain = ret;
                } else if (ret != null) {
                    domain = Domain.merge(domain, ret);
                }
            }
        } else {
            for (String handle : obj.getHandle()) {
                SimpleValue val = getValue(handle);
                Domain valDomain = getValueStatus(val);
                if (domain == null) {
                    domain = valDomain;
                } else {
                    domain = Domain.merge(domain, valDomain);
                }
            }
        }
        if (domain == null) {
            domain = Domain.NOT_POLLUTED;
        }
        return domain;
    }

    public Domain getValueStatus(SimpleValue value) {
        if (value == null) return Domain.NOT_POLLUTED;
        value.setLocked(true);
        Domain valDomain = value.getStatus();

        if (value.isArray()) {
            for (String arrayHandle : value.getArrays()) {
                SimpleValue arrayVal = getValue(arrayHandle);
                if (arrayVal != null && !arrayVal.isLocked()) {
                    valDomain = Domain.merge(valDomain, getValueStatus(arrayVal));
                }
            }
        }
        value.setLocked(false);
        return valDomain;
    }

    public SimpleValue getValue(String uuid) {
        if (localValues.containsKey(uuid)) {
            return localValues.getOrDefault(uuid, null);
        }
        return globalValues.getOrDefault(uuid, null);
    }

    public void bindThisOrArg(SimpleObject obj, int index) {
        if (obj != null) {
            obj.setThis(index == -1);
            obj.setParam(index >= 0);
            obj.setIndex(index);
            for (String handle : obj.getHandle()) {
                SimpleValue val = getValue(handle);
                if (val != null) {
                    val.setStatus(Domain.POLLUTED);
                    val.getPosition().add(index);
                    val.setThis(obj.isThis());
                }
            }
        }
    }

    public Set<String> getRelatedObjectsFromPTS(String name) {
        return getRelatedObjectsFromPTS(name, pointToSet);
    }

    public String removeEndTag(String name){
        if(name.endsWith(">")){
            return name.substring(0, name.length()-3);
        }
        return name;
    }

    public Set<String> getRelatedObjectsFromPTS(String name, Map<String, Set<String>> data) {
        Set<String> ret = new HashSet<>();
        String cleanName = removeEndTag(name);
        String ptWithField = cleanName + "<f>";
        String ptWithArray = cleanName + "<a>";
        String ptWithRest = cleanName + "<s>";
        String ptWithAppend = cleanName + "<m>";

        Set<String> keys = data.keySet();
        for (String key : keys) {
            if (key.equals(name)
                    || key.startsWith(ptWithRest)
                    || key.startsWith(ptWithAppend)
                    || key.startsWith(ptWithField)
                    || key.startsWith(ptWithArray)) {
                ret.add(key);
            }
        }
        return ret;
    }

    public boolean isThisObj(SimpleObject obj) { // 检查当前value是否为this对象，且非this的field对象
        if (obj == null) return false;

        for (String handle : obj.getHandle()) {
            SimpleValue val = getValue(handle);
            if (val != null && val.isThis() && !val.isField()) {
                return true;
            }
        }

        return false;
    }

    public boolean isThisObjField(SimpleObject obj) {
        if (obj == null) return false;

        for (String handle : obj.getHandle()) {
            SimpleValue val = getValue(handle);
            if (val != null && val.isField()) {
                if (val.isThis() || val.getPosition().contains(PositionUtils.THIS)) {
                    return true;
                }
            }
        }

        return false;
    }

    public Set<Integer> getPositions(SimpleObject obj, boolean flag) {
        Set<Integer> retData = getPositions(obj, new HashSet<>());
        if (flag && retData.isEmpty()) {
            retData.add(PositionUtils.NOT_POLLUTED_POSITION);
        }
        return retData;
    }

    public Set<Integer> getPositions(SimpleObject obj, Set<String> visited) {
        Set<Integer> retData = new HashSet<>();
        if (obj == null || visited.contains(obj.getName())) return retData;

        visited.add(obj.getName());
        if (obj instanceof MultiObject) {
            for (SimpleObject o : ((MultiObject) obj).getObjects()) {
                retData.addAll(getPositions(o, visited));
            }
        } else {
            for (String handle : obj.getHandle()) {
                SimpleValue val = getValue(handle);
                retData.addAll(getPositions(val, visited));
            }
        }

        return retData;
    }

    public Set<Integer> getPositionsOnObj(SimpleObject obj) {
        Set<Integer> retData = new HashSet<>();
        if (obj instanceof MultiObject) {
            for (SimpleObject o : ((MultiObject) obj).getObjects()) {
                retData.addAll(getPositionsOnObj(o));
            }
        } else {
            for (String handle : obj.getHandle()) {
                SimpleValue val = getValue(handle);
                if (val != null) {
                    retData.addAll(val.getPosition());
                }
            }
        }

        return retData;
    }

    public Set<Integer> getPositions(SimpleValue val, Set<String> visited) {
        Set<Integer> retData = new HashSet<>();
        if (val == null || visited.contains(val.getUuid())) return retData;
        visited.add(val.getUuid());
        // 变量自身
        retData.addAll(val.getPosition());
        // array类型
        if (val.isArray()) {
            for (String arrayHandle : val.getArrays()) {
                SimpleValue arrayVal = getValue(arrayHandle);
                retData.addAll(getPositions(arrayVal, visited));
            }
        }
        // 收集field
        for (Set<String> fieldHandles : val.getFields().values()) {
            for (String fieldHandle : fieldHandles) {
                SimpleObject fieldObj = getObject(fieldHandle);
                retData.addAll(getPositions(fieldObj, visited));
            }
        }
        return retData;
    }

    public void addObjectField(SimpleObject obj,
                               String fieldName, String fieldHandle) {
        if (isValueNull(obj)) return; // null 类型没有field
        for (String handle : obj.getHandle()) {
            SimpleValue val = getValue(handle);
            if (val != null && !val.isNull()) {
                val.addField(fieldName, fieldHandle);
            }
        }
    }

    public void addObjectArrayField(SimpleObject obj, String fieldName, String fieldHandle) {
        if (obj == null || isValueNull(obj)) return;

        for (String handle : obj.getHandle()) {
            SimpleValue val = getValue(handle);
            if (val != null && !val.isNull()) {
                if (val.getArrays().isEmpty()) { // 需要new一个
                    SootClass cls = SemanticUtils.getSootClass(obj.getType());
                    if (cls == null) continue;
                    SimpleValue newArrayVal =
                            SimpleValue.makeArrayValueWithType(obj.getName(), val, cls.getType());
                    val.addArray(newArrayVal.getUuid());
                    addValueToContainer(newArrayVal, obj.isStatic());
                } else {
                    for (String array : val.getArrays()) {
                        SimpleValue arrayVal = getValue(array);
                        if (arrayVal != null && !arrayVal.isNull()) {
                            arrayVal.addField(fieldName, fieldHandle);
                        }
                    }
                }
            }
        }
    }

    public boolean containsPollutedValue(List<ValueBox> valueBoxes) {
        for (ValueBox box : valueBoxes) {
            Value value = box.getValue();
            SimpleObject obj = getObject(value);
            if (obj != null && isPolluted(obj, new HashSet<>())) {
                return true;
            }
        }
        return false;
    }

    public Set<Integer> collectPositions(List<ValueBox> valueBoxes) {
        Set<Integer> position = new HashSet<>();
        for (ValueBox box : valueBoxes) {
            Value value = box.getValue();
            SimpleObject obj = getObject(value);
            if (obj != null) {
                for (String handle : obj.getHandle()) {
                    SimpleValue val = getValue(handle);
                    if (val != null && val.getPosition().size() > 0) {
                        position.addAll(val.getPosition());
                    }
                }
            }
        }
        position.remove(PositionUtils.NOT_POLLUTED_POSITION);
        return position;
    }

    /**
     * 获取当前obj 污点位置
     *
     * @param obj
     * @return
     */
    public Set<Integer> collectPollutedPositions(SimpleObject obj, Set<String> visited, boolean checkField) {
        Set<Integer> polluted = new HashSet<>();
        if (obj == null || visited.contains(obj.getName())) return polluted;

        visited.add(obj.getName());

        for (String handle : obj.getHandle()) {
            SimpleValue value = getValue(handle);
            polluted.addAll(collectPollutedPositions(value, visited, checkField));
        }

        polluted.remove(PositionUtils.NOT_POLLUTED_POSITION);
        return polluted;
    }

    public Set<Integer> collectPollutedPositions(SimpleValue val, Set<String> visited, boolean checkField) {
        Set<Integer> polluted = new HashSet<>();
        if (val == null || visited.contains(val.getUuid())) return polluted;
        visited.add(val.getUuid());
        polluted.addAll(val.getPosition());

        if (val.isArray()) {
            for (String arrayHandle : val.getArrays()) {
                SimpleValue array = getValue(arrayHandle);
                polluted.addAll(collectPollutedPositions(array, visited, checkField));
            }
        }

        if (checkField) {
            for (Set<String> handles : val.getFields().values()) {
                for (String handle : handles) {
                    SimpleObject obj = getObject(handle);
                    polluted.addAll(collectPollutedPositions(obj, visited, checkField));
                }
            }
        }

        return polluted;
    }

    public void updateStatus(Value value, Domain domain, Set<Integer> positions, boolean force) {
        SimpleObject obj = getObject(value);

        updateObjectStatus(obj, domain, positions, new HashSet<>(), force);
    }

    public void updateObjectStatus(SimpleObject obj, Domain domain, Set<Integer> positions, Set<String> visited, boolean force) {
        if (obj != null && !isValueNull(obj)) {
            if (obj instanceof MultiObject) {
                for (SimpleObject object : ((MultiObject) obj).getObjects()) {
                    updateObjectStatus(object, domain, positions, visited, force);
                }
            } else {
                if (visited.contains(obj.getName())) return;

                visited.add(obj.getName());
                for (String handle : obj.getHandle()) {
                    SimpleValue val = getValue(handle);
                    if (val != null) {
                        val.setPosition(new HashSet<>(positions));
                        updateValueStatus(val, domain, positions, visited, force);
                    }
                }
            }
        }
    }

    /**
     * 递归更新obj 对应可能value的污点状态
     * 包括其属性的value污点状态
     *
     * @param val
     * @param domain
     * @param force
     */
    public void updateValueStatus(SimpleValue val, Domain domain, Set<Integer> positions, Set<String> visited, boolean force) {
        if (val != null && !val.isNull() && !visited.contains(val.getUuid()) && (!val.isConstant() || force)) {// 常量还是保持其原有状态
            visited.add(val.getUuid());
            val.setStatus(domain);
            // field
            for (Set<String> fields : val.getFields().values()) {
                for (String field : fields) {
                    SimpleObject fieldObj = getObject(field);
                    updateObjectStatus(fieldObj, domain, positions, visited, force);
                }
            }
            // array
            if (val.isArray()) {
                for (String array : val.getArrays()) {
                    SimpleValue arrayVal = getValue(array);
                    if (arrayVal != null) {
                        updateValueStatus(arrayVal, domain, positions, visited, true);
                    }
                }
            }
        }
    }

    public void assign(SimpleObject left, Set<String> handles, boolean isStore, boolean isAppend) {
        if (left instanceof MultiObject) {
            for (SimpleObject o : ((MultiObject) left).getObjects()) {
                assign(o, handles, isStore, isAppend);
            }
        } else {
            if (handles.size() > 0) {
                if (isStore) {
                    // 对于数组类型，只增不减
                    for (String handle : left.getHandle()) {
                        SimpleValue val = getValue(handle);
                        if (val != null && !val.isNull()) {
                            val.addArray(handles);
                        }
                    }
                } else {
                    if (!isAppend) {
                        left.getHandle().clear();
                    }
                    left.getHandle().addAll(handles);
                }
            }
        }
    }

    /**
     * x = a;
     * add a -> x
     * a:{} => a:{x}
     * <p>
     * reverse data dependency graph
     * x = a;
     * add x -> a
     * x:{} => x:{a}
     * addToDataDependencyGraph
     *
     * @param left
     * @param right
     */
    public void addToPointToSet(String left, String right) {
        if (left == null || right == null) return;

        if (pointToSet.containsKey(left)) {
            pointToSet.get(left).add(right);
        } else {
            Set<String> tmp = new HashSet<>();
            tmp.add(right);
            pointToSet.put(left, tmp);
        }
    }

    public void addToPointToSet(String left, Set<String> rights) {
        if (rights == null || rights.isEmpty()) return;

        for (String right : rights) {
            addToPointToSet(left, right);
        }
    }

    public void setToPointToSet(String left, Set<String> rights) {
        if (rights == null || rights.isEmpty()) return;

        pointToSet.put(left, rights);
    }

    /**
     * y = x; => x:{y}
     * x.f = c.f => x:{y} c.f:{x.f}
     * x = a; => x:{y} a:{x}
     * x = b; => x:{y} b:{x} a:{}
     * remove all x from values
     * 跟x相关的都要移除，包括类属性的指向
     * a:{x} => a:{}
     *
     * @param obj
     */
    public void removeFromPointToSet(String obj) {
        if (obj == null) return;
        String cleaned = SemanticUtils.cleanTag(obj, STATUS_FEATURE, MERGE_FEATURE);
        Set<String> keys = new HashSet<>(pointToSet.keySet());
        String fTag = cleaned + FIELD_FEATURE;
        String aTag = cleaned + ARRAY_FEATURE;
        String sTag = cleaned + STATUS_FEATURE;
        String mTag = cleaned + MERGE_FEATURE;
        for (String key : keys) {
            if (key.equals(obj)
                    || key.startsWith(fTag)
                    || key.startsWith(sTag)
                    || key.startsWith(mTag)
                    || key.equals(aTag)) {
                pointToSet.remove(key);
            }
        }

    }

    /**
     * y = x; => x:{y}
     * x = a; => x:{y} a:{x}
     * x = b; => x:{y} a:{ } b:{x}
     * <p>
     * 假设当前语句 x = b;
     * 1. 剔除之前流向x的指向信息，removeFromDataDependencyGraph(x)
     * 2. 更新指向关系，即b:{x}, addToDataDependencyGraph(x, b)
     * 3. 更新x的handle，x的handle将指向b的handle
     * 4. 递归更新x集合指向，即更新所有跟x相关的指向，此时y的handle也等于b的handle
     * <p>
     * 这里一个变量指针不可能同时指向两个，数组类型允许多种来源
     *
     * @param lObj
     * @param rObj
     */
    public void propagate(String lName, SimpleObject lObj, boolean isStore, String rName, SimpleObject rObj, boolean isLoad) {
        if (rName.isEmpty()) return;
        // a[1] = a1; 忽略
        if (!isStore) {// 数组类型允许多种来源
            // 如果lName=a，则需要remove 所有跟a相关的指向，a,a.f,a[]
            // 如果lName=a.f，则需要remove 所有跟a.f相关的指向，a.f
            removeFromPointToSet(lName);
        }

        // 对于load的情况，不需要添加转递关系；a1 = a[1]
        // 这种情况，a1仅是当前状态的a数组的所有值，后续a数组发生变化，不会影响a1的值
        // 所以load的情况，不需要添加到ddg中

        boolean isRObjNull = isValueNull(rObj);
        Action lNameAction = Action.parse(lName);
        if (isStore) {
            lNameAction.setArray(true);
        }

        if (!isRObjNull) {
            // rName 不会有递归的情况 仅可能 a, a[], a.f
//            Map<String, String> outMap = generateObjectMap(this.objects, true);
            Action rNameAction = Action.parse(rName);
            // get rObj pts
            Set<String> targets = getPointToSetByAction(rNameAction, false);
            if (targets == null) {
                Set<Integer> positions = collectPollutedPositions(rObj, new HashSet<>(), true);
                targets = PositionUtils.getPositions(positions, PositionUtils.NOT_POLLUTED_POSITION);
                lNameAction.setStatus(true);
            }

            addToPointToSet(lNameAction.toString(), targets);
            // array1 = array2 需要将 array2<a> 的指向给 array1<a>
            if (!isStore && lObj.isArray() && rObj.isArray()) {
                rNameAction.setArray(true);
                lNameAction.setArray(true);
                targets = pointToSet.get(rNameAction.toString());
                addToPointToSet(lNameAction.toString(), targets);
            }

            Set<String> related = getRelatedObjectsFromPTS(rName);
            if(related.contains(rName)){
                related.remove(rName);
            }else{
                Action action = Action.parse(rName);
                action.setStatus(true);
                related.remove(action.toString());
            }
            for(String r:related){
                Action action = Action.parse(r);
                Set<String> pts = getPointToSetByAction(action, true);
                if(pts == null) continue;

                Action copied = lNameAction.clone();
                if(action.isHasField()){
                    copied.append(action.getSubAction());
                    addToPointToSet(copied.toString(), new CopyOnWriteArraySet<>(pts));
                }
            }

        }else{
            Set<String> nullTypes = new CopyOnWriteArraySet<>(Arrays.asList("null_type"));
            addToPointToSet(lNameAction.toString(), nullTypes);
        }

        // handle object assign
        if (lName.contains("<f>") && isRObjNull) {
            String[] pos = lName.split("<f>");
            setObjectFieldNull(pos[0], pos[1]);
        } else if (lName.contains("<f>") && isNull(lObj)) {
            // 取出来的a.f == null obj，直接忽略
        } else {
            // 如果是load，则获取array的handles，反之，则直接获取rObj的handles
            Set<String> handles = getHandles(lObj, rObj, isLoad);

            assign(lObj, handles, isStore, false);
        }
    }

    public String getNullValHandle(){
        SimpleObject obj = getObject(PositionUtils.NULL_STRING);
        if(obj == null){
            obj = SimpleObject.makeNullObject(this);
        }
        return obj.getHandle().toArray(new String[0])[0];
    }

    public void setObjectFieldNull(String baseName, String fieldName) {
        SimpleObject obj = getObject(baseName);

        if (obj == null || isValueNull(obj)) return;

        String nullValHandle = getNullValHandle();

        for (String handle : obj.getHandle()) {
            SimpleValue val = getValue(handle);

            if (val == null || val.isNull()) continue;

            if (val.getFields().containsKey(fieldName)) {
                Set<String> handles = val.getFieldHandle(fieldName);
                for(String filedObjHandle:handles){
                    SimpleObject fieldObj = getObject(filedObjHandle);
                    if(fieldObj == null) continue;
                    fieldObj.getHandle().clear();
                    fieldObj.getHandle().add(nullValHandle);
                }
            }
        }
    }

    /**
     * 处理invoke类型的传播
     *
     * @param lName
     * @param lObj
     * @param isStore
     * @param rObj
     */
    public void propagate(String lName, SimpleObject lObj, Value lop, boolean isStore, SimpleObject rObj) {
        if (!isStore) {
            removeFromPointToSet(lName);
        }
        Action leftAction = Action.parse(lName);
        if (rObj.isInvoke()) {
            Map<String, Set<String>> targets = rObj.getInvokeTargets();
            if (targets.size() > 0) {
                for (Map.Entry<String, Set<String>> entry : targets.entrySet()) {
                    String key = entry.getKey();
                    if ("return".equals(key)) {
                        addToPointToSet(lName, new HashSet<>(entry.getValue()));
                    } else if ("return<s>".equals(key)) {
                        leftAction.setStatus(true);
                        addToPointToSet(leftAction.toString(), new HashSet<>(entry.getValue()));
                    } else if ("return<a>".equals(key)) {
                        String temp = lName;
                        if (!lName.endsWith("<a>")) {
                            temp = lName + "<a>";
                        }
                        addToPointToSet(temp, new HashSet<>(entry.getValue()));
                    } else if (key.contains("<f>")) {
                        String name = SemanticUtils.replaceFirst(key, "return", lName);
                        addToPointToSet(name, new HashSet<>(entry.getValue()));
                    }
                }
            }
        }
        if (rObj.isSimplifyInvoke()) { // 对于模糊处理的调用，仅接受ret值的status 和 positions
            if (isValueNull(lObj) && lop != null) { // 如果当前变量被赋值为null时，需要将当前的lObj给删掉，重新建一个
                removeObject(lObj.getName());
                lObj = getOrAdd(lop, true);
            }
            Domain status = getObjectStatus(rObj);
            Set<Integer> positions = collectPollutedPositions(rObj, new HashSet<>(), true);
            setPollutedStatus(lObj, status, positions);
            propagateArrayAndField(lObj, rObj);
        } else {
            Set<String> handles = getHandles(null, rObj, false);
            assign(lObj, handles, isStore, false);
        }
    }

    public void propagateArrayAndField(SimpleObject lObj, SimpleObject rObj) {
        for (String leftHandle : lObj.getHandle()) {
            SimpleValue lVal = getValue(leftHandle);
            if (lVal == null) continue;
            for (String rightHandle : rObj.getHandle()) {
                SimpleValue rVal = getValue(rightHandle);
                if (rVal == null) continue;
                if (rVal.getArrays().size() > 0) {
                    lVal.getArrays().addAll(rVal.getArrays());
                }
                if (rVal.getFields().size() > 0) {
                    lVal.getFields().putAll(rVal.getFields());
                }
            }
        }
    }

    public Set<String> getHandles(SimpleObject lObj, SimpleObject rObj, boolean isLoad) {
        Set<String> handles = new HashSet<>();
        if (rObj instanceof MultiObject) {
            for (SimpleObject obj : ((MultiObject) rObj).getObjects()) {
                if (obj != null) {
                    handles.addAll(getHandles(lObj, obj, isLoad));
                }
            }
        } else {
            if (isLoad && !isNull(rObj)) {
                for (String handle : rObj.getHandle()) {
                    SimpleValue val = getValue(handle);
                    if (val != null && val.isArray()) {
                        Set<String> arrayHandles = val.getArrays();
                        if (arrayHandles.size() == 0 && val.isPolluted()) {
                            // 特殊情况：可能是param、this的情况，本身是可控的 但是数组类型从来没出现过。此时要创建一个fake 但是待状态的obj
                            SimpleValue phantomVal = null;
                            for (String lHandle : lObj.getHandle()) {
                                SimpleValue lVal = getValue(lHandle);
                                if (lVal != null) {
                                    phantomVal = SimpleValue.makePhantomArrayValue(rObj.getName(), val, lVal);
                                    break;
                                }
                            }
                            if (phantomVal != null) {
                                val.addArray(phantomVal.getUuid());
                                addValueToContainer(phantomVal, rObj.isStatic());
                                handles.add(phantomVal.getUuid());
                            }
                        } else {
                            handles.addAll(val.getArrays());
                        }
                    }
                }
            } else {
                handles.addAll(rObj.getHandle());
            }
        }
        return handles;
    }

    /**
     * 仅判断当前obj是否是null obj，不判断obj value 是否为null
     * @param obj
     * @return
     */
    public boolean isNull(SimpleObject obj) {
        if (obj == null || obj.isNull()) return true;

        boolean flag = false;

        if (obj instanceof MultiObject) {
            for (SimpleObject o : ((MultiObject) obj).getObjects()) {
                flag = isNull(o);
                if (!flag) {
                    break;
                }
            }
        }

        return flag;
    }

    public boolean isValueNull(SimpleObject obj) {
        if (obj == null || obj.isNull()) return true;

        boolean flag = true;

        if (obj instanceof MultiObject) {
            for (SimpleObject o : ((MultiObject) obj).getObjects()) {
                flag = isNull(o);
                if (!flag) {
                    break;
                }
            }
        } else {
            for (String handle : obj.getHandle()) {
                SimpleValue val = getValue(handle);
                if (val != null && !val.isNull()) { // 只要obj的handle里面有一个val为非null 就return false
                    flag = false;
                    break;
                }
            }
        }
        return flag;
    }

    public SimpleObject copyAndStore(SimpleObject origin, String rename) {
        SimpleObject cloned = origin.clone();
        Set<String> newHandles = new HashSet<>();

        for (String handle : cloned.getHandle()) {
            SimpleValue value = getValue(handle);
            if (value == null) continue;
            if (rename.endsWith("<a>") && value.isArray()) {
                for (String arrayHandle : value.getArrays()) {
                    SimpleValue array = getValue(arrayHandle);
                    if (array == null) continue;
                    SimpleValue copied = array.clone();
                    String uuid = null;
                    if (copied.getUuid().endsWith(rename)) {
                        uuid = copied.getUuid(); // 只能拷贝一次，第二次直接覆盖原来的
                    } else {
                        uuid = copied.getUuid() + "-" + rename;
                    }
                    copied.setUuid(uuid);
                    newHandles.add(uuid);
                    addValueToContainer(copied, false);
                }
            } else {
                SimpleValue copied = value.clone();
                String uuid = null;
                if (copied.getUuid().endsWith(rename)) {
                    uuid = copied.getUuid(); // 只能拷贝一次，第二次直接覆盖原来的
                } else {
                    uuid = copied.getUuid() + "-" + rename;
                }
                copied.setUuid(uuid);
                newHandles.add(uuid);
                addValueToContainer(copied, false);
            }

        }

        cloned.setName(rename);
        cloned.getHandle().clear();
        cloned.getHandle().addAll(newHandles);
        addObjectToContainer(cloned);
        return cloned;
    }

    public String cleanArrayTag(String str) {
        if (str.endsWith("<a>")) {
            return str.substring(0, str.length() - 3);
        }
        return str;
    }

    public void cleanUnnecessaryPts(Map<String, Set<String>> data) {
        if (data.size() < 2) return;
        // 清除一下情况
        // a -> this
        // a.f -> this.f
        // 本质上是一样的
        Map<String, Set<String>> copied = ImmutableMap.copyOf(data);
        for (Map.Entry<String, Set<String>> entry : copied.entrySet()) {
            String name = entry.getKey();
            Set<String> values = entry.getValue();
            Set<String> related = getRelatedObjectsFromPTS(name, data);
            related.remove(name);
            for (String r : related) {
                String subName = r.substring(name.length());
                if ("".equals(subName)) continue;
                Set<String> pts = new HashSet<>(data.get(r));

                for (String value : values) {
                    pts.remove(value + subName);
                }
                if (pts.isEmpty()) {
                    data.remove(r);
                } else {
                    data.put(r, pts);
                }
            }
            if (data.containsKey(name) && data.get(name).size() > 1) {
                data.get(name).remove("new_operation");
            }
        }
        // 聚合操作
        // a -> this,this.f,this.f2
        // 统一改为 a -> this
        Set<String> keys = new HashSet<>(data.keySet());

        for (String key : keys) {
            Set<String> values = new HashSet<>(data.get(key));
            if (values.size() > 1) {
                values = values.stream().sorted(Comparator.comparingInt(String::length)).collect(Collectors.toCollection(LinkedHashSet::new));
                for (String value : values) {
                    String fTag = cleanArrayTag(value) + "<f>";
                    String aTag = cleanArrayTag(value) + "<a>";
                    data.get(key).removeIf(
                            v -> (v.startsWith(fTag) || v.startsWith(aTag))
                                    && !v.equals(value));
                }
            }
        }
    }

    public Set<String> getPointToSetByAction(Action action, boolean isNeedFromStatusOrMerge) {
        Set<String> pts = new HashSet<>();
        if (action == null) return pts;
        // 直接查找
        pts = pointToSet.get(action.toString());
        if (pts != null) {
            return pts;
        }

        if (action.isHasField()) {
            // 处理field类型
            return getPointToSetByComplexAction(action, isNeedFromStatusOrMerge);
        } else {
            return getPointToSetBySimpleAction(action, isNeedFromStatusOrMerge);
        }
    }

    /**
     * 不包含 field
     *
     * @param action
     * @return
     */
    private Set<String> getPointToSetBySimpleAction(Action action, boolean isNeedFromStatusOrMerge) {
        Set<String> pts = null;

        if (action.isStatus() || action.isMerge()) {
            // 找 a<s> a<m>，没有找到，可以用 a 来代替
            String baseName = action.getIdentify();
            pts = pointToSet.get(baseName);
        } else if (action.isArray()) {
            // a<a> 没有找到，可以用a的指向生成一个 a<a>的指向
            String baseName = action.getIdentify();
            pts = pointToSet.get(baseName);
            if (pts != null) {
                addToPointToSet(action.toString(), new HashSet<>(pts));
            }
        } else if (isNeedFromStatusOrMerge) {
            // a 没有找到，可以用 a<s> a<m> 的指向代替
            Action copied = action.clone();
            copied.setStatus(true);
            pts = pointToSet.get(copied.toString());
            if (pts != null) {
                return pts;
            }
            copied.setStatus(false);
            copied.setMerge(true);
            pts = pointToSet.get(copied.toString());
            if (pts != null) {
                return pts;
            }
        }
        return pts;
    }

    /**
     * 包含 field
     *
     * @param action
     * @return
     */
    private Set<String> getPointToSetByComplexAction(Action action, boolean isNeedFromStatusOrMerge) {
        String baseName = null;
        String maxBaseName = null;
        int fieldLength = action.getFieldLength();
        Action[] actions = new Action[2];

        for (int i = 0; i <= fieldLength; i++) { // 找到最大存在指向集
            boolean flag = true;
            Action[] temps = action.split(i);
            baseName = temps[0].toString();
            if (pointToSet.containsKey(baseName)) {
                maxBaseName = baseName;
                flag = false;
                actions = temps;
            } else if (temps[0].isEndWithArray()) {
                String temp = baseName.substring(0, baseName.length() - 3); // basename

                if (pointToSet.containsKey(temp)) {
                    flag = false;
                    // 如果有 a 但是没有 a<a> 则用a的指向集创建a<a>
                    Set<String> tempPts = pointToSet.get(temp);
                    addToPointToSet(baseName, new HashSet<>(tempPts));
                    maxBaseName = baseName;
                    actions = temps;
                }
            } else if (temps[0].isEndWithStatus() || temps[0].isEndWithMerge()) {
                String temp = baseName.substring(0, baseName.length() - 3);
                if (pointToSet.containsKey(temp)) {
                    maxBaseName = temp;
                    flag = false;
                    actions = temps;
                }
            } else if (isNeedFromStatusOrMerge) {
                if (pointToSet.containsKey(baseName + STATUS_FEATURE)) {
                    maxBaseName = baseName + STATUS_FEATURE;
                    flag = false;
                    actions = temps;
                } else if (pointToSet.containsKey(baseName + MERGE_FEATURE)) {
                    maxBaseName = baseName + MERGE_FEATURE;
                    flag = false;
                    actions = temps;
                }
            }
            if (flag) break;
        }

        Set<String> pts = null;
        if (maxBaseName != null) {
            pts = pointToSet.get(maxBaseName);
            if (pts != null) {
                Set<String> ret = new HashSet<>();
                if (maxBaseName.endsWith(STATUS_FEATURE) || maxBaseName.endsWith(MERGE_FEATURE) || actions[1] == null) {
                    ret.addAll(pts);
                } else {
                    for (String pt : pts) {
                        if ("new_operation".equals(pt) || "source".equals(pt)) {
                            ret.add(pt);
                        } else {
                            Action ptAction = Action.parse(pt);
                            if (ptAction != null) {
                                ptAction.append(actions[1]);
                                if (ptAction.isOverMaxFieldSize()) continue; // 最大仅跟踪 10 层field，防止无止尽的增加
                                ret.add(ptAction.toString());
                            }
                        }
                    }
                }
                return ret;
            }
        }

        return null;
    }

    public void applyTypes(SimpleObject obj, Set<String> types) {
        if (obj != null) {
            for (String handle : obj.getHandle()) {
                SimpleValue val = getValue(handle);
                if (val != null) {
                    val.getType().addAll(types);
                }
            }
        }
    }

    /**
     * 根据类型 assign value
     * lObj = rObj
     *
     * @param lObj
     * @param isStore
     * @param rObj
     * @param isLoad
     * @param isAppend
     * @param container
     */
    public void assign(SimpleObject lObj, boolean isStore, SimpleObject rObj, boolean isLoad, boolean isAppend, ValueContainer container) {
        if (isStore && !isPolluted(rObj, new HashSet<>())) return; // 如果是 数组存储类型，往数组里存的内容一定是受污染的，不受污染的不加进去
        Set<String> handles = getSimpleHandle(rObj, isLoad, this);
        container.assign(lObj, handles, isStore, isAppend);
    }

    public void setPollutedStatus(SimpleObject object, Domain status, Set<Integer> positions) {
        if (object == null || isNull(object)) return;

        if (object instanceof MultiObject) {
            for (SimpleObject o : ((MultiObject) object).getObjects()) {
                setPollutedStatus(o, status, positions);
            }
        } else {
            for (String handle : object.getHandle()) {
                SimpleValue val = getValue(handle);
                if (val == null || val.isNull()) continue;

                val.setStatus(status.copy());
                val.setPosition(new HashSet<>(positions));
            }
        }
    }

    /**
     * 不抹除原来的污点信息，添加新的信息
     *
     * @param object
     * @param status
     * @param positions
     */
    public void addPollutedStatus(SimpleObject object, Domain status, Set<Integer> positions) {
        if (object == null || isNull(object)) return;

        if (object instanceof MultiObject) {
            for (SimpleObject o : ((MultiObject) object).getObjects()) {
                addPollutedStatus(o, status, positions);
            }
        } else {
            for (String handle : object.getHandle()) {
                SimpleValue val = getValue(handle);
                if (val == null || val.isNull()) continue;

                val.setStatus(Domain.merge(status, val.getStatus()));
                val.getPosition().addAll(positions);
            }
        }
    }

    public Set<String> getSimpleHandle(SimpleObject obj, boolean isLoad, ValueContainer container) {
        if (obj == null) return new HashSet<>();
        Set<String> handles = new HashSet<>();

        if (obj instanceof MultiObject) {
            for (SimpleObject o : ((MultiObject) obj).getObjects()) {
                handles.addAll(getSimpleHandle(o, isLoad, container));
            }
        } else {
            if (isLoad && !isNull(obj) && !obj.isSource()) {
                for (String handle : obj.getHandle()) {
                    SimpleValue val = container.getValue(handle);
                    if (val == null) {
                        val = getValue(handle);
                    }
                    if (val != null && val.isArray()) {
                        if (val.getArrays().isEmpty()) {
                            // 新增 array 内容
                            SimpleValue newArrayValue = SimpleValue.makeArrayValueWithType(obj.getName(), val);
                            addValueToContainer(newArrayValue, false);
                            handles.add(newArrayValue.getUuid());
                            val.getArrays().add(newArrayValue.getUuid());
                        } else {
                            handles.addAll(val.getArrays());
                        }
                    }
                }
            } else {
                handles.addAll(obj.getHandle());
            }
        }

        return handles;
    }

    public Map<String, String> generateObjectMap(SimpleObject[] objects, boolean reverse) {
        Map<String, String> map = new HashMap<>();
        if (objects == null) return map;
        if (objects[0] != null) {
            if (reverse) {
                map.put("this", objects[0].getName());
            } else {
                map.put(objects[0].getName(), "this");
            }
        }

        for (int i = 1; i < objects.length; i++) {
            int pos = i - 1;
            if (objects[i] != null) {
                if (reverse) {
                    map.put("param-" + pos, objects[i].getName());
                } else {
                    map.put(objects[i].getName(), "param-" + pos);
                }
            }
        }
        return map;
    }

    public void replace(ValueContainer source) {
        localObjects.putAll(source.getLocalObjects());
        localValues.putAll(source.getLocalValues());
        globalObjects.putAll(source.getGlobalObjects());
        globalValues.putAll(source.getGlobalValues());
        pointToSet.putAll(source.getPointToSet());
    }

    public void copy(SimpleObject[] objects, ValueContainer dest) {
        for (SimpleObject obj : objects) {
            if (obj != null) {
                if (dest.containsObject(obj.getName())) continue;

                dest.addObjectToContainer(obj.clone());
                obj.setLocked(true);
                for (String handle : obj.getHandle()) {
                    SimpleValue val = getValue(handle);
                    if (val == null || val.isLocked() || dest.containsValue(handle)) continue;
                    val.setLocked(true);
                    copy(val, obj.isStatic(), dest);
                    val.setLocked(false);
                }
                obj.setLocked(false);
            }
        }
        dest.getPointToSet().putAll(pointToSet);
    }

    public boolean containsValue(String identity) {
        return localValues.containsKey(identity) || globalValues.containsKey(identity);
    }

    public boolean containsObject(String identity) {
        return localObjects.containsKey(identity) || globalObjects.containsKey(identity);
    }

    public void copy(SimpleValue value, boolean isStatic, ValueContainer dest) {
        if (value != null) {
            // value本身
            dest.addValueToContainer(value.clone(), isStatic);
            // value array
            if (value.isArray()) {
                for (String arrayHandle : value.getArrays()) {
                    SimpleValue arrayVal = getValue(arrayHandle);
                    if (arrayVal == null || arrayVal.isLocked() || dest.containsValue(arrayHandle)) continue;
                    arrayVal.setLocked(true);
                    copy(arrayVal, isStatic, dest);
                    arrayVal.setLocked(false);
                }
            }
            // value field
            Set<SimpleObject> objects = new HashSet<>();
            for (Set<String> fields : value.getFields().values()) {
                for (String field : fields) {
                    SimpleObject fieldObj = getObject(field);
                    if (fieldObj != null && !fieldObj.isLocked()) {
                        objects.add(fieldObj);
                    }
                }
            }
            SimpleObject[] objects1 = new SimpleObject[objects.size()];
            objects.toArray(objects1);
            copy(objects1, dest);
        }
    }

    /**
     * 推算当前obj的type
     * 如果obj的values有多个，且type有多个，则返回obj的type
     * 反之优先返回values的type
     *
     * @param obj
     * @return
     */
    public String getType(SimpleObject obj) {
        if (obj == null) return null;

//        if(obj.isCastType()){
//            return obj.getType();
//        }
        Set<String> types = new HashSet<>();
        for (String handle : obj.getHandle()) {
            SimpleValue val = getValue(handle);
            if (val != null) {
                types.addAll(val.getType());
            }
        }

        if (types.size() == 1) {
            return (String) types.toArray()[0];
        } else {
            return obj.getType();
        }
    }

    public Set<String> getTypes(SimpleObject obj) {
        if (obj == null) return new HashSet<>();

        Set<String> types = new HashSet<>();
        types.add(obj.getType());

        for (String handle : obj.getHandle()) {
            SimpleValue val = getValue(handle);
            if (val != null) {
                types.addAll(val.getType());
            }
        }
        return types;
    }

    /**
     * 更具当前value的transfer字段，传递相应的内容给Obj的values
     *
     * @param obj
     * @param val
     */
    public void transfer(SimpleObject obj, SimpleValue val) {
        for (String handle : obj.getHandle()) {
            SimpleValue tmp = getValue(handle);
            if (tmp != null) {
                if ("type".equals(val.getTransfer())) {
                    tmp.setType(val.getType());
                } else if ("status".equals(val.getTransfer())) {
                    tmp.setStatus(val.getStatus());
                    tmp.setPosition(val.getPosition());
                }
            }
        }
    }

    public void addValueToContainer(SimpleValue value, boolean isStatic) {
        if (isStatic) {
            globalValues.put(value.getUuid(), value);
        } else {
            localValues.put(value.getUuid(), value);
        }
    }

    public void addObjectToContainer(SimpleObject obj) {
        if (obj.isStatic()) {
            globalObjects.put(obj.getName(), obj);
        } else {
            localObjects.put(obj.getName(), obj);
        }
    }

    /**
     * copy source to this value container
     *
     * @param source
     */
    public void copy(ValueContainer source) {
        localObjects.clear();
        for (Map.Entry<String, SimpleObject> entry : source.getLocalObjects().entrySet()) {
            localObjects.put(entry.getKey(), entry.getValue().clone());
        }

        localValues.clear();
        for (Map.Entry<String, SimpleValue> entry : source.getLocalValues().entrySet()) {
            localValues.put(entry.getKey(), entry.getValue().clone());
        }

        globalObjects.clear();
        for (Map.Entry<String, SimpleObject> entry : source.getGlobalObjects().entrySet()) {
            globalObjects.put(entry.getKey(), entry.getValue().clone());
        }

        globalValues.clear();
        for (Map.Entry<String, SimpleValue> entry : source.getGlobalValues().entrySet()) {
            globalValues.put(entry.getKey(), entry.getValue().clone());
        }

        valueParser = source.valueParser;
        dataContainer = source.getDataContainer();

//        dataDependencyGraph.clear();
//        for(Map.Entry<String, Set<String>> entry:source.getDataDependencyGraph().entrySet()){
//            dataDependencyGraph.put(entry.getKey(), new HashSet<>(entry.getValue()));
//        }

//        reverseDataDependencyGraph.clear();
//        for(Map.Entry<String, Set<String>> entry:source.getReverseDataDependencyGraph().entrySet()){
//            reverseDataDependencyGraph.put(entry.getKey(), new HashSet<>(entry.getValue()));
//        }

        pointToSet.clear();
        for (Map.Entry<String, Set<String>> entry : source.getPointToSet().entrySet()) {
            pointToSet.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }

//        actionContainer = source.actionContainer.copy();
    }

    public Set<String> getMaxFieldPts(Action action){
        Action copied = action.clone();
        while (copied.isHasField()){
            copied = copied.popRight();
            Set<String> pts = getPointToSetByAction(copied, true);
            if(pts != null && pts.size() > 0){
                return pts;
            }
        }
        return null;
    }

    /**
     * union other's localObjects and globalObjects
     *
     * @param other
     */
    public void union(ValueContainer other) {
        unionValues(other.localValues, false);
        unionValues(other.globalValues, true);
        unionObjects(other.localObjects, false);
        unionObjects(other.globalObjects, true);

        // union pts
        Map<String, Set<String>> finalPts = new HashMap<>();
        Map<String, Set<String>> diffAPts = new HashMap<>(pointToSet);
        Map<String, Set<String>> diffBPts = new HashMap<>(other.getPointToSet());
        for (Map.Entry<String, Set<String>> entry : other.getPointToSet().entrySet()) {
            String key = entry.getKey();
            if (pointToSet.containsKey(entry.getKey())) {
                Set<String> ptsValues = new HashSet<>(pointToSet.get(key));
                ptsValues.addAll(entry.getValue());
                finalPts.put(key, ptsValues);
                diffAPts.remove(key);
                diffBPts.remove(key);
            }
        }
        // deal diffAPts
        for (Map.Entry<String, Set<String>> entry : diffAPts.entrySet()) {
            String key = entry.getKey();
            Action action = Action.parse(entry.getKey());
            if(action.isHasField()){
                // A有B没有，找B里面最大的fieldPts
                Set<String> pts = other.getMaxFieldPts(action);
                if(pts == null) continue;
                Set<String> ptsValues = new HashSet<>(pointToSet.get(key));
                ptsValues.addAll(pts);
                ptsValues.remove("new_operation");
                finalPts.put(key, ptsValues);
            }else{
                finalPts.put(key, new HashSet<>(entry.getValue()));
            }
        }
        // deal diffBPts
        for (Map.Entry<String, Set<String>> entry : diffBPts.entrySet()) {
            String key = entry.getKey();
            Action action = Action.parse(entry.getKey());
            if(action.isHasField()){
                // A没有B有，找A里面最大的fieldPts
                Set<String> pts = getMaxFieldPts(action);
                if(pts == null) continue;
                Set<String> ptsValues = new HashSet<>(other.getPointToSet().get(key));
                ptsValues.addAll(pts);
                ptsValues.remove("new_operation");
                finalPts.put(key, ptsValues);
            }else{
                finalPts.put(key, new HashSet<>(entry.getValue()));
            }
        }
    }


    private void unionValues(Map<String, SimpleValue> source, boolean isGlobal) {
        Map<String, SimpleValue> dest = isGlobal ? globalValues : localValues;
        for (Map.Entry<String, SimpleValue> entry : source.entrySet()) {
            if (dest.containsKey(entry.getKey())) {
                SimpleValue v1 = dest.get(entry.getKey());
                SimpleValue v2 = entry.getValue();
                if (v1 != null) {
                    v1.union(v2);
                } else {
                    v1 = v2.clone();
                }
                dest.put(entry.getKey(), v1);
            } else {
                dest.put(entry.getKey(), entry.getValue().clone());
            }
        }
    }

    /**
     * 实际的union操作
     * union 所有 objects，包括localObjects 和 globalObjects
     *
     * @param source
     */
    private void unionObjects(Map<String, SimpleObject> source, boolean isGlobal) {
        Map<String, SimpleObject> dest = isGlobal ? globalObjects : localObjects;

        for (Map.Entry<String, SimpleObject> entry : source.entrySet()) {
            if (dest.containsKey(entry.getKey())) {
                SimpleObject obj1 = dest.get(entry.getKey());
                SimpleObject obj2 = entry.getValue();
                obj1.getHandle().addAll(obj2.getHandle());
            } else {
                dest.put(entry.getKey(), entry.getValue().clone());
            }
        }
    }

    public boolean isContainsPollutedObject(SimpleObject[] objects) {
        for (SimpleObject object : objects) {
            if (object == null) continue;
            if (isPolluted(object, new HashSet<>())) return true;
        }
        return false;
    }

    /**
     * 判断当前obj是否是污染的状态，并且会去判断field的可控性
     *
     * @param obj
     * @return
     */
    public boolean isPolluted(SimpleObject obj, Set<String> visited) {
        if (obj == null || visited.contains(obj.getName())) return false;

        if (obj instanceof MultiObject) {
            for (SimpleObject o : ((MultiObject) obj).getObjects()) {
                if (isPolluted(o, visited)) return true;
            }
        } else {
            visited.add(obj.getName());
            for (String handle : obj.getHandle()) {
                SimpleValue value = getValue(handle);
                if (value == null) continue;

                if (isPolluted(value, visited)) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean isPolluted(SimpleValue val, Set<String> visited) {
        if (val == null || visited.contains(val.getUuid())) return false;
        visited.add(val.getUuid());

        if (val.isPolluted()) return true;

        if (val.isArray()) {
            for (String array : val.getArrays()) {
                SimpleValue arrayVal = getValue(array);
                if (arrayVal != null && arrayVal.isPolluted()) {
                    return true;
                }
            }
        }

        for (Set<String> fields : val.getFields().values()) {
            for (String field : fields) {
                SimpleObject fieldObj = getObject(field);
                if (fieldObj != null && isPolluted(fieldObj, visited)) {
                    return true;
                }
            }
        }

        return false;
    }

    public void clear() {
        localObjects.clear();
        localValues.clear();
        globalObjects.clear();
        globalValues.clear();
        pointToSet.clear();
        objects = null;
//        dataDependencyGraph.clear();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValueContainer that = (ValueContainer) o;
        return Objects.equal(localObjects, that.localObjects) && Objects.equal(globalObjects, that.globalObjects) && Objects.equal(pointToSet, that.pointToSet);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(localObjects, globalObjects, pointToSet);
    }

    @Override
    public void close() throws Exception {
        clear();
    }
}
