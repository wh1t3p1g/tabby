package tabby.core.switcher.value;

import lombok.Getter;
import lombok.Setter;
import soot.Local;
import soot.PrimType;
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
        if(v.getType() instanceof PrimType) return; // 提出无用的类属性传递

        TabbyVariable var = context.getOrAdd(v);

        generateAction(var, rvar, -1, unbind);
        if(unbind){
            var.clearVariableStatus();
        }else{
            var.assign(rvar, false);
        }
    }

    /**
     * case Class.field = rvar
     * @param v
     */
    public void caseStaticFieldRef(StaticFieldRef v) {
        if(v.getField().getType() instanceof PrimType) return; // 提出无用的类属性传递
        TabbyVariable var = context.getOrAdd(v);
        if(unbind){
            context.unbind(v);
        } else {
            var.assign(rvar, false);
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
            generateAction(baseVar, rvar, index, unbind);
            if(unbind){
                baseVar.clearElementStatus(index);
            }else{
                baseVar.assign(index, rvar);
            }
        }else if(indexValue instanceof Local){
            // 存在lvar = a[i2] 这种情况，暂无法推算处i2的值是什么，存在缺陷这部分；近似处理，添加到最后一个位置上
            int size = baseVar.getElements().size();
            generateAction(baseVar, rvar, size, unbind);
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
        if(v.getField().getType() instanceof PrimType) return; // 提出无用的类属性传递 是否需要剔除static类型？
        TabbyVariable fieldVar = context.getOrAdd(v);

        if(unbind){
            fieldVar.clearVariableStatus();
        }else{
            fieldVar.assign(rvar, false);
        }
    }

    public void generateAction(TabbyVariable lvar, TabbyVariable rvar, int index, boolean unbind){
        if(!reset) return; // 不记录 actions
        if(unbind && lvar.isPolluted(-1)){
            if(index != -1){
                methodRef.addAction(lvar.getValue().getRelatedType() + "|"+index, "clear");
            }else{
                methodRef.addAction(lvar.getValue().getRelatedType(), "clear");
            }
        }else if(lvar.isPolluted(-1)){
            if(rvar != null && rvar.isPolluted(-1)){
                if(index != -1){
                    methodRef.addAction(lvar.getValue().getRelatedType() + "|"+index, rvar.getValue().getRelatedType());
                }else if(!lvar.getValue().getRelatedType().equals(rvar.getValue().getRelatedType())){
                    methodRef.addAction(lvar.getValue().getRelatedType(), rvar.getValue().getRelatedType());
                }

            }else{
                if(index != -1){
                    methodRef.addAction(lvar.getValue().getRelatedType() + "|"+index, "clear");
                }else{
                    methodRef.addAction(lvar.getValue().getRelatedType(), "clear");
                }
            }
        }
    }

}
