package tabby.core.soot.switcher.value;

import lombok.Getter;
import lombok.Setter;
import soot.Local;
import soot.SootMethod;
import soot.Value;
import soot.jimple.*;
import tabby.core.data.TabbyVariable;

import java.util.HashSet;
import java.util.Set;

/**
 * 需要额外清晰的处理函数调用时的情况，即域敏感分析
 * @author wh1t3P1g
 * @since 2020/11/26
 */
@Getter
@Setter
public class AccurateRightValueSwitcher extends SimpleRightValueSwitcher {

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
}
