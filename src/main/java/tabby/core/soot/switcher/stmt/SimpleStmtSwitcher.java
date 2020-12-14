package tabby.core.soot.switcher.stmt;

import lombok.Getter;
import lombok.Setter;
import soot.Local;
import soot.Value;
import soot.ValueBox;
import soot.jimple.*;
import tabby.core.data.TabbyVariable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        InvokeExpr invokeExpr = stmt.getInvokeExpr();
        if(invokeExpr instanceof StaticInvokeExpr)return; // 静态函数调用是影响不了当前变量状态的
        List<ValueBox> valueBoxes = stmt.getUseBoxes();
        Set<String> relatedType = new HashSet<>();
        List<TabbyVariable> notPollutedVars = new ArrayList<>();
        for(ValueBox box: valueBoxes){
            Value value = box.getValue();
            if(value instanceof Local){
                TabbyVariable var = context.getOrAdd(value);
                if(var.isPolluted()){
                    relatedType.addAll(var.getValue().getRelatedType());
                }else{
                    notPollutedVars.add(var);
                }
            }
        }
        if(relatedType.isEmpty() || notPollutedVars.isEmpty()) return;
        // 状态传递
        for(TabbyVariable var: notPollutedVars){
            var.setPolluted(true);
            var.getValue().getRelatedType().addAll(relatedType);
        }
    }

    @Override
    public void caseAssignStmt(AssignStmt stmt) {
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
}
