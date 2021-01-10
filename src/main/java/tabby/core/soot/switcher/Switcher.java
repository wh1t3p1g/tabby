package tabby.core.soot.switcher;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
import tabby.core.data.DataContainer;
import tabby.core.data.TabbyVariable;
import tabby.core.soot.toolkit.PollutedVarsPointsToAnalysis;
import tabby.db.bean.ref.MethodReference;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * @param dataContainer
     * @param method
     * @param methodRef
     */
    public static PollutedVarsPointsToAnalysis doMethodAnalysis(Context context, DataContainer dataContainer, SootMethod method, MethodReference methodRef, boolean force){
        try{
            if(method.isAbstract() || Modifier.isNative(method.getModifiers())
                    || method.isPhantom()){
                methodRef.setInitialed(true);
                methodRef.setActionInitialed(true);
//                methodRef.getActions().put("return", "clear");
                return null;
            }

            if(methodRef.isActionInitialed() && !force){
                return null;
            }

            JimpleBody body = (JimpleBody) method.retrieveActiveBody();
            UnitGraph graph = new BriefUnitGraph(body);
            PollutedVarsPointsToAnalysis pta =
                    PollutedVarsPointsToAnalysis.makeDefault(methodRef, body, graph, dataContainer, context, !methodRef.isActionInitialed());

            methodRef.setInitialed(true);
            methodRef.setActionInitialed(true);
            return pta;
        }catch (RuntimeException e){
            // TODO 无法分析body
            e.printStackTrace();
            // 这种情况两种处理方式
            // 1. 根据rule 判断是否是sink函数
            // 2. 近似处理
            //      先判断当前args有没有可控的变量，如果有，则直接将left 和 其他参数都置为可控
        }
        return null;
    }

    public static TabbyVariable doInvokeExprAnalysis(
            InvokeExpr invokeExpr,
            DataContainer dataContainer,
            Context context){
        // extract baseVar and args
        TabbyVariable baseVar = Switcher.extractBaseVarFromInvokeExpr(invokeExpr, context); // 调用对象
        Map<Integer, TabbyVariable> args = Switcher.extractArgsFromInvokeExpr(invokeExpr, context);
        // 检查当前的调用 是否需要分析 看入参 baseVar是否可控
        boolean flag = baseVar != null && baseVar.isPolluted();

        for(TabbyVariable var: args.values()){
            if(var != null && var.isPolluted()){
                flag = true;
                break;
            }
        }

        if(!flag) return null; // baseVar，入参均不可控，返回值必不可控，无需做分析

//        if(baseVar == null){
//            baseVar = TabbyVariable.makeRandomInstance();
//        }
        // do method call back actions
        MethodReference methodRef = dataContainer.getMethodRefBySignature(invokeExpr.getMethod().getSignature());
        if(methodRef == null) return null;
        if((!methodRef.isInitialed() || !methodRef.isActionInitialed()) // never analysis with pta
                && !context.isInRecursion(methodRef.getSignature())){ // not recursion
            // do call method analysis
            Context subContext = context.createSubContext(methodRef.getSignature());
            Switcher.doMethodAnalysis(subContext, dataContainer, invokeExpr.getMethod(), methodRef, false);
        }
        TabbyVariable retVar = null;
        // 参数修正，将从子函数的分析结果套用到当前的localMap
        // 修正 入参和baseVar
        for (Map.Entry<String, String> entry : methodRef.getActions().entrySet()) {
            String position = entry.getKey();
            String newRelated = entry.getValue();
            if("return".equals(position))continue; // return的修正 不进行处理，由assign的时候自己去处理
            TabbyVariable oldVar = parsePosition(position, baseVar, args, true);
            TabbyVariable newVar = null;

            if (oldVar != null) {
                if ("clear".equals(newRelated)) {
                    oldVar.clearVariableStatus();
                } else {
                    newVar = parsePosition(newRelated, baseVar, args, false);
                    oldVar.assign(newVar);
                }
            }
        }

        if(methodRef.getActions().containsKey("return")){
            retVar = parsePosition(methodRef.getActions().get("return"), baseVar, args, true);
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
                    tempVar.getValue().setPolluted(retVar.isPolluted());
                    if(retVar.isPolluted()){
                        tempVar.getValue().setRelatedType(retVar.getValue().getRelatedType()+"|"+index);
                    }
                    retVar.addElement(index, tempVar);
                }
                retVar = tempVar;
            }else if(retVar != null){ // 类似 this|name
                TabbyVariable tempVar = retVar.getField(pos);
                if(created && tempVar == null){
                    tempVar = TabbyVariable.makeRandomInstance();
                    if(retVar.isPolluted()){
                        tempVar.getValue().setPolluted(true);
                        tempVar.getValue().setRelatedType(retVar.getValue().getRelatedType()+"|"+pos);
                    }
                    retVar.addField(pos, tempVar);
                }
                retVar = tempVar;
            }else{
                retVar = null; // 所有情况都不符合时，置为null
            }
        }
        return retVar;
    }
}
