package tabby.core.soot.switcher.value;

import soot.Local;
import soot.SootFieldRef;
import soot.Value;
import soot.jimple.*;
import tabby.core.data.TabbyVariable;
import tabby.core.soot.switcher.Switcher;

/**
 * @author wh1t3P1g
 * @since 2020/12/12
 */
public class SimpleRightValueSwitcher extends ValueSwitcher {

    public void caseInterfaceInvokeExpr(InterfaceInvokeExpr v) {
        caseInvokeExpr(v);
    }

    public void caseSpecialInvokeExpr(SpecialInvokeExpr v) {
        caseInvokeExpr(v);
    }

    public void caseStaticInvokeExpr(StaticInvokeExpr v) {
        caseInvokeExpr(v);
    }

    public void caseVirtualInvokeExpr(VirtualInvokeExpr v) {
        caseInvokeExpr(v);
    }

    public void caseDynamicInvokeExpr(DynamicInvokeExpr v) {
        defaultCase(v);
    }

    public void caseCastExpr(CastExpr v) {
        Value value = v.getOp();
        value.apply(this);
    }

    public void caseNewArrayExpr(NewArrayExpr v) {
        setResult(TabbyVariable.makeAnyNewRightInstance(v));
    }

    public void caseNewMultiArrayExpr(NewMultiArrayExpr v) {
        defaultCase(v);
    }

    public void caseNewExpr(NewExpr v) {
        setResult(TabbyVariable.makeAnyNewRightInstance(v));
    }

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

    public void caseLocal(Local v) {
        setResult(context.getOrAdd(v));
    }

    public void caseStaticFieldRef(StaticFieldRef v) {
        setResult(context.getOrAdd(v));
    }

    public void caseInstanceFieldRef(InstanceFieldRef v) {
        TabbyVariable var = null;
        SootFieldRef sootFieldRef = v.getFieldRef();
        Value base = v.getBase();
        if(base instanceof Local){
            TabbyVariable baseVar = context.getOrAdd(base);
            var = baseVar.getOrAddField(baseVar, sootFieldRef);
        }
        setResult(var);
    }

    public void caseInvokeExpr(InvokeExpr invokeExpr){
        setResult(Switcher.doInvokeExprAnalysis(invokeExpr, cacheHelper, context));
    }

}
