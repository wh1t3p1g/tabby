package tabby.core.switcher.value;

import lombok.extern.slf4j.Slf4j;
import soot.Local;
import soot.Value;
import soot.jimple.*;
import tabby.config.GlobalConfiguration;
import tabby.core.data.TabbyVariable;
import tabby.core.switcher.Switcher;

/**
 * @author wh1t3P1g
 * @since 2020/12/12
 */
@Slf4j
public class SimpleRightValueSwitcher extends ValueSwitcher {
    @Override
    public void caseInterfaceInvokeExpr(InterfaceInvokeExpr v) {
        caseInvokeExpr(v, "InterfaceInvoke");
    }

    @Override
    public void caseSpecialInvokeExpr(SpecialInvokeExpr v) {
        caseInvokeExpr(v, "SpecialInvoke");
    }

    @Override
    public void caseStaticInvokeExpr(StaticInvokeExpr v) {
        caseInvokeExpr(v, "StaticInvoke");
    }

    @Override
    public void caseVirtualInvokeExpr(VirtualInvokeExpr v) {
        caseInvokeExpr(v, "VirtualInvoke");
    }

    @Override
    public void caseDynamicInvokeExpr(DynamicInvokeExpr v) {
        defaultCase(v);
    }

    @Override
    public void caseCastExpr(CastExpr v) {
        Value value = v.getOp();
        value.apply(this);
    }

//    @Override
//    public void caseNewArrayExpr(NewArrayExpr v) {
//        TabbyVariable var = TabbyVariable.makeRandomInstance();
//        var.getValue().setArray(true);
//        setResult(var);
//    }
//
//    @Override
//    public void caseNewMultiArrayExpr(NewMultiArrayExpr v) {
//        defaultCase(v);
//    }
//
    @Override
    public void caseNewExpr(NewExpr v) {
        setResult(TabbyVariable.makeRandomInstance());
    }

    @Override
    public void caseArrayRef(ArrayRef v) {
        TabbyVariable var = null;
        Value baseValue = v.getBase();
        Value indexValue = v.getIndex();
        TabbyVariable baseVar = context.getOrAdd(baseValue);
        if (indexValue instanceof IntConstant) {
            int index = ((IntConstant) indexValue).value;
            var = baseVar.getElement(index);
        }else if(indexValue instanceof Local){
            // 存在lvar = a[i2] 这种情况，暂无法推算处i2的值是什么，存在缺陷这部分
        }
        if(var == null){ // 处理无法获取数组某一个值时，直接获取当前baseVar
            setResult(baseVar);
        }else{
            setResult(var);
        }
    }

    @Override
    public void caseLocal(Local v) {
        setResult(context.getOrAdd(v));
    }

    @Override
    public void caseStaticFieldRef(StaticFieldRef v) {
        TabbyVariable var = context.getGlobalMap().get(v);
        setResult(var);
    }

    @Override
    public void caseInstanceFieldRef(InstanceFieldRef v) {
        TabbyVariable var = context.getOrAdd(v);
        setResult(var);
    }

    public void caseInvokeExpr(InvokeExpr invokeExpr, String invokeType){
        if(GlobalConfiguration.DEBUG) {
            log.debug("Analysis: " + invokeExpr.getMethodRef().getSignature() + "; "+context.getTopMethodSignature());
        }

        setResult(Switcher.doInvokeExprAnalysis(unit, invokeExpr, dataContainer, context));

        if(GlobalConfiguration.DEBUG) {
            log.debug("Analysis: " + invokeExpr.getMethodRef().getName() + " done, return to" + context.getMethodSignature() + "; "+context.getTopMethodSignature());
        }
    }
}
