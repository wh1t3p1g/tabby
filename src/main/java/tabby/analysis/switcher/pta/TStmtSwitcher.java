package tabby.analysis.switcher.pta;


import lombok.extern.slf4j.Slf4j;
import soot.Value;
import soot.ValueBox;
import soot.jimple.*;
import tabby.analysis.ActionWorker;
import tabby.analysis.container.ValueContainer;
import tabby.analysis.data.Action;
import tabby.analysis.data.Context;
import tabby.analysis.data.SimpleObject;
import tabby.analysis.data.SimpleValue;
import tabby.analysis.switcher.StmtSwitcher;
import tabby.common.utils.PositionUtils;
import tabby.common.utils.SemanticUtils;
import tabby.config.GlobalConfiguration;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * @author wh1t3p1g
 * @since 2021/11/26
 */
@Slf4j
public class TStmtSwitcher extends StmtSwitcher {

    private ValueContainer container;

    @Override
    public void accept(Context context) {
        this.context = context;
        this.container = context.getContainer();
        if (valueSwitcher == null) {
            valueSwitcher = new TValueSwitcher(context); // 过程内
            valueSwitcher.setPrimTypeNeedToCreate(GlobalConfiguration.IS_PRIM_TYPE_NEED_TO_CREATE);
        } else {
            valueSwitcher.accept(context);
        }
    }

    /**
     * a = @This
     * b = @Parameter
     * 正常这些语句都是在函数开始的地方，是最初的状态，所以不用进行field的处理
     * 绑定this和parameters
     *
     * @param stmt
     */
    @Override
    public void caseIdentityStmt(IdentityStmt stmt) {
        Value lop = stmt.getLeftOp();
        Value rop = stmt.getRightOp();
        Action leftAction = Action.getSimpleAction(lop);
        SimpleObject obj = container.getOrAdd(lop, false);
        if (rop instanceof ThisRef) {
            container.bindThisOrArg(obj, -1);
            container.addToPointToSet(leftAction.toString(), "this");
            context.getCurObjects()[0] = obj;
        } else if (rop instanceof ParameterRef) {
            int index = ((ParameterRef) rop).getIndex();

            if (context.getCurObjects().length >= index + 1) {
                context.getCurObjects()[index + 1] = obj;
            }

            container.addToPointToSet(leftAction.toString(), "param-" + index);
            container.bindThisOrArg(obj, index);
        }
    }

    /**
     * 处理如下几种情况
     * new => a = new newarray
     * assign => a = b; a.f = b; a = b.f; 由于目前算法将field类型当作Object了，所以也归类到assign上
     * store =>  a[1] = b;
     * load =>  a = b[1];
     * cast => a = (A) b;
     * invoke => a = b.func();
     * 这里不区分静态和非静态的区别，实际的操作是相同的
     * 对于store和load 主要用于数组类型
     * 每次assign都会触发一次变量传播，对于普通的对象，array类型的传播不太一样
     *
     * @param stmt
     */
    @Override
    public void caseAssignStmt(AssignStmt stmt) {
        Value lop = stmt.getLeftOp();
        String lName = Action.getSimpleName(lop);
        // 强制停止措施，不得已的情况会触发（防止因为当前函数的分析陷入死循环）
        if (SemanticUtils.increaseAndCheck(lName, triggerTimes)) {
            log.debug("Method {} triggered force stop mechanism!", context.getMethodSignature());
            context.setForceStop(true);
            return;
        }

        Value rop = stmt.getRightOp();
        valueSwitcher.setResult(null);
        rop.apply(valueSwitcher);
        Object rObj = valueSwitcher.getResult();
        if (rObj != null) {
            boolean isStore = lop instanceof ArrayRef;
            boolean isLoad = rop instanceof ArrayRef;
            SimpleObject lObj = container.getOrAdd(lop, true);
            if (lObj == null) return;

            if (rObj instanceof SimpleObject) {
                if (!stmt.containsInvokeExpr()) {
                    // 处理assign store load cast
                    String rName = SemanticUtils.getSimpleName0(rop);

                    if (rName != null) {
                        // 还需要进一步测试
                        container.setObjects(context.getCurObjects());
                        container.propagate(
                                lName, lObj, isStore,
                                rName, (SimpleObject) rObj, isLoad);
                        container.setObjects(null);
                    } else if (rop instanceof BinopExpr) {
                        if (!isStore) {// 数组类型允许多种来源
                            // 如果lName=a，则需要remove 所有跟a相关的指向，a,a.f,a[]
                            // 如果lName=a.f，则需要remove 所有跟a.f相关的指向，a.f
                            container.removeFromPointToSet(lName);
                        }

                        String left = lName;
                        if (isStore) {
                            if (lName != null && !lName.endsWith("<a>")) {
                                left = lName + "<a>";
                            }
                        }

                        Set<String> targets = new HashSet<>();
                        for (ValueBox box : rop.getUseBoxes()) {
                            String opName = SemanticUtils.getSimpleName0(box.getValue());
                            Set<String> set = container.getPointToSet().getOrDefault(opName, new HashSet<>());
                            targets.addAll(set);
                        }
                        if (!targets.isEmpty()) {
                            container.addToPointToSet(left, targets);
                        }

                        Set<String> handles = container.getHandles(lObj, (SimpleObject) rObj, isLoad);

                        container.assign(lObj, handles, isStore, false);
                    }
                } else {
                    // 处理invoke
                    // 这里有两种处理
                    // 1. 精确的处理：可以通过backtrack知道当前返回值的一些情况，然后根据这些情况进行传播处理
                    // 2. 模糊的处理：无法获取到body分析不了，只能进行模糊处理，也需要根据情况进行传播处理
                    container.propagate(lName, lObj, lop, isStore, (SimpleObject) rObj);
                }

            } else if (rObj instanceof SimpleValue) { // new操作的返回值
                if (!isStore) { // 因为很难推算array哪个值发生了kill操作，所以这里将忽略array store类型的new传递
                    SimpleValue rVal = (SimpleValue) rObj;
                    container.transfer(lObj, rVal);
                    // new 操作 删除之前的所有关系依赖
                    container.removeFromPointToSet(lObj.getName());
                    // 添加new标识
                    container.addToPointToSet(lObj.getName(), "new_operation");
                }

            }
        }
    }

    /**
     * a.func
     *
     * @param stmt
     */
    @Override
    public void caseInvokeStmt(InvokeStmt stmt) {
        InvokeExpr ie = stmt.getInvokeExpr();
        valueSwitcher.setResult(null);
        ie.apply(valueSwitcher);
    }

    /**
     * return obj
     * 返回值保存在context
     * 并且生成action
     *
     * @param stmt
     */
    @Override
    public void caseReturnStmt(ReturnStmt stmt) {
        valueSwitcher.setResult(null);
        Value value = stmt.getOp();
        value.apply(valueSwitcher);
        Object retObj = valueSwitcher.getResult();
        if (retObj instanceof SimpleObject) {
            caseReturn((SimpleObject) retObj);
        } else if (retObj instanceof SimpleValue) { // new 类型 直接添加action return:clear
            SimpleValue retVal = (SimpleValue) retObj;
            context.getMethodReference().getReturnType().addAll(retVal.getType());
            caseReturn(null);
        }
    }

    @Override
    public void caseReturnVoidStmt(ReturnVoidStmt stmt) {
        caseReturn(null);
        // 注意在应用actions时，所有都应在当前的函数调用者，函数参数变量的基础上进行。actions之间的操作不能影响原有的变量
        // 譬如最简单的swap函数，a.name 和 b.name进行互换
        // a.name = "a"; b.name = "b";
        // actions => {param-0<f>name:param-1<f>name, param-1<f>name:param-0<f>name}
        // 如果actions的应用在修改后的基础上进行的话，最终会得到 a.name = "b"; b.name = "b"; 显然是不对的
        // 那么此时，应该clone一份a和b，然后基于原a和b，对clone的a和b进行修改，最终才能得到正确的答案
    }

    public void caseReturn(SimpleObject returnObj) {
        if (context.getMethodReference().isActionInitialed()) return;
        // backtrack
        SimpleObject[] objects = context.getCurObjects();
        Map<String, Set<String>> actions = new HashMap<>();
        Map<String, String> map = container.generateObjectMap(objects, false);

        SemanticUtils.merge(process(map), actions);// 检查当前调用的 this、param-n 的变化 并生成action

        if (returnObj != null) {
            if (container.isValueNull(returnObj)) {
                Set<String> set = new CopyOnWriteArraySet<>();
                set.add("null_type");
                Map<String, Set<String>> clearMap = new HashMap<>();
                clearMap.put("return", set);
                SemanticUtils.merge(clearMap, actions);
            } else {
                Set<String> returnTypes = context.getMethodReference().getReturnType();
                if (!returnTypes.contains("void")) {
                    Set<String> types = container.getObjectTypes(returnObj);
                    types.remove("null_type");
                    context.getMethodReference().getReturnType().addAll(types);
                }

                SemanticUtils.merge(process(returnObj.getName(), "return", map), actions); // 处理返回值 并生成action
            }
        }

        SemanticUtils.merge(actions, this.actions);
    }

    public Map<String, Set<String>> process(Map<String, String> map) {
        Map<String, Set<String>> actions = new HashMap<>();

        for (Map.Entry<String, String> entry : map.entrySet()) {
            String name = entry.getKey();
            String replace = entry.getValue();
            actions.putAll(process(name, replace, map));
        }
        return actions;
    }

    public Map<String, Set<String>> process(String name, String replace, Map<String, String> map) {
        Map<String, Set<String>> actions = new HashMap<>();
        // check name 指向的object的指向关系
        Set<String> related = container.getRelatedObjectsFromPTS(name);
        if (related != null) {
            for (String r : related) {
                Set<String> targets = container.getPointToSet().get(r);
                String key = SemanticUtils.replaceFirst(r, name, replace);
                if (key == null) continue;

                if (targets != null) {
                    Set<String> copied = new CopyOnWriteArraySet<>(targets);
                    copied.remove(replace); // 剔除自身指向
                    copied.remove(replace + ActionWorker.STATUS_FEATURE); // 剔除自身指向
                    copied.remove(replace + ActionWorker.MERGE_FEATURE); // 剔除自身指向

                    if(copied.size() == 1 && copied.contains(PositionUtils.NULL_STRING)){
                        actions.put(key, copied);
                    }else if(copied.size() > 0){
                        copied.removeIf(t -> t.startsWith("new_operation")
                                || t.startsWith("constant(")
                                || t.startsWith("invoke_operation")
                                || t.equals(PositionUtils.NULL_STRING)
                        );
                        if (copied.size() > 0) {
                            actions.put(key, copied);
                        }
                    }
                }
            }
        }

        if(actions.isEmpty()){
            SimpleObject object = container.getObject(name);
            Set<Integer> positions = container.collectPollutedPositions(object, new HashSet<>(), false);
            Collection<String> currentTables = map.values();
            // self
            Set<String> action = PositionUtils.getPositions(positions, currentTables);
            action.remove(replace);
            if (action.size() > 0) {
                actions.put(replace + "<s>", action);
            }
        }

            // field
//            Map<String, Set<Integer>> fieldsPositions = container.collectFieldPollutedPositions(object);
//            for(Map.Entry<String, Set<Integer>> entry: fieldsPositions.entrySet()){
//                String field = entry.getKey();
//                action = PositionHelper.getPositions(entry.getValue(), currentTables);
//
//                if(action.size() > 0){
//                    actions.put(String.format("%s<f>%s", replace, field), action);
//                }
//            }

        if ("return".equals(replace)
                && context.getMethodReference().isReturnConstantType()
                && actions.containsKey("return")
        ) {
            // 如果返回值为基础类型，则转化 return 为 return<s>
            Set<String> tmp = actions.get("return");
            if (actions.containsKey("return<s>")) {
                actions.get("return<s>").addAll(tmp);
            } else {
                actions.put("return<s>", tmp);
            }
            actions.remove("return");
        }

        return actions;
    }
}
