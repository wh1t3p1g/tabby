package tabby.analysis.switcher.stmt;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import soot.Local;
import soot.PrimType;
import soot.Value;
import soot.jimple.*;
import tabby.analysis.switcher.Switcher;
import tabby.config.GlobalConfiguration;
import tabby.analysis.data.TabbyVariable;

import java.util.ArrayList;

/**
 * @author wh1t3P1g
 * @since 2020/12/12
 */
@Getter
@Setter
@Slf4j
public class SimpleStmtSwitcher extends StmtSwitcher {

    @Override
    public void caseInvokeStmt(InvokeStmt stmt) {
        // extract baseVar and args
        InvokeExpr ie = stmt.getInvokeExpr();
        if("<java.lang.Object: void <init>()>".equals(ie.getMethodRef().getSignature())) return;
        if(GlobalConfiguration.DEBUG){
            log.debug("Analysis: "+ie.getMethodRef().getSignature() + "; "+context.getTopMethodSignature());
        }
        Switcher.doInvokeExprAnalysis(stmt, ie, dataContainer, context, builder);
        if(GlobalConfiguration.DEBUG) {
            log.debug("Analysis: " + ie.getMethodRef().getName() + " done, return to" + context.getMethodSignature() + "; "+context.getTopMethodSignature());
        }
    }

    @Override
    public void caseAssignStmt(AssignStmt stmt) {
        Value lop = stmt.getLeftOp();
        Value rop = stmt.getRightOp();
        TabbyVariable rvar = null;
        boolean unbind = false;
        rightValueSwitcher.setUnit(stmt);
        rightValueSwitcher.setContext(context);
        rightValueSwitcher.setDataContainer(dataContainer);
        rightValueSwitcher.setResult(null);
        rop.apply(rightValueSwitcher);
        Object result = rightValueSwitcher.getResult();
        if(result instanceof TabbyVariable){
            rvar = (TabbyVariable) result;
        }
        if(rop instanceof Constant && !(rop instanceof StringConstant)){
            unbind = true;
        }
        if(rvar != null && rvar.getValue() != null && rvar.getValue().getType() instanceof PrimType){
            rvar = null; // 剔除基础元素的污点传递，对于我们来说，这部分是无用的分析
        }
        // 处理左值
        if(rvar != null || unbind){
            leftValueSwitcher.setContext(context);
            leftValueSwitcher.setMethodRef(methodRef);
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

    // 暂不处理if语句中的条件语句
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
        // 近似处理 只要有一种return的情况是可控的，就认为函数返回是可控的
        // 并结算当前的入参区别
        if(context.getReturnVar() != null && context.getReturnVar().containsPollutedVar(new ArrayList<>())) return;
        rightValueSwitcher.setUnit(stmt);
        rightValueSwitcher.setContext(context);
        rightValueSwitcher.setDataContainer(dataContainer);
        rightValueSwitcher.setResult(null);
        value.apply(rightValueSwitcher);
        var = (TabbyVariable) rightValueSwitcher.getResult();
        context.setReturnVar(var);
        if(var != null && var.isPolluted(-1) && reset){
            methodRef.addAction("return", var.getValue().getRelatedType());
        }
    }

}
