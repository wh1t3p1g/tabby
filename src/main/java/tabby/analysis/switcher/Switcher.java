package tabby.analysis.switcher;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JimpleLocalBox;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;
import tabby.analysis.PollutedVarsPointsToAnalysis;
import tabby.analysis.data.Context;
import tabby.analysis.data.TabbyVariable;
import tabby.common.bean.edge.Call;
import tabby.common.bean.ref.MethodReference;
import tabby.common.utils.PositionUtils;
import tabby.common.utils.SemanticUtils;
import tabby.core.container.DataContainer;

import java.util.ArrayList;
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
    public static PollutedVarsPointsToAnalysis doMethodAnalysis(Context context,
                                                                DataContainer dataContainer,
                                                                SootMethod method,
                                                                MethodReference methodRef){
        try{
            if(method.isAbstract() || Modifier.isNative(method.getModifiers())
                    || method.isPhantom()){
                methodRef.setInitialed(true);
                methodRef.setActionInitialed(true);
                return null;
            }

            if(methodRef.isActionInitialed() && methodRef.isInitialed()){
                // 已经初始化过了
                return null;
            }

            JimpleBody body = (JimpleBody) SemanticUtils.retrieveBody(method, methodRef.getSignature(), true);
            if(body == null) return null;

            UnitGraph graph = new BriefUnitGraph(body);
            PollutedVarsPointsToAnalysis pta =
                    PollutedVarsPointsToAnalysis
                            .makeDefault(methodRef, body, graph,
                            dataContainer, context, !methodRef.isActionInitialed());

            methodRef.setInitialed(true);
            methodRef.setActionInitialed(true);
            return pta;
        }catch (Exception e){
            throw new RuntimeException(e);
        }
    }



    public static TabbyVariable doInvokeExprAnalysis(
            Unit unit,
            InvokeExpr invokeExpr,
            DataContainer dataContainer,
            Context context){
        // extract baseVar and args
        TabbyVariable baseVar = Switcher.extractBaseVarFromInvokeExpr(invokeExpr, context); // 调用对象
        Map<Integer, TabbyVariable> args = Switcher.extractArgsFromInvokeExpr(invokeExpr, context);
        // 检查当前的调用 是否需要分析 看入参、baseVar是否可控
        List<Integer> pollutedPosition = pollutedPositionAnalysis(baseVar, args, context);
        TabbyVariable firstPollutedVar = null;
        boolean flag = false;
        int index = 0;
        for(Integer pos:pollutedPosition){
            if(pos != PositionUtils.NOT_POLLUTED_POSITION){
                if(index == 0){
                    firstPollutedVar = baseVar;
                }else{
                    firstPollutedVar = args.get(index-1);
                }
                flag=true;
                break;
            }
            index++;
        }

        if(!flag) return null;
        // baseVar，入参均不可控，返回值必不可控，无需做分析
        // find target MethodRef
        SootClass cls = invokeExpr.getMethod().getDeclaringClass();
        SootMethod invokedMethod = invokeExpr.getMethod();

        MethodReference methodRef = dataContainer
                .getOrAddMethodRef(invokeExpr.getMethodRef(), invokedMethod);

        // construct call edge
        String invokeType = "";
        if(invokeExpr instanceof StaticInvokeExpr){
            invokeType = "StaticInvoke";
        }else if(invokeExpr instanceof VirtualInvokeExpr){
            invokeType = "VirtualInvoke";
        }else if(invokeExpr instanceof SpecialInvokeExpr){
            invokeType = "SpecialInvoke";
        }else if(invokeExpr instanceof InterfaceInvokeExpr){
            invokeType = "InterfaceInvoke";
        }

        // try to analysis this method
        if((!methodRef.isInitialed() || !methodRef.isActionInitialed()) // never analysis with pta
                && !context.isInRecursion(methodRef.getSignature())){ // not recursion
            //  分析interfaceInvoke时，
            //   由于获取到的method是没有函数内容的，所以需要找到对应的具体实现来进行分析
            //   这里继续进行简化，对于无返回的函数调用，可以仍然保持原状，也就是舍弃了函数参数在函数体内可能发生的变化
            //   对于有返回的函数调用，则找到一个会影响返回值的具体实现
            Context subContext = context.createSubContext(methodRef.getSignature(), methodRef);
            Switcher.doMethodAnalysis(subContext, dataContainer, invokedMethod, methodRef);
        }
        // 回溯
        TabbyVariable retVar = null;
        if("<init>".equals(methodRef.getName())
                && baseVar != null && !baseVar.isPolluted(-1)){
            // 对于new语句 拆分成2个
            // obj = new type
            // obj.<init>(xxx)
            // 为了不丢失污点，这里近似处理
            // 将args的第一个污点状态传递给obj
            for(TabbyVariable arg: args.values()){
                if(arg != null && arg.isPolluted(-1)){
                    baseVar.getValue().setPolluted(true);
                    baseVar.getValue().setRelatedType(arg.getValue().getRelatedType());
                    break;
                }
            }
        }
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
                    boolean remain = false;
                    if(newRelated != null && newRelated.contains("&remain")){
                        remain = true;
                    }
                    newVar = parsePosition(newRelated, baseVar, args, false);
                    oldVar.assign(newVar, remain);
                }
            }
        }

        if(methodRef.getActions().containsKey("return")){
            retVar = parsePosition(methodRef.getActions().get("return"), baseVar, args, true);
        }
        boolean optimize = false;
        // TODO 接口类型 传递优化
//        if(retVar == null
//                && "InterfaceInvoke".equals(invokeType)
//                && invokedMethod.isAbstract()
//                && firstPollutedVar != null){
//            optimize = true;
//            String relatedType = firstPollutedVar.getValue().getRelatedType();
//            if(relatedType == null){
//                relatedType = firstPollutedVar.getFirstPollutedVarRelatedType();
//            }
//            TabbyValue retValue = new TabbyValue(invokedMethod.getReturnType(), relatedType);
//            retVar = TabbyVariable.makeRandomInstance();
//            retVar.setName("temp");
//            retVar.setValue(retValue);
//        }

        buildCallRelationship(cls.getName(), context, optimize,
                methodRef, dataContainer, unit, invokeType,
                pollutedPosition);

        return retVar;
    }

    public static List<Integer> pollutedPositionAnalysis(TabbyVariable baseVar,
                                                         Map<Integer, TabbyVariable> args,
                                                         Context context){
        List<Integer> positions = new ArrayList<>();
        // baseVar
        positions.add(getPollutedPosition(baseVar));

        // args
        for(TabbyVariable var: args.values()){
            positions.add(getPollutedPosition(var));
        }

        return positions;
    }

    public static int getPollutedPosition(TabbyVariable var){
        if(var != null){
            String related = null;
            if(var.isPolluted(-1)){ // var本身是pollted的情况
                related = var.getValue().getRelatedType();
            }else if(var.containsPollutedVar(new ArrayList<>())){ // 当前var的类属性，element元素是polluted的情况
                related = var.getFirstPollutedVarRelatedType();
            }
            if(related != null){
                return PositionUtils.getPosition(related);
            }
        }
        return PositionUtils.NOT_POLLUTED_POSITION;
    }

    public static void buildCallRelationship(String classname, Context context, boolean isOptimize,
                                      MethodReference targetMethodRef, DataContainer dataContainer,
                                      Unit unit, String invokeType, List<Integer> pollutedPosition){
        MethodReference sourceMethodRef = context.getMethodReference();
        if(sourceMethodRef == null || targetMethodRef == null){
            // 两个函数对象均不能为空
            return;
        }
        boolean isPolluted = true;
        if(targetMethodRef.isSink()){
            // 调用sink函数时，需要符合sink函数的可控点，如果均为可控点，则当前调用是可控的
            for(int i:targetMethodRef.getPollutedPosition()){
                if(pollutedPosition.size() > i+1 && pollutedPosition.get(i+1) == PositionUtils.NOT_POLLUTED_POSITION){
                    isPolluted = false;
                    break;
                }
            }
        }

        if(!targetMethodRef.isIgnore()
                && isPolluted){ // 剔除不可控边

            if("java.lang.String".equals(classname) // 这种情况一般均不可控，可控也没有意义
                    && ("equals".equals(targetMethodRef.getName())
                    || "hashCode".equals(targetMethodRef.getName())
                    || "length".equals(targetMethodRef.getName()))) return;

            if("java.lang.StringBuilder".equals(classname) // 这种情况一般均不可控，可控也没有意义
                    && ("toString".equals(targetMethodRef.getName())
                    || "hashCode".equals(targetMethodRef.getName()))) return;

            Call call = Call.newInstance(sourceMethodRef, targetMethodRef);
            call.setRealCallType(classname);
            call.setInvokerType(invokeType);
            call.setPollutedPosition(new ArrayList<>(pollutedPosition));
            call.setLineNum(unit.getJavaSourceStartLineNumber());
            call.generateId();

            if(!sourceMethodRef.getCallEdge().contains(call)){
                sourceMethodRef.getCallEdge().add(call);
                dataContainer.store(call);
            }

        }
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
            baseVar = context.getOrAdd(context.getThisVar());
        }
        return baseVar;
    }

    public static Map<Integer, TabbyVariable> extractArgsFromInvokeExpr(InvokeExpr invokeExpr, Context context){
        Map<Integer, TabbyVariable> args = new HashMap<>();
        for(int i=0; i<invokeExpr.getArgCount(); i++){
            TabbyVariable var = context.getOrAdd(invokeExpr.getArg(i));
            args.put(i, var);
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
            if(pos.contains("&remain")){ // 通常为 xxx&remain 表示 处理时需要保留原有的污点状态
                pos = pos.split("&")[0];
            }
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
                    boolean isPolluted = retVar.isPolluted(-1);
                    tempVar.getValue().setPolluted(isPolluted);
                    if(isPolluted){
                        tempVar.getValue().setRelatedType(retVar.getValue().getRelatedType()+"|"+index);
                    }
                    retVar.addElement(index, tempVar);
                }
                retVar = tempVar;
            }else if(retVar != null){ // 类似 this|name
                TabbyVariable tempVar = retVar.getField(pos);
                if(created && tempVar == null){
                    SootField field = retVar.getSootField(pos);
                    if(field != null){
                        tempVar = retVar.getOrAddField(retVar, field);
                    }
                }
                retVar = tempVar;
            }else{
                retVar = null; // 所有情况都不符合时，置为null
            }
        }
        return retVar;
    }
}
