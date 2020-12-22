package tabby.core.soot.switcher;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import soot.Modifier;
import soot.SootMethod;
import soot.Value;
import soot.ValueBox;
import soot.jimple.InvokeExpr;
import soot.jimple.JimpleBody;
import soot.jimple.SpecialInvokeExpr;
import soot.jimple.internal.JimpleLocalBox;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;
import tabby.core.data.Context;
import tabby.core.data.TabbyVariable;
import tabby.core.soot.toolkit.PollutedVarsPointsToAnalysis;
import tabby.neo4j.bean.ref.MethodReference;
import tabby.neo4j.cache.CacheHelper;

import java.util.*;

/**
 * switcher的公共函数
 * @author wh1t3P1g
 * @since 2020/12/14
 */
@Slf4j
public class Switcher {

    /**
     * 当前函数可能有两种情况
     * 1. 直接分析一个函数，入参、baseVar 无上下文关联
     * 2. 函数调用，入参、baseVar 存在上下文关联
     * @param context
     * @param cacheHelper
     * @param method
     * @param methodRef
     */
    public static void doMethodAnalysis(Context context, CacheHelper cacheHelper, SootMethod method, MethodReference methodRef){
        try{
            if(method.isAbstract() || Modifier.isNative(method.getModifiers())){
                methodRef.setInitialed(true);
                methodRef.setActionInitialed(true);
                methodRef.getRelatedPosition().put("return", "clear");
                return;
            }
            JimpleBody body = (JimpleBody) method.retrieveActiveBody();
            UnitGraph graph = new BriefUnitGraph(body);
            PollutedVarsPointsToAnalysis pta =
                    PollutedVarsPointsToAnalysis.makeDefault(methodRef, graph, cacheHelper, context);

            // 做变量回溯，比较出action
            Map<String, String> actions = new HashMap<>();
            if(!methodRef.isActionInitialed()){
                context.getArgs().forEach((index, oldArg) -> { // 比较入参
                    TabbyVariable newArg = context.getCurrentArgs().get(index);
                    actions.putAll(getActions("param-"+index, oldArg, newArg, new HashSet<>()));
                });
                // 比较baseVar
                actions.putAll(getActions("this", context.getBaseVar(), context.getThisVar(), new HashSet<>()));
                methodRef.setActionInitialed(true);
            }

            // 处理 returnVar
            if(context.getReturnVar() != null){
                TabbyVariable retVar = context.getReturnVar();
                if(retVar.isPolluted()){

                    actions.put("return", retVar.getValue().getRelatedType());
                }else{
                    actions.put("return", "clear");
                }
            }

            methodRef.setInitialed(true);
            methodRef.getRelatedPosition().putAll(actions);
            methodRef.setPolluted(context.getReturnVar() != null && context.getReturnVar().isPolluted());
        }catch (RuntimeException e){
            // TODO 无法分析body
            e.printStackTrace();
            // 这种情况两种处理方式
            // 1. 根据rule 判断是否是sink函数
            // 2. 近似处理
            //      先判断当前args有没有可控的变量，如果有，则直接将left 和 其他参数都置为可控
        }

    }

    public static TabbyVariable doInvokeExprAnalysis(
            InvokeExpr invokeExpr,
            CacheHelper cacheHelper,
            Context context){
        // extract baseVar and args
        TabbyVariable baseVar = Switcher.extractBaseVarFromInvokeExpr(invokeExpr, context);
        Map<Integer, TabbyVariable> args = Switcher.extractArgsFromInvokeExpr(invokeExpr, context);
        // 检查当前的调用 是否需要分析 看入参 baseVar是否可控
        boolean flag = false;
        if(baseVar != null && baseVar.isPolluted()){
            flag = true;
        }

        for(TabbyVariable var: args.values()){
            if(var != null && var.isPolluted()){
                flag = true;
                break;
            }
        }

        if(!flag) return null; // baseVar，入参均不可控，返回值必不可控，无需做分析

        if(baseVar == null){
            baseVar = TabbyVariable.makeRandomInstance();
        }
        // do method call back actions
        MethodReference methodRef = cacheHelper.loadMethodRef(invokeExpr.getMethod().getSignature());
        if(methodRef == null) return null;
        if((!methodRef.isInitialed() || !methodRef.isActionInitialed()) // never analysis with pta
                && !context.isInRecursion(methodRef.getSignature())){ // not recursion
            // do call method analysis
            Context subContext = context.createSubContext(methodRef.getSignature());
            // copy this
            TabbyVariable clonedVar = baseVar.deepClone(new ArrayList<>());
            subContext.setBaseVar(clonedVar);
            // copy args
            args.forEach((index, arg) -> {
                if(arg != null){
                    TabbyVariable tempVar = arg.deepClone(new ArrayList<>());
                    subContext.getArgs().put(index, tempVar);
                }
            });

            Switcher.doMethodAnalysis(subContext, cacheHelper, invokeExpr.getMethod(), methodRef);
        }
        TabbyVariable retVar = null;
        // 参数修正，将从子函数的分析结果套用到当前的localMap
        for (Map.Entry<String, String> entry : methodRef.getRelatedPosition().entrySet()) {
            String position = entry.getKey();
            String newRelated = entry.getValue();
            TabbyVariable oldVar = parsePosition(position, baseVar, args, true);
            TabbyVariable newVar = null;

            if (oldVar != null) {
                if ("clear".equals(newRelated)) {
                    oldVar.clearVariableStatus();
                } else {
                    newVar = parsePosition(newRelated, baseVar, args, false);
                    oldVar.assign(newVar);
                }
            } else if (position.equals("return")) { // 处理return
                if (!"clear".equals(newRelated)) {
                    retVar = parsePosition(newRelated, baseVar, args, false);
                }
            }
        }

        return retVar;
    }

    public static TabbyVariable extractBaseVarFromInvokeExpr(InvokeExpr invokeExpr, Context context){
        TabbyVariable baseVar = null;
        List<ValueBox> valueBoxes = invokeExpr.getUseBoxes();
        for(ValueBox box:valueBoxes){
            Value value = box.getValue();
            if(box instanceof JimpleLocalBox){
                baseVar = context.getOrAdd(value);
                break;
            }
        }
        if(baseVar == null && invokeExpr instanceof SpecialInvokeExpr){
            baseVar = context.getThisVar();
        }
        return baseVar;
    }

    public static Map<Integer, TabbyVariable> extractArgsFromInvokeExpr(InvokeExpr invokeExpr, Context context){
        Map<Integer, TabbyVariable> args = new HashMap<>();
        for(int i=0; i<invokeExpr.getArgCount(); i++){
            TabbyVariable var = context.getOrAdd(invokeExpr.getArg(i));
            if(var != null){ // 不提取常量
                args.put(i, var);
            }
        }
        return args;
    }

    public static TabbyVariable parsePosition(String position,
                                              TabbyVariable baseVar,
                                              Map<Integer, TabbyVariable> args,
                                              boolean created){
        if(position == null) return null;
        TabbyVariable retVar = null;
        String[] positions = position.split("\\|");
        for(String pos:positions){
            if("this".equals(pos)){ // this
                retVar = baseVar;
            }else if(pos.startsWith("param-")){ // param-0
                int index = Integer.valueOf(pos.split("-")[1]);
                retVar = args.get(index);

            }else if(retVar != null && StringUtils.isNumeric(pos)){ // 后续找element 类似this|0
                int index = Integer.valueOf(pos);
                TabbyVariable tempVar = retVar.getElement(index);
                if(created && tempVar == null){
                    tempVar = TabbyVariable.makeRandomInstance();
                    retVar.addElement(index, tempVar);
                }
                retVar = tempVar;
            }else if(retVar != null){ // 类似 this|name
                TabbyVariable tempVar = retVar.getField(pos);
                if(created && tempVar == null){
                    tempVar = TabbyVariable.makeRandomInstance();
                    retVar.addField(pos, tempVar);
                }
                retVar = tempVar;
            }else{
                retVar = null; // 所有情况都不符合时，置为null
            }
        }
        return retVar;
    }

    /**
     * 检查入参的前后变化
     * @param oldVar
     * @param newVar
     * @return
     */
    public static Map<String, String> getActions(
            String position, TabbyVariable oldVar, TabbyVariable newVar, Set<TabbyVariable> checkedVars){
        Map<String, String> actions = new HashMap<>();
        if(oldVar == null || newVar == null || checkedVars.contains(oldVar)) return actions;
        checkedVars.add(oldVar);

        if(oldVar.getValue().getUuid().equals(newVar.getValue().getUuid())){
            // 当前变量未发生变化，但需要检查elements 和 field是否发生了变化
            if(!oldVar.getElements().equals(newVar.getElements())){// 检查elements里是否发生了变化
                for(Map.Entry<Integer, TabbyVariable> entry : newVar.getElements().entrySet()){ // newVar's elements >= old's elments
                    TabbyVariable oldElement = oldVar.getElement(entry.getKey());
                    EqualsBuilder eb = new EqualsBuilder();
                    eb.append(entry.getValue(), oldElement);
                    if(!eb.isEquals()){
                        if(oldElement == null && entry.getValue() != null && entry.getValue().isPolluted()){
                            actions.put(position+"|"+entry.getKey(), entry.getValue().getValue().getRelatedType());
                        }else if(oldElement != null && entry.getValue() != null){
                            actions.putAll(
                                    getActions(
                                            position+"|"+entry.getKey(),
                                            oldElement,
                                            entry.getValue(),
                                            new HashSet<>(checkedVars)));
                        }
                    }
                }
            }

            if(!oldVar.getFieldMap().equals(newVar.getFieldMap())){// 检查field是否发现变化
                for(Map.Entry<String, TabbyVariable> entry : newVar.getFieldMap().entrySet()){ // newVar's elements >= old's elments
                    TabbyVariable oldField = oldVar.getField(entry.getKey());
                    EqualsBuilder eb = new EqualsBuilder();
                    eb.append(entry.getValue(), oldField);
                    if(!eb.isEquals()){
                        if(oldField == null && entry.getValue() != null && entry.getValue().isPolluted()){
                            actions.put(position+"|"+entry.getKey(), entry.getValue().getValue().getRelatedType());
                        }else if(oldField != null && entry.getValue() != null){
                            actions.putAll(
                                    getActions(
                                            position+"|"+entry.getKey(),
                                            oldField,
                                            entry.getValue(),
                                            new HashSet<>(checkedVars)));
                        }
                    }
                }
            }
        }else {
            // 当前变量被重新赋值过，此时去判断是否是polluted
            if(newVar.isPolluted()){
                actions.put(position, newVar.getValue().getRelatedType());
            }else{
                actions.put(position, "clear");
            }
        }
        return actions;
    }
}
