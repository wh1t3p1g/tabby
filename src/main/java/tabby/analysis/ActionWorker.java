package tabby.analysis;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import soot.SootField;
import soot.Type;
import soot.VoidType;
import tabby.analysis.container.ValueContainer;
import tabby.analysis.data.*;
import tabby.common.utils.PositionUtils;
import tabby.common.utils.SemanticUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author wh1t3p1g
 * @since 2023/4/24
 */
@Slf4j
@Getter
public class ActionWorker {

    private ValueContainer currentContainer;
    private ValueContainer tempContainer;
    private Map<String, String> innerMap;
    private Map<String, String> outerMap;
    private Set<String> clearSet = new HashSet<>();
    private Map<String, Set<String>> returnMap = new HashMap<>();
    private Map<String, Set<String>> otherMap = new HashMap<>();

    private SimpleObject[] invokeStmtObjs;
    private SimpleObject[] currentFuncObjs;
    private Type returnType;
    private SimpleObject retObj = null;

    public final static String PARAM_PREFIX = "param-";
    public final static String THIS_PREFIX = "this";
    public final static String SOURCE_PREFIX = "source";

    public final static String STATUS_FEATURE = "<s>";
    public final static String MERGE_FEATURE = "<m>";
    public final static String ARRAY_FEATURE = "<a>";
    public final static String FIELD_FEATURE = "<f>";

    public ActionWorker(Map<String, Set<String>> actions, Type returnType,
                        ValueContainer current, SimpleObject[] innerObjs) {
        this.currentContainer = current;
        this.returnType = returnType;
        this.innerMap = current.generateObjectMap(innerObjs, true);
        this.outerMap = current.generateObjectMap(currentContainer.getObjects(), true);
        this.invokeStmtObjs = innerObjs;
        this.currentFuncObjs = currentContainer.getObjects();

        this.tempContainer = new ValueContainer(currentContainer.getDataContainer());
        currentContainer.copy(innerObjs, tempContainer);

        for (Map.Entry<String, Set<String>> entry : actions.entrySet()) {
            String key = entry.getKey();
            Set<String> values = entry.getValue();
            if (key.startsWith("return")) {
                returnMap.put(Action.parse(key).toString(), values);
                continue;
            } else if (values.size() == 1 && values.contains("null_type")) {
                clearSet.add(key);
                continue;
            }

            values.remove("null_type"); // 当同时有null_type和其他类型，不处理null_type
            otherMap.put(key, new HashSet<>(values));
        }
    }

    public void doWork() {
        caseClearAction();
        caseArgsAction();
        caseReturnAction();
        currentContainer.replace(tempContainer);
    }

    /**
     * key:[clear]
     * 将 key 的 pts 清空， 指向 value 改成 null 对象
     * 这里 不区分 <s> <m>
     * key 可能是 param 或 this<f>
     * 放在最后
     */
    public void caseClearAction() {
        for (String key : clearSet) {
            Action action = Action.parse(key);
            SimpleObject obj = parseAction(action, invokeStmtObjs, tempContainer);
            if (obj != null && !tempContainer.isValueNull(obj)) {
                applyClearAction(obj, action.isEndWithArray(), tempContainer);

                String name = parseAction(action.toString(), innerMap);
                if (name != null) {
                    tempContainer.removeFromPointToSet(name);
                    currentContainer.removeFromPointToSet(name);
                }
            }
        }
    }

    /**
     * 参数所发生的变化
     * 可能涉及到 this param source
     */
    public void caseArgsAction() {
        for (Map.Entry<String, Set<String>> entry : otherMap.entrySet()) {
            Action leftAction = Action.parse(entry.getKey());

            Set<String> rights = entry.getValue();

            String lName = parseAction(leftAction.toString(), innerMap); // 找到 innerMap 里面 action 对应的名字
            SimpleObject lObj = parseAction(leftAction, invokeStmtObjs, tempContainer);
            Action realLeftAction = Action.parse(lName);

            boolean isLObjConstant = SimpleObject.isConstantObject(lObj);
            if (lObj == null || lName == null
                    || tempContainer.isNull(lObj)
                    || (leftAction.isStartWithParam() && !leftAction.isHasField() && isLObjConstant)) {
                // null, 常量参数在函数中发生的变化不会影响参数的值
                continue;
            }


            boolean isLObjConstantArray = SimpleObject.isConstantArrayObject(lObj);
            boolean isNeedKeep = isLObjConstantArray || leftAction.isEndWithMerge() || leftAction.isEndWithArray();
            // 优先处理 left 所代表的变量的指向关系
            if (!isNeedKeep) { // 清除原有指向
                currentContainer.removeFromPointToSet(lName);
                tempContainer.removeFromPointToSet(lName);
            }
            // 添加新的指向
            boolean append = rights.size() > 1; // 如果有多个结果的继承，则append给lObj
            boolean first = true;
            for (String right : rights) {
                boolean isNeedAppend = !first && append;
                Action rightAction = Action.parse(right);
                // right 也有可能多层
                SimpleObject rObj = parseAction(rightAction, invokeStmtObjs, currentContainer);
                // 传入新的指向
                String rName = parseAction(rightAction.toString(), innerMap);
                Action realRightAction = Action.parse(rName);
                if (rName != null) {
                    // 更新 left 的 pts 指向集 开始
                    if (isLObjConstantArray) { // 对于 常量 或 常量数组 不需要更新他们的related指向集
                        // 常量数组比较特殊，需要把 right的指向指向给left指向内容
                        // action param-2 : this<f>buffer
                        // 假设 param-2 和 this<f>buffer 都是 常量 或常量数组
                        // param-2 对应 a this<f>buffer 对应 b
                        // a pts
                        //      a : this<buffer>
                        // b pts
                        //      b : param-0
                        // 此时 action 操作为 a = b; 但是因为是常量 他们之间是没办法交换的，所以要把a的指向更新为b的指向
                        // 也就是 this<f>buffer : param-0
                        Set<String> rightPts = currentContainer.getPointToSetByAction(realRightAction, true);
                        Set<String> leftPts = currentContainer.getPointToSetByAction(realLeftAction, true);
                        if (leftPts != null) {
                            leftPts.removeIf("new_operation"::equals);
                        }
                        if (leftPts == null || leftPts.isEmpty()) {
                            tempContainer.addToPointToSet(realLeftAction.toString(), rightPts);
                        } else {
                            for (String leftPt : leftPts) {
                                String realLeftPt = parseAction(leftPt, outerMap);
                                if (!isNeedAppend) {
                                    currentContainer.removeFromPointToSet(realLeftPt);
                                    tempContainer.removeFromPointToSet(realLeftPt);
                                }
                                tempContainer.addToPointToSet(realLeftPt, rightPts);
                            }
                        }
                    } else if (isLObjConstant) {
                        // 对于 常量，直接将right指给left即可
                        if (!isNeedAppend) {
                            currentContainer.removeFromPointToSet(realLeftAction.toString());
                            tempContainer.removeFromPointToSet(realLeftAction.toString());
                        }
                        Action copied = realLeftAction.clone(); // 直接把left 改成 status类型的，不继承原有的指向obj
                        copied.setStatus(true);
                        Set<String> rightPts = currentContainer.getPointToSetByAction(realRightAction, true);
                        tempContainer.addToPointToSet(copied.toString(), rightPts);
                    } else {
                        // 例子 action 对应 param-0 : param-1
                        // 实际对应变量 r0 : r1
                        // 存在pts
                        //      r1 : param-1
                        //      r1<f>field : param-2
                        // => 计算后应该得到 r0 相关的指向
                        //      r0 : param-1
                        //      r0<f>field : param-2
                        String temp = realRightAction.toString();
                        Set<String> related = currentContainer.getRelatedObjectsFromPTS(temp);
                        Map<String, Set<String>> pts = new HashMap<>();
                        // 从 pts 查找指向
                        for (String relatedRightName : related) {
                            Action action = Action.parse(relatedRightName);
                            Set<String> rightPts = currentContainer.getPointToSetByAction(action, true);
                            if (rightPts == null || rightPts.isEmpty()) continue;

                            String relatedActionString = action.toString();
                            String appendStr = relatedActionString.substring(temp.length());
                            Action appendAction = Action.parse(appendStr);
                            if (appendAction.isNull() || realLeftAction.isEndWithStatus() || realLeftAction.isEndWithMerge()) {
                                String key = realLeftAction.toString();
                                if (pts.containsKey(key)) {
                                    pts.get(key).addAll(rightPts);
                                } else {
                                    pts.put(key, new HashSet<>(rightPts));
                                }
                            } else {
                                // left 添加上 append，并指向当前的right pts
                                Action copied = realLeftAction.clone();
                                copied.append(appendAction);
                                if (copied.isOverMaxFieldSize()) continue;
                                String key = copied.toString();
                                if (pts.containsKey(key)) {
                                    pts.get(key).addAll(rightPts);
                                } else {
                                    pts.put(key, new HashSet<>(rightPts));
                                }
                            }
                        }
                        // 从变量本身查找污点
                        if (pts.isEmpty()) {
                            Set<Integer> positions = currentContainer.collectPollutedPositions(rObj, new HashSet<>(), true);
                            Set<String> pt = PositionUtils.getPositions(positions, PositionUtils.NOT_POLLUTED_POSITION);
                            if (!pt.isEmpty()) {
                                pts.put(realLeftAction.toString(), pt);
                            }
                        }
                        // 更新pts到container
                        for (Map.Entry<String, Set<String>> entry1 : pts.entrySet()) {
                            String key = entry1.getKey();
                            Set<String> values = entry1.getValue();
                            values.remove("new_operation"); // 直接清除new_operation的继承，没有意义
                            if (values.isEmpty()) continue;
                            if (isNeedAppend) {
                                tempContainer.addToPointToSet(key, values);
                            } else {
                                tempContainer.setToPointToSet(key, values);
                            }
                        }
                    }
                } else if (rObj != null && rObj.isSource()) {
                    Set<String> sourceValues = new HashSet<>();
                    sourceValues.add("source");
                    tempContainer.addToPointToSet(realLeftAction.toString(), sourceValues);
                }
                // assign
                if (rObj != null) {
                    if (leftAction.isEndWithMerge() || isLObjConstantArray) { // append
                        Set<Integer> positions = currentContainer.collectPollutedPositions(rObj, new HashSet<>(), true);
                        Domain status = currentContainer.getObjectStatus(rObj);
                        tempContainer.addPollutedStatus(lObj, status, positions);
                    } else if (leftAction.isEndWithStatus() || isLObjConstant) {// 清楚原有的信息，并添加当前的状态
                        Set<Integer> positions = currentContainer.collectPollutedPositions(rObj, new HashSet<>(), true);
                        Domain status = currentContainer.getObjectStatus(rObj); // 这里没有递归获取field，可能不准，最终还是看position
                        if (status.isNotPolluted() && positions.size() > 0) {
                            status = Domain.POLLUTED;
                        }
                        if (isNeedAppend) {
                            tempContainer.addPollutedStatus(lObj, status, positions);
                        } else {
                            tempContainer.setPollutedStatus(lObj, status, positions);
                        }
                    } else {
                        currentContainer.assign(lObj, leftAction.isEndWithArray(),
                                rObj, rightAction.isEndWithArray(), isNeedAppend, tempContainer);
                    }
                    first = false;
                }
            }
        }
    }

    public void caseReturnAction() {
        if (returnType != null && !(returnType instanceof VoidType)) {
            // return 类型共有3种情况
            // return 直接对应 一个base对象
            // return<s> <m> 对应仅返回一个状态
            // return<f> 对应返回一个field，此时应该填充到retVal里面
            // 优先处理 return
            retObj = processReturnMap(returnMap, returnType, innerMap, invokeStmtObjs, outerMap, tempContainer);
        }
    }

    /**
     * 处理return 返回一个集合Object
     * 保证 obj指向是对的
     * 保证 pts指向是对的
     *
     * @param returnMap
     * @param tempContainer
     * @return
     */
    private SimpleObject processReturnMap(Map<String, Set<String>> returnMap,
                                          Type returnType,
                                          Map<String, String> argsMap,
                                          SimpleObject[] argObjects,
                                          Map<String, String> outerMap,
                                          ValueContainer tempContainer) {
        SimpleObject retObj = SimpleObject.makeInvokeReturnObject("temp-invoke");
        SimpleValue retVal = null;
        // 对于 return<s> return<m> return 都存在的情况
        // 优先处理 return<s> return<m>
        // 如果都不存在 才处理 return
        if (returnMap.containsKey("return<s>") || returnMap.containsKey("return<m>")) {
            // 对于两种都有情况，取 return<m> 的方式，append 所有的东西
            retVal = SimpleValue.makeReturnValue(returnType);
            Set<String> values = returnMap.getOrDefault("return<s>", new HashSet<>());
            values.removeIf("null_type"::equals);
            returnMap.remove("return<s>");
            boolean append = returnMap.containsKey("return<m>") || values.size() > 1;
            if (returnMap.containsKey("return<m>")) {
                values.addAll(returnMap.getOrDefault("return<m>", new HashSet<>()));
                returnMap.remove("return<m>");
                values.removeIf("null_type"::equals);
            }

            if (values.size() > 0) {
                String type = append ? "append" : "reset";
                boolean isAppend = values.size() > 1;
                for (String right : values) {
                    applyReturnAction("return", right, type,
                            retObj, retVal, isAppend, argsMap, argObjects, outerMap, tempContainer);
                }

                retObj.getHandle().add(retVal.getUuid());
                tempContainer.addValueToContainer(retVal, false);
                retObj.setSimplifyInvoke(true);
            }
        } else if (returnMap.containsKey("return")) {
            Set<String> values = returnMap.remove("return");
            boolean isAppend = values.size() > 1;
            for (String right : values) {
                applyReturnAction(
                        "return", right, "object",
                        retObj, retVal, isAppend,
                        argsMap, argObjects, outerMap, tempContainer);
            }
        }
        // 处理 array
        if (returnMap.containsKey("return<a>")) {
            Set<String> values = returnMap.remove("return<a>");
            values.remove(PositionUtils.NULL_STRING);

            if (retObj.getHandle().isEmpty()) {
                retVal = SimpleValue.makeReturnValue(returnType);
                retObj.getHandle().add(retVal.getUuid());
                tempContainer.addValueToContainer(retVal, false);
            }

            for (String right : values) {
                for (String handle : retObj.getHandle()) {
                    retVal = tempContainer.getValue(handle);
                    if (retVal == null) {
                        retVal = currentContainer.getValue(handle);
                    }
                    if (retVal != null) {
                        applyReturnAction(
                                "return<a>", right, "array",
                                retObj, retVal, true,
                                argsMap, argObjects, outerMap, tempContainer);
                    }
                }
            }
        }
        // 接下来处理 field
        // 有两种情况
        // 一种是已经有具体的retVal了，则直接往retVal里添加field
        // 一种是没有具体的retVal，需要新建retVal，再往retVal里添加field

        for (Map.Entry<String, Set<String>> entry : returnMap.entrySet()) {
            String key = clean(entry.getKey());
            if (key.contains("<f>")) {
                if (retObj.getHandle().isEmpty()) {
                    retVal = SimpleValue.makeReturnValue(returnType);
                    retObj.getHandle().add(retVal.getUuid());
                    tempContainer.addValueToContainer(retVal, false);
                }
                // find retObj 's fieldObj
                String[] pos = key.split("<f>");
                int len = pos.length;
                MultiObject baseObj = new MultiObject();
                baseObj.addObject(retObj);
                for (int i = 1; i < len; i++) {
                    MultiObject tempObj = new MultiObject();
                    SimpleObject fieldObj = null;
                    for (String handle : baseObj.getHandle()) {
                        SimpleValue val = tempContainer.getValue(handle);
                        if (val == null) {
                            val = currentContainer.getValue((handle));
                        }
                        if (val != null) {
                            Set<String> fieldObjHandles = val.getFieldHandle(pos[i]);
                            if (fieldObjHandles.size() > 0) {
                                for (String fHandle : fieldObjHandles) {
                                    fieldObj = tempContainer.getObject(fHandle);
                                    if (fieldObj == null) {
                                        fieldObj = currentContainer.getObject(fHandle);
                                    }
                                    if (fieldObj != null) {
                                        tempObj.addObject(fieldObj);
                                    }
                                }
                            } else {
                                Set<String> types = val.getType();

                                if ((types.size() == 1 && types.contains("java.lang.Object")) || types.isEmpty()) {
                                    fieldObj = SimpleObject.makeObjectInstanceField(pos[i], val, tempContainer);
                                    val.addField(pos[i], fieldObj.getName());
                                    tempObj.addObject(fieldObj);
                                } else {
                                    for (String type : types) {
                                        SootField field = SemanticUtils.getField(type, pos[i]);
                                        if (field == null) continue;

                                        fieldObj = SimpleObject.makeFieldObject(baseObj.getFirstObject(), field, false, false, false, tempContainer);
                                        tempContainer.addObjectField(baseObj, pos[i], fieldObj.getName());
                                        tempObj.addObject(fieldObj);
                                        break;
                                    }
                                    if (fieldObj == null) {
                                        fieldObj = SimpleObject.makeObjectInstanceField(pos[i], val, tempContainer);
                                        val.addField(pos[i], fieldObj.getName());
                                        tempObj.addObject(fieldObj);
                                    }
                                }
                            }
                        }
                    }
                    baseObj = tempObj;
                }
                // apply rightObj
                Set<String> values = entry.getValue();
                MultiObject fieldObjs = baseObj;
                boolean first = true;
                for (String right : values) {
                    for (SimpleObject object : fieldObjs.getObjects()) {
                        if (object.getHandle().isEmpty()) continue;
                        String handle = (String) object.getHandle().toArray()[0];
                        if (first) {
                            object.getHandle().clear();
                        }
                        retVal = tempContainer.getValue(handle);
                        if (retVal == null) {
                            retVal = currentContainer.getValue(handle);
                        }
                        if (retVal != null) {

                            applyReturnAction(
                                    key, right, "field",
                                    retObj, retVal, false,
                                    argsMap, argObjects, outerMap, tempContainer);
                            object.getHandle().addAll(retVal.getTempFieldValues());
                        }
                    }
                    first = false;
                }
            }
        }

        return retObj;
    }

    private void applyReturnAction(String returnAction, String right, String type,
                                   SimpleObject retObj, SimpleValue retVal, boolean isAppend,
                                   Map<String, String> argsMap, SimpleObject[] argObjects,
                                   Map<String, String> outerMap,
                                   ValueContainer tempContainer) {
        Action rightAction = Action.parse(right);
        SimpleObject rObj = parseAction(rightAction, argObjects, currentContainer);
        Set<Integer> positions = null;
        Domain status = null;
        if (rObj != null && rObj.isNotEmpty()) {
            switch (type) {
                case "append": // return<m>
                    positions = currentContainer.collectPollutedPositions(rObj, new HashSet<>(), true);
                    status = currentContainer.getObjectStatus(rObj);
                    retVal.getPosition().addAll(positions);
                    retVal.setStatus(Domain.merge(status, retVal.getStatus()));
                    retObj.addInvokeTargets("return<s>", PositionUtils.getPositions(positions, PositionUtils.NOT_POLLUTED_POSITION));
                    break;
                case "reset": // return<s>
                    positions = currentContainer.collectPollutedPositions(rObj, new HashSet<>(), true);
                    status = currentContainer.getObjectStatus(rObj);
                    retVal.setPosition(positions);
                    retVal.setStatus(status);
                    retObj.addInvokeTargets("return<s>", PositionUtils.getPositions(positions, PositionUtils.NOT_POLLUTED_POSITION));
                    break;
                case "array": // return<a>
                    if (retVal.isArray() && !rObj.isNull()) {
                        Set<String> handles = currentContainer.getSimpleHandle(rObj, rightAction.isEndWithArray(), tempContainer);
                        retVal.addArray(handles);
                        // 更新 pts
                        String rName = parseAction(rightAction.toString(), argsMap);
//                        Set<String> pts = currentContainer.getPointToSetByComplexName(rName, outerMap);
                        Action rNameAction = Action.parse(rName);
                        Set<String> pts = currentContainer.getPointToSetByAction(rNameAction, true);

                        if (pts != null && !pts.isEmpty()) {
                            retObj.addInvokeTargets("return<a>", pts);
                        } else {
                            positions = currentContainer.collectPollutedPositions(rObj, new HashSet<>(), true);
                            retObj.addInvokeTargets("return<a>", PositionUtils.getPositions(positions, PositionUtils.NOT_POLLUTED_POSITION));
                        }
                    }
                    break;
                case "field": // return<f>aaa
                    String[] pos = returnAction.split("<f>");
                    String fieldName = pos[pos.length - 1];
                    SimpleObject fieldObj = null;
                    if (rObj instanceof MultiObject) {
                        fieldObj = ((MultiObject) rObj).getFirstObject();
                    } else {
                        fieldObj = rObj;
                    }
                    if (fieldObj != null) {
                        SimpleObject field = null;
                        if (SimpleObject.isConstantArrayObject(rObj) || SimpleObject.isConstantObject(rObj)) {
                            field = currentContainer.copyAndStore(fieldObj,
                                    "copied-field-object-" + rObj.getName());
                        } else {
                            if (rightAction.isEndWithArray()) {
                                field = SimpleObject.makeRandomArrayObject();
                                field.setType(fieldObj.getType());
                                for (String handle : fieldObj.getHandle()) {
                                    SimpleValue value = currentContainer.getValue(handle);
                                    if (value != null && value.isArray()) {
                                        field.getHandle().addAll(value.getArrays());
                                    }
                                }
                            } else {
                                field = fieldObj;
                            }
                        }

                        if (field != null) { // 检查当前 field 类型 是否符合 retVal field
                            // 这里的retVal 实际上是baseObj 我们需要往这个val里面塞field的内容，然后交给后续直接赋值
//                            Set<String> types = retVal.getType();
//                            added = SemanticHelper.checkField(field, pos, types, tempContainer);
//                            added = true;

                            retVal.getTempFieldValues().addAll(field.getHandle());
                            String rName = parseAction(rightAction.toString(), argsMap);
//                            Set<String> rightValues = currentContainer.getPointToSetByComplexName(rName, outerMap);
                            Action rNameAction = Action.parse(rName);
                            Set<String> pts = currentContainer.getPointToSetByAction(rNameAction, true);
                            if (pts != null && pts.size() > 0) {
                                retObj.addInvokeTargets(returnAction, pts);
                            } else {
                                positions = currentContainer.collectPollutedPositions(field, new HashSet<>(), true);
                                Set<String> realPositions = PositionUtils.getPositions(positions, PositionUtils.NOT_POLLUTED_POSITION);
                                if(!realPositions.isEmpty()){
                                    retObj.addInvokeTargets(returnAction+STATUS_FEATURE, realPositions);
                                }
                            }
                        }
                    }
                    break;
                default: // 处理 object
                    // obj 指向更新
                    Action leftAction = Action.parse("return");
                    Set<String> handles = currentContainer.getSimpleHandle(rObj, rightAction.isEndWithArray(), tempContainer);
                    tempContainer.assign(retObj, handles, false, isAppend);

                    // pts 指向更新
                    String rName = parseAction(rightAction.toString(), argsMap);
//                    Set<String> pts = currentContainer.getPointToSetByComplexName(rName, outerMap);

                    Action rNameAction = Action.parse(rName);
                    Set<String> pts = currentContainer.getPointToSetByAction(rNameAction, false);
                    if (pts == null) {
                        Set<Integer> position = currentContainer.collectPollutedPositions(rObj, new HashSet<>(), true);
                        pts = PositionUtils.getPositions(position, PositionUtils.NOT_POLLUTED_POSITION);
                        leftAction.setStatus(true);
                    }
                    if (!pts.isEmpty()) {
                        retObj.addInvokeTargets(leftAction.toString(), pts);
                    } else {
                        positions = currentContainer.collectPollutedPositions(rObj, new HashSet<>(), true);
                        positions.remove(PositionUtils.NOT_POLLUTED_POSITION);
                        if (positions.size() > 0) {
                            leftAction.setStatus(true);
                            retObj.addInvokeTargets(leftAction.toString(), PositionUtils.getPositions(positions, PositionUtils.NOT_POLLUTED_POSITION));
                        }
                    }

            }
        } else if (right.contains("param-") && right.contains("..")) {
            // 多参数类型，一般这种情况只继承position和polluted status 只会从规则里来
            int[] startAndEnd = PositionUtils.getMultiParamPositions(right);
            if (startAndEnd.length == 2) {
                if (startAndEnd[1] == -1) { // 1..n的情况 end为n时 当前为-1
                    startAndEnd[1] = invokeStmtObjs.length;
                }

                for (int i = startAndEnd[0]; i < startAndEnd[1]; i++) {
                    try {
                        rObj = invokeStmtObjs[i];
                        if (rObj != null) {
                            positions = currentContainer.collectPollutedPositions(rObj, new HashSet<>(), true);
                            positions.remove(PositionUtils.NOT_POLLUTED_POSITION);
                            if (positions.size() > 0) {
                                status = currentContainer.getObjectStatus(rObj);
                                retVal.getPosition().addAll(positions);
                                retVal.setStatus(Domain.merge(status, retVal.getStatus()));
                                Set<String> temps = new HashSet<>();
                                for (int pos : positions) {
                                    temps.add(PositionUtils.getPosition(pos));
                                }
                                retObj.addInvokeTargets("return"+STATUS_FEATURE, temps);
                            }
                        }
                    } catch (Exception ignore) {
                    }
                }
            }
        }
    }

    public void applyReturnAction(String action, SimpleObject retObj, SimpleValue retVal, SimpleObject rObj, boolean isLoad) {

        if (action.endsWith("<s>")) { // 临时
            Set<Integer> positions = currentContainer.collectPollutedPositions(rObj, new HashSet<>(), true);
            Domain status = currentContainer.getObjectStatus(rObj);
            retVal.setStatus(status);
            retVal.setPosition(positions);
            retObj.getHandle().add(retVal.getUuid());
            retObj.setSimplifyInvoke(true);
            return;
        } else if (action.endsWith("<m>")) { // merge
            Set<Integer> positions = currentContainer.collectPollutedPositions(rObj, new HashSet<>(), true);
            Domain status = currentContainer.getObjectStatus(rObj);
            retVal.setStatus(Domain.merge(status, retVal.getStatus()));
            retVal.getPosition().addAll(positions);
            retObj.getHandle().add(retVal.getUuid());
            retObj.setSimplifyInvoke(true);
            return;
        }

        Set<String> handles = currentContainer.getSimpleHandle(rObj, isLoad, currentContainer);
        if (handles == null || handles.isEmpty()) return;
        if (action.contains("<f>")) { // a.f = b return<f>f
            String[] pos = action.split("<f>");
            String fieldName = pos[pos.length - 1];
            // make a new field obj for retVal
            SimpleObject fieldObj = null;
            if (rObj instanceof MultiObject) {
                fieldObj = ((MultiObject) rObj).getFirstObject();
            } else {
                fieldObj = rObj;
            }
            if (fieldObj != null) {
                SimpleObject cloned = fieldObj.clone();
                cloned.setName("return-field-obj-" + action);
                cloned.getHandle().clear();
                cloned.getHandle().addAll(handles);

                currentContainer.addObjectToContainer(cloned);
                retVal.addField(fieldName, cloned.getName());
                retObj.getHandle().add(retVal.getUuid());
            }
        } else if (action.endsWith("<a>")) { // a[] = b
            retVal.addArray(handles);
            retObj.getHandle().add(retVal.getUuid());
        } else { // a = b
            retObj.getHandle().addAll(handles);
        }
    }

    /**
     * 根据map 获取当前action对应的变量名
     *
     * @param action
     * @param map
     * @return
     */
    private String parseAction(String action, Map<String, String> map) {
        // 特殊处理 source类型直接返回
        if (PositionUtils.isSpecialAction(action)) {
            return action;
        }

        String name = action; // this
        if (action.contains("<a>")) {// this[]
            name = action.replace("<a>", "");
        }

        if (action.contains("<f>")) { // this<f>a
            name = action.split("<f>")[0];
        }

        name = clean(name);
        String replace = map.get(name);
        if (replace != null) {
            return SemanticUtils.replaceFirst(action, name, replace);
        }
        return null;
    }

    /**
     * 清除原有状态，相当于初始化一个变量
     *
     * @param object
     * @param isArray
     * @param container
     */
    public void applyClearAction(SimpleObject object, boolean isArray, ValueContainer container) {
        if (object != null) {
            if (object instanceof MultiObject) {
                for (SimpleObject obj : ((MultiObject) object).getObjects()) {
                    applyClearAction(obj, isArray, container);
                }
            } else {
                for (String handle : object.getHandle()) {
                    SimpleValue val = container.getValue(handle);
                    if (val != null && !val.isNull()) {
                        if (isArray) {
                            val.getArrays().clear();
                        } else {
                            val.getPosition().clear();
                            val.setLocked(false);
                            val.setStatus(Domain.NOT_POLLUTED);
                            val.getArrays().clear();
                            val.getFields().clear();
                        }
                    }
                }
            }
        }
    }

    /**
     * parse action to obj
     *
     * @param action
     * @param objects
     * @param container
     * @return
     */
    public SimpleObject parseAction(Action action, SimpleObject[] objects, ValueContainer container) {
        int pos = PositionUtils.getPosition(action.getIdentify());
        if (pos >= -1 && pos + 1 < objects.length) {
            try {
                SimpleObject baseObj = objects[pos + 1];
                if (baseObj != null && !currentContainer.isNull(baseObj)) {
                    Action realAction = action.clone();
                    realAction.setIdentify(baseObj.getName());
                    if (realAction.isHasField()) {// a.f a.f.f a[].f
                        return container.getInstanceFieldObjects(realAction.toString(), true);
                    } else { // a[] a this<a><a>
                        return container.getObject(baseObj.getName());
                    }
                }
            } catch (ArrayIndexOutOfBoundsException ignore) {
                // 这种情况出现在 模糊处理时，将不可能的一些指向只指给了当前的action，直接忽略就行
                // 当前添加了判断 所以后续不会近当前的catch
                log.error("ArrayIndexOutOfBoundsException: " + action + ", objects length: " + objects.length);
            }
        }

        return SimpleObject.makeSpecialObject(pos, container);
    }

    private String clean(String str) {
        if (str == null) return null;
        return str.replace("<s>", "").replace("<m>", "");
    }

}
