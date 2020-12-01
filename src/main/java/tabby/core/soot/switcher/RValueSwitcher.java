package tabby.core.soot.switcher;

import lombok.Getter;
import lombok.Setter;
import soot.Local;
import soot.SootFieldRef;
import soot.SootMethod;
import soot.Value;
import soot.jimple.*;
import tabby.core.data.Context;
import tabby.core.data.TabbyVariable;
import tabby.dal.cache.CacheHelper;

import java.util.HashSet;
import java.util.Set;

/**
 * @author wh1t3P1g
 * @since 2020/11/26
 */
@Getter
@Setter
public class RValueSwitcher extends AbstractJimpleValueSwitch {

    private Context context;
    private CacheHelper cacheHelper;

    public RValueSwitcher(Context context, CacheHelper cacheHelper){
        this.context = context;
        this.cacheHelper = cacheHelper;
    }

    public void caseInterfaceInvokeExpr(InterfaceInvokeExpr v) {
        SootMethod method = v.getMethod();
        TabbyVariable var = TabbyVariable.newInstance(method);
        var.getValue().setControllableArgs(getControllableArgs(v));
        TabbyVariable base = context.getOrAdd(v.getBase());
        var.getValue().setBase(base);
        setResult(var);
    }

    public void caseSpecialInvokeExpr(SpecialInvokeExpr v) {
        SootMethod method = v.getMethod();
        TabbyVariable var = TabbyVariable.newInstance(method);
        var.getValue().setControllableArgs(getControllableArgs(v));
        TabbyVariable base = context.getOrAdd(v.getBase());
        var.getValue().setBase(base);
        setResult(var);
    }

    public void caseStaticInvokeExpr(StaticInvokeExpr v) {
        SootMethod method = v.getMethod();
        TabbyVariable var = TabbyVariable.newInstance(method);
        var.getValue().setControllableArgs(getControllableArgs(v));
        setResult(var);
    }

    public void caseVirtualInvokeExpr(VirtualInvokeExpr v) {
        SootMethod method = v.getMethod();
        TabbyVariable var = TabbyVariable.newInstance(method);
        var.getValue().setControllableArgs(getControllableArgs(v));
        TabbyVariable base = context.getOrAdd(v.getBase());
        var.getValue().setBase(base);
        setResult(var);
    }

    public void caseDynamicInvokeExpr(DynamicInvokeExpr v) {
        defaultCase(v);
    }

    public Set<Integer> getControllableArgs(InvokeExpr ie){
        Set<Integer> controllableArgs = new HashSet<>();
        int size = ie.getArgCount();
        for(int i = 0; i<size; i++){
            Value value = ie.getArg(i);
            if(value instanceof Local){
                TabbyVariable mayVar = context.getVariable(value);
                if(mayVar.isPolluted() || mayVar.isDependOnMethod()){
                    controllableArgs.add(i);
                }
            }
        }
        return controllableArgs;
    }

    public void caseCastExpr(CastExpr v) {
        TabbyVariable var = null;
        Value value = v.getOp();
        if(value instanceof Local){
            var = context.getOrAdd(value);
        }
        setResult(var);
    }

    public void caseNewArrayExpr(NewArrayExpr v) {
        TabbyVariable var = TabbyVariable.newInstance(v);
        Value arrSizeValue = v.getSize();
        if (arrSizeValue instanceof IntConstant) {
            int arrSize = ((IntConstant) arrSizeValue).value;
            var.getValue().asFixedArray(arrSize);
        }
        setResult(var);
    }

    public void caseNewMultiArrayExpr(NewMultiArrayExpr v) {
        defaultCase(v);
    }

    public void caseNewExpr(NewExpr v) {
        setResult(TabbyVariable.newInstance(v));
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
        setResult(var);
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
            var = baseVar.getField(sootFieldRef);
            if(var == null){
                TabbyVariable newFieldVar = TabbyVariable.newInstance(sootFieldRef);
                newFieldVar.setPolluted(baseVar.isPolluted());
                baseVar.addField(sootFieldRef, newFieldVar);
                var = newFieldVar;
            }
        }
        setResult(var);
    }



}
