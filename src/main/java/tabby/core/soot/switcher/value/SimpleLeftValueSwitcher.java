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

    /**
     * case a = rvar
     * @param v
     */
    public void caseLocal(Local v) {
        TabbyVariable var = context.getOrAdd(v);

        if(unbind){
            var.clearVariableStatus();
        }else{
            var.assign(rvar);
        }
    }

    /**
     * case Class.field = rvar
     * @param v
     */
    public void caseStaticFieldRef(StaticFieldRef v) {
        TabbyVariable var = context.getOrAdd(v);
        if(unbind){
            context.unbind(v);
        } else {
            var.assign(rvar);
        }
    }

    /**
     * case a[index] = rvar
     * @param v
     */
    public void caseArrayRef(ArrayRef v) {
        Value baseValue = v.getBase();
        Value indexValue = v.getIndex();
        TabbyVariable baseVar = context.getOrAdd(baseValue);

        if (indexValue instanceof IntConstant) {
            int index = ((IntConstant) indexValue).value;
            if(unbind){
                baseVar.clearElementStatus(index);
            }else{
                baseVar.assign(index, rvar);
            }
        }else if(indexValue instanceof Local){
            // 存在lvar = a[i2] 这种情况，暂无法推算处i2的值是什么，存在缺陷这部分；近似处理，添加到最后一个位置上
            int size = baseVar.getValue().getElements().size();
            if(!unbind){
                baseVar.assign(size, rvar);
            }// 忽略可控性消除
        }
    }

    /**
     * case a.f = rvar
     * @param v
     */
    public void caseInstanceFieldRef(InstanceFieldRef v) {
        SootFieldRef sootFieldRef = v.getFieldRef();
        Value base = v.getBase();
        if(base instanceof Local){
            TabbyVariable baseVar = context.getOrAdd(base);
            TabbyVariable fieldVar = baseVar.getOrAddField(baseVar, sootFieldRef);
            if(unbind){
                fieldVar.clearVariableStatus();
            }else{
                fieldVar.assign(rvar);
            }
        }
    }

}
