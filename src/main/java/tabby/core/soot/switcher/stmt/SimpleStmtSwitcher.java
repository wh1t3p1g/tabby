package tabby.core.soot.switcher.stmt;

import lombok.Getter;
import lombok.Setter;
import soot.Local;
import soot.SootMethod;
import soot.Value;
import soot.ValueBox;
import soot.jimple.*;
import soot.jimple.internal.JimpleLocalBox;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;
import tabby.core.data.Context;
import tabby.core.data.TabbyVariable;
import tabby.core.soot.toolkit.PollutedVarsPointsToAnalysis;
import tabby.neo4j.bean.ref.MethodReference;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 粗略的域敏感分析，遵循以下原则：
 * 单独的InvokeStmt
 *      a.func(b) 如果b为可控变量，a不可控，则置a为可控变量；如果a为可控变量，b不可控，则置b为可控变量
 * AssignStmt类型下的函数调用
 *      a = b.func(c) 如果b、c中存在可控变量，则置a为可控变量
 *      a = b         如果b为可控变量，则a为可控变量
 * 分析过程只保存，是否可控变量(polluted)，以及可控位(this,param-0,param-1,......)
 *
 * @author wh1t3P1g
 * @since 2020/12/12
 */
@Getter
@Setter
public class SimpleStmtSwitcher extends StmtSwitcher {

    @Override
    public void caseInvokeStmt(InvokeStmt stmt) {
        // extract baseVar and args
        InvokeExpr ie = stmt.getInvokeExpr();
        TabbyVariable baseVar = null;
        Map<Integer, TabbyVariable> args = new HashMap<>();
        for(int i=0; i<ie.getArgCount(); i++){
            TabbyVariable var = context.getOrAdd(ie.getArg(i));
            args.put(i, var);
        }
        List<ValueBox> valueBoxes = stmt.getUseBoxes();
        for(ValueBox box:valueBoxes){
            Value value = box.getValue();
            if(box instanceof JimpleLocalBox){
                baseVar = context.getOrAdd(value);
                break;
            }
        }
        // do method call back actions
        MethodReference methodRef = cacheHelper.loadMethodRef(ie.getMethod().getSignature());
        if(!methodRef.isInitialed()){
            // do call method analysis
            doMethodAnalysis(ie.getMethod(), methodRef);
            methodRef = cacheHelper.loadMethodRef(ie.getMethod().getSignature()); // refresh
        }

        for(Map.Entry<String, String> entry:methodRef.getRelatedPosition().entrySet()){
            String position = entry.getKey();
            String newRelated = entry.getValue();
            if(position.startsWith("param")){ // 修正入参
                int paramIndex = Integer.valueOf(position.split("-")[1]);
                TabbyVariable paramVar = args.get(paramIndex);
                if(paramVar != null){
                    paramVar.modify(position, newRelated);
                }
            }else if(position.startsWith("this")){ // 修正当前baseVar的类属性
                if(baseVar != null){
                    baseVar.modify(position, newRelated);
                }
            }else if(position.equals("return")){ // 修正返回值
                // 当前无左值
            }
        }
    }

    @Override
    public void caseAssignStmt(AssignStmt stmt) {
        // TODO 重写
        Value lop = stmt.getLeftOp();
        Value rop = stmt.getRightOp();
        TabbyVariable rvar = null;
        boolean unbind = false;
        rightValueSwitcher.setContext(context);
        rightValueSwitcher.setCacheHelper(cacheHelper);
        rop.apply(rightValueSwitcher);
        Object result = rightValueSwitcher.getResult();
        if(result instanceof TabbyVariable){
            rvar = (TabbyVariable) result;
        }
        if(rop instanceof Constant){
            unbind = true;
        }
        // 处理左值
        if(rvar != null || unbind){
            leftValueSwitcher.setContext(context);
            leftValueSwitcher.setRvar(rvar);
            leftValueSwitcher.setUnbind(unbind);
            lop.apply(leftValueSwitcher);
        }
    }

    @Override
    public void caseIdentityStmt(IdentityStmt stmt) {
        Value lop = stmt.getLeftOp();
        Value rop = stmt.getRightOp();
        if(rop instanceof ThisRef){
            context.bindThis(lop);
        }else if(rop instanceof ParameterRef){
            ParameterRef pr = (ParameterRef)rop;
            context.bindArg((Local)lop, pr.getIndex());
        }
    }

//    @Override
//    public void caseIfStmt(IfStmt stmt) {
//        super.caseIfStmt(stmt);
//    }
//
//    @Override
//    public void caseRetStmt(RetStmt stmt) {
//        super.caseRetStmt(stmt);
//    }

    @Override
    public void caseReturnStmt(ReturnStmt stmt) {
        Value value = stmt.getOp();
        TabbyVariable var = null;
        if(context.getReturnVar() != null && context.getReturnVar().isPolluted()) return; // 只要有一种return的情况是可控的，就认为函数返回是可控的
        rightValueSwitcher.setContext(context);
        rightValueSwitcher.setCacheHelper(cacheHelper);
        value.apply(rightValueSwitcher);
        var = (TabbyVariable) rightValueSwitcher.getResult();
        context.setReturnVar(var);
    }

    public void doMethodAnalysis(SootMethod method, MethodReference methodRef){
        if(context.isInRecursion(method.getSignature())) return; // 递归不分析

        JimpleBody body = (JimpleBody) method.retrieveActiveBody();
        UnitGraph graph = new BriefUnitGraph(body);
        Context subContext = context.createSubContext(method.getSignature());
        PollutedVarsPointsToAnalysis pta = PollutedVarsPointsToAnalysis.makeDefault(methodRef, graph, cacheHelper, subContext);
    }
}
