package tabby.core.soot.switcher;

import soot.SootMethod;
import soot.Value;
import soot.ValueBox;
import soot.jimple.InvokeExpr;
import soot.jimple.JimpleBody;
import soot.jimple.internal.JimpleLocalBox;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;
import tabby.core.data.Context;
import tabby.core.data.TabbyVariable;
import tabby.core.soot.toolkit.PollutedVarsPointsToAnalysis;
import tabby.neo4j.bean.ref.MethodReference;
import tabby.neo4j.cache.CacheHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * switcher的公共函数
 * @author wh1t3P1g
 * @since 2020/12/14
 */
public class Switcher {

    public static void doMethodAnalysis(Context context, CacheHelper cacheHelper, SootMethod method, MethodReference methodRef){
        try{
            JimpleBody body = (JimpleBody) method.retrieveActiveBody();
            UnitGraph graph = new BriefUnitGraph(body);
            PollutedVarsPointsToAnalysis pta =
                    PollutedVarsPointsToAnalysis.makeDefault(methodRef, graph, cacheHelper, context);
            methodRef.setInitialed(true);
            // TODO 做变量回溯，比较出action
            methodRef.getRelatedPosition().putAll(context.getReturnActions());
            methodRef.setPolluted(context.getReturnVar() != null && context.getReturnVar().isPolluted());
        }catch (RuntimeException e){

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

    public static TabbyVariable doInvokeExprAnalysis(InvokeExpr invokeExpr, CacheHelper cacheHelper, Context context){
        TabbyVariable retVar = null;
        // extract baseVar and args
        TabbyVariable baseVar = Switcher.extractBaseVarFromInvokeExpr(invokeExpr, context);
        Map<Integer, TabbyVariable> args = Switcher.extractArgsFromInvokeExpr(invokeExpr, context);

        // do method call back actions
        MethodReference methodRef = cacheHelper.loadMethodRef(invokeExpr.getMethod().getSignature());
        if(!methodRef.isInitialed()  // never analysis with pta
                && !context.isInRecursion(methodRef.getSignature())){ // not recursion
            // do call method analysis
            Context subContext = context.createSubContext(methodRef.getSignature());
            if(baseVar != null){
                TabbyVariable clonedVar = baseVar.deepClone(new ArrayList<>());
                clonedVar.setPreRelatedType();
                subContext.setBaseVar(clonedVar);
            }

            args.forEach((index, arg) -> {
                TabbyVariable clonedVar = arg.deepClone(new ArrayList<>());
                clonedVar.setPreRelatedType();
                subContext.getArgs().put(index, clonedVar);
            });

            Switcher.doMethodAnalysis(subContext, cacheHelper, invokeExpr.getMethod(), methodRef);
        }
        // TODO 参数修正，将从子函数的分析结果套用到当前的localMap
        // 左值为老的var，右值为新的指向
        // 那么要将老的var的value替换成新的var的value
        for(Map.Entry<String, String> entry:methodRef.getRelatedPosition().entrySet()){
            String position = entry.getKey();
            String newRelated = entry.getValue();
            TabbyVariable newRelatedVar = "this".equals(newRelated)? baseVar:null;
            if(newRelatedVar == null && newRelated.startsWith("param")){
                int paramIndex = Integer.valueOf(position.split("-")[1]);
                newRelatedVar = args.get(paramIndex);
            }

            if(position.startsWith("param")){ // 修正入参
                int paramIndex = Integer.valueOf(position.split("-")[1]);
                TabbyVariable paramVar = args.get(paramIndex);
                if(paramVar != null && newRelatedVar != null){
                    paramVar.assign(newRelatedVar);
                }
            }else if(position.startsWith("this")){ // 修正当前baseVar的类属性
                if(baseVar != null){
                    baseVar.modify(position, newRelated);
                }
            }else if(position.equals("return")){ // 修正返回值
                retVar = TabbyVariable.makeRandomInstance();
                retVar.modify(position, newRelated);
            }
        }
        return retVar;
    }
}
