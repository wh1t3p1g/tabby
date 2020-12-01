package tabby.core.soot.switcher;

import lombok.Getter;
import lombok.Setter;
import soot.Local;
import soot.SootFieldRef;
import soot.Value;
import soot.jimple.*;
import tabby.core.data.Context;
import tabby.core.data.TabbyVariable;

/**
 * @author wh1t3P1g
 * @since 2020/11/26
 */
@Getter
@Setter
public class LValueSwitcher extends AbstractJimpleValueSwitch {

    private Context context;
    private TabbyVariable rvar;
    private boolean unbind = false;

    public LValueSwitcher(Context context, TabbyVariable rvar, boolean unbind) {
        this.context = context;
        this.rvar = rvar;
        this.unbind = unbind;
    }

    public void caseArrayRef(ArrayRef v) {
        Value baseValue = v.getBase();
        Value indexValue = v.getIndex();
        TabbyVariable baseVar = context.getOrAdd(baseValue);
        if (indexValue instanceof IntConstant) {
            int index = ((IntConstant) indexValue).value;
            if(unbind){
                baseVar.removeElement(index);
            }else{
                baseVar.addElement(index, rvar);
            }
        }else if(indexValue instanceof Local){
            // 存在lvar = a[i2] 这种情况，暂无法推算处i2的值是什么，存在缺陷这部分
        }
    }

    public void caseLocal(Local v) {
        if(unbind){
            context.unbind(v);
        }else{
            TabbyVariable var = context.getOrAdd(v);
            var.assign(rvar);
        }
    }

    public void caseStaticFieldRef(StaticFieldRef v) {
        TabbyVariable var = context.getOrAdd(v);
        if(unbind){
            context.unbind(v);
        } else {
            var.assign(rvar);
        }
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
            if(unbind){
                var.removeField(sootFieldRef);
            }else{
                var.assign(rvar);
            }
        }
    }

}
