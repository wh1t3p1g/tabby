package tabby.core.soot.switcher;

import lombok.Getter;
import lombok.Setter;
import soot.*;
import soot.jimple.*;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;
import tabby.core.data.Context;
import tabby.core.data.TabbyVariable;
import tabby.core.soot.toolkit.VarsPointsToAnalysis;
import tabby.neo4j.bean.ref.MethodReference;
import tabby.neo4j.cache.CacheHelper;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author wh1t3P1g
 * @since 2020/11/25
 */
@Setter
@Getter
public class StmtSwitcher extends AbstractStmtSwitch {

    private Context context;
    private CacheHelper cacheHelper;

    @Override
    public void caseInvokeStmt(InvokeStmt stmt) {
        // a.func() a可控
        // a.func(b,c) b,c 可控
        super.caseInvokeStmt(stmt);
    }

    /**
     * assign语句存在一下几种情况：
     * a|a.f|a[i] = b|b.f|b.method()|new xxx|b[i]|(T) q
     * 当遇到assign语句时，都将重新设置lop所对应的内容
     * @param stmt
     */
    @Override
    public void caseAssignStmt(AssignStmt stmt) {
        Value lop = stmt.getLeftOp();
        Value rop = stmt.getRightOp();
        TabbyVariable rvar = null;
        boolean unbind = false;
        RValueSwitcher valueSwitcher = new RValueSwitcher(context, cacheHelper);
        rop.apply(valueSwitcher);
        Object result = valueSwitcher.getResult();
        if(result instanceof TabbyVariable){
            rvar = (TabbyVariable) result;
        }
        if(rop instanceof Constant){
            unbind = true;
        }
        // 处理左值
        if(rvar != null || unbind){
            LValueSwitcher lValueSwitcher = new LValueSwitcher(context, rvar, unbind);
            lop.apply(lValueSwitcher);
        }
    }

    @Override
    public void caseIdentityStmt(IdentityStmt stmt) { // Identity statement p := @this
        Value lop = stmt.getLeftOp();
        Value rop = stmt.getRightOp();
        if(rop instanceof ThisRef){
            context.bindThis(lop);
        }else if(rop instanceof ParameterRef){
            ParameterRef pr = (ParameterRef)rop;
            context.bindArg((Local)lop, pr.getIndex());
        }
    }

    @Override
    public void caseIfStmt(IfStmt stmt) {
        super.caseIfStmt(stmt);
    }

    @Override
    public void caseReturnStmt(ReturnStmt stmt) {
        Value value = stmt.getOp();
        TabbyVariable var = null;
        if(context.getReturnVar() != null && context.getReturnVar().isPolluted()) return; // 只要有一种return的情况是可控的，就认为函数返回是可控的
        RValueSwitcher valueSwitcher = new RValueSwitcher(context, cacheHelper);
        value.apply(valueSwitcher);
        var = (TabbyVariable) valueSwitcher.getResult();
        context.setReturnVar(var);
    }

    @Override
    public void caseThrowStmt(ThrowStmt stmt) {
        super.caseThrowStmt(stmt);
    }

    public MethodReference doInvokeAnalysis(InvokeExpr invokeExpr){
        SootMethod method = invokeExpr.getMethod();
        List<Value> args = invokeExpr.getArgs();
        MethodReference methodRef = cacheHelper.loadMethodRef(method.getSignature());

        if(methodRef == null || context.isInRecursion(method.getSignature())) return null; // 递归调用

        if(methodRef.isSink() || methodRef.isInitialed()){ // sink点为不动点，无需分析该函数内的调用情况
            methodRef.setInitialed(true);
            return methodRef;
        }

        if(methodRef.isIgnore() || method.isAbstract() || Modifier.isNative(method.getModifiers())){
            methodRef.setInitialed(true);
            return null; // native/抽象函数没有具体的body
        }

        invokeExpr.apply(new AbstractExprSwitch() {
            @Override
            public void caseInterfaceInvokeExpr(InterfaceInvokeExpr v) {
                TabbyVariable base = context.getOrAdd(v.getBase());
                Context subContext = context.createSubContext(method.getSignature(), args, base);
                doMethodAnalysis(methodRef, subContext);
                super.caseInterfaceInvokeExpr(v);
            }

            @Override
            public void caseSpecialInvokeExpr(SpecialInvokeExpr v) {
                TabbyVariable base = context.getOrAdd(v.getBase());
                Context subContext = context.createSubContext(method.getSignature(), args, base);
                doMethodAnalysis(methodRef, subContext);

                super.caseSpecialInvokeExpr(v);
            }

            @Override
            public void caseStaticInvokeExpr(StaticInvokeExpr v) {
                Context subContext = context.createSubContext(method.getSignature(), args, null);
                doMethodAnalysis(methodRef, subContext);
                super.caseStaticInvokeExpr(v);
            }

            @Override
            public void caseVirtualInvokeExpr(VirtualInvokeExpr v) {
                TabbyVariable base = context.getOrAdd(v.getBase());
                Context subContext = context.createSubContext(method.getSignature(), args, base);
                doMethodAnalysis(methodRef, subContext);
            }

            @Override
            public void caseDynamicInvokeExpr(DynamicInvokeExpr v) {
                super.caseDynamicInvokeExpr(v);
            }
        });
        methodRef.setInitialed(true);
        return methodRef;
    }
    // virtualinvoke r.xxx()
    // staticinvoke Class.xxx()
    // interfaceinvoke r.xxx()
    // specialinvoke r0.xxx() == this.xxx()

    public void doMethodAnalysis(MethodReference methodRef, Context context){
        try{
            SootMethod method = methodRef.getCachedMethod();
            JimpleBody body = (JimpleBody) method.retrieveActiveBody();
            UnitGraph graph = new BriefUnitGraph(body);
            VarsPointsToAnalysis analysis = new VarsPointsToAnalysis(graph);
            analysis.setCacheHelper(cacheHelper);
            analysis.setContext(context);
            analysis.doAnalysis();// 分析时，已经将当前函数内的函数调用初始化完成

            for(Unit unit: body.getUnits()){
                Stmt stmt = (Stmt) unit;
                if(stmt.containsInvokeExpr()){
                    Map<Local, TabbyVariable> localMap = analysis.getFlowBefore(unit);
                    InvokeExpr invokeExpr = stmt.getInvokeExpr();
                    MethodReference invokedMethodRef = cacheHelper.loadMethodRef(invokeExpr.getMethodRef().getSignature());
                    if(invokedMethodRef != null && invokedMethodRef.isInitialed() && invokedMethodRef.isPolluted()){
                        Set<Integer> pollutedPosition = invokedMethodRef.getPollutedPosition();
                        pollutedPosition.forEach(position -> {
                            Value value = invokeExpr.getArg(position);
                            TabbyVariable var = null;
                            if(value instanceof Local){
                                var = localMap.getOrDefault(value, null);
                            }else{
                                var = Context.globalMap.getOrDefault(value, null);
                            }
                            if(var != null && var.isPolluted()){
                                invokedMethodRef.setPolluted(true);

                            }
                        });
                    }
                }else if(unit instanceof ReturnStmt){

                }
            }
            // 后续处理return
        }catch (RuntimeException e){

        }
    }
}
