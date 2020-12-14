package tabby.core.soot.switcher.value;

import lombok.Getter;
import lombok.Setter;
import soot.Local;
import soot.SootFieldRef;
import soot.Value;
import soot.jimple.ArrayRef;
import soot.jimple.InstanceFieldRef;
import soot.jimple.IntConstant;
import soot.jimple.StaticFieldRef;
import tabby.core.data.TabbyVariable;

/**
 * @author wh1t3P1g
 * @since 2020/11/26
 */
@Getter
@Setter
public class SimpleLeftValueSwitcher extends ValueSwitcher {

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
                if(rvar.isPolluted()){
                    baseVar.setPolluted(true);
                    baseVar.getValue().getRelatedType().addAll(rvar.getValue().getRelatedType());
                }
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
                var = TabbyVariable.newInstance(sootFieldRef);
                var.setPolluted(baseVar.isPolluted());
            }
            if(unbind){
                var.removeField(sootFieldRef);
            }else{
                var.assign(rvar);
                baseVar.addField(sootFieldRef, var);
            }
        }
    }

}
