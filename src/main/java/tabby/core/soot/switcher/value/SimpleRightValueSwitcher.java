package tabby.core.soot.switcher.value;

import soot.*;
import soot.jimple.*;
import soot.jimple.internal.JimpleLocalBox;
import tabby.core.data.TabbyVariable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        TabbyVariable var = null;
        Value value = v.getOp();
        if(value instanceof Local){
            var = context.getOrAdd(value);
        }
        TabbyVariable retVar = var.clone(true, new ArrayList<>());
        retVar.getValue().setType(v.getCastType());
        retVar.getValue().setTypeName(v.getCastType().toString());
        setResult(retVar);
    }

    public void caseNewArrayExpr(NewArrayExpr v) {
        TabbyVariable var = TabbyVariable.newInstance(v);
        Value arrSizeValue = v.getSize();
        if (arrSizeValue instanceof IntConstant) {
            int arrSize = ((IntConstant) arrSizeValue).value;
            var.getValue().asFixedArray(arrSize);
        }else if(arrSizeValue instanceof Local){
            var.getValue().asDynamicArray();
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
            var = baseVar.getField(sootFieldRef);
            if(var == null){
                var = TabbyVariable.newInstance(sootFieldRef);
                var.setPolluted(baseVar.isPolluted());
                var.getValue().getRelatedType().addAll(baseVar.getValue().getRelatedType());
                baseVar.addField(sootFieldRef, var);
            }
        }
        setResult(var);
    }

    public void caseInvokeExpr(InvokeExpr invokeExpr){
        List<ValueBox> valueBoxes = invokeExpr.getUseBoxes();
        SootMethod method = invokeExpr.getMethod();

        Set<String> relatedType = new HashSet<>();
        List<TabbyVariable> notPollutedVars = new ArrayList<>();
        Value baseValue = null;
        for(ValueBox box: valueBoxes){
            if(box instanceof JimpleLocalBox){
                baseValue = box.getValue();
            }
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
        TabbyVariable retVar = null;
        if("<java.lang.Object: java.lang.Object clone()>".equals(method.getSignature())
                || "<java.util.List: java.lang.Object[] toArray(java.lang.Object[])>".equals(method.getSignature()) // StringBuilder append
        ){
            if(baseValue != null){
                retVar = context.getVariable(baseValue);
            }
        }

        if(retVar == null){
            retVar = TabbyVariable.newInstance(method);
        }

        if(relatedType.isEmpty()) { // 表明不存在可控变量
            retVar.setPolluted(false);
            setResult(retVar);
            return;
        }
        // 状态传递 到下面的话 说明一定存在可控变量
        for(TabbyVariable var: notPollutedVars){
            var.setPolluted(true);
            var.getValue().getRelatedType().addAll(relatedType);
        }

        retVar.setPolluted(true);
        retVar.getValue().getRelatedType().addAll(relatedType);
        setResult(retVar);
    }
}
