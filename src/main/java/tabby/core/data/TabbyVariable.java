package tabby.core.data;

import lombok.Data;
import soot.*;
import soot.jimple.FieldRef;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author wh1t3P1g
 * @since 2020/11/26
 */
@Data
public class TabbyVariable {

    private String name;
    private int aliasId; // 变量索引
    private TabbyValue value;
    private Local origin;
    private boolean isPolluted = false;
    private boolean dependOnMethod = false;

    private TabbyVariable(){

    }

    private TabbyVariable(Value sootValue, TabbyValue tabbyValue){
        name = sootValue.toString();
        if(sootValue instanceof Local){
            origin = (Local) sootValue;
        }else if(sootValue instanceof FieldRef){
            name = ((FieldRef) sootValue).getFieldRef().name();
        }
        value = tabbyValue;
    }

    /**
     * 初始化一个有value的变量
     * @param sootValue
     * @return
     */
    public static TabbyVariable newInstance(Value sootValue){
        TabbyValue tabbyValue = TabbyValue.newInstance(sootValue);
        return new TabbyVariable(sootValue, tabbyValue);
    }

    /**
     * 初始化一个没有value的变量
     * @param local
     * @return
     */
    public static TabbyVariable newInstance(Local local){
        TabbyVariable var = new TabbyVariable();
        var.setOrigin(local);
        var.setName(local.getName());
        return var;
    }

    public static TabbyVariable newInstance(SootFieldRef fieldRef){
        TabbyVariable var = new TabbyVariable();
        var.setName(fieldRef.name());
        TabbyValue val = new TabbyValue();
        val.setType(fieldRef.type());
        val.setTypeName(fieldRef.type().toString());
        val.setField(true);
        val.setStatic(fieldRef.isStatic());
        var.setValue(val);
        return var;
    }

    public static TabbyVariable newInstance(SootMethod method){
        TabbyVariable var = new TabbyVariable();
        var.setName("value from "+method.getName());
        var.setValue(TabbyValue.newInstance(method));
        return var;
    }

    public TabbyVariable clone(boolean deepClone, List<TabbyVariable> clonedVars){
        TabbyVariable clonedVar = null;
        // try to find from cache
        if(clonedVars.contains(this)) {
            return clonedVars.get(clonedVars.indexOf(this));
        }else{
            clonedVars.add(this);
        }
        // try to clone value
        clonedVar = new TabbyVariable();
        clonedVar.setName(name);
        clonedVar.setOrigin(origin);
        clonedVar.setValue(value != null ? value.clone(deepClone, clonedVars) : null);
        clonedVar.setAliasId(aliasId);
        clonedVar.setPolluted(isPolluted);
        clonedVar.setDependOnMethod(dependOnMethod);
        return clonedVar;
    }

    public void assign(TabbyValue value){
        this.value = value.clone(false, new ArrayList<>());
    }

    public void assign(TabbyVariable var){
        if(var.getValue() != null){
            this.value = var.getValue().clone(false, new ArrayList<>());
        }
        isPolluted = var.isPolluted();
    }

    public TabbyVariable getField(SootFieldRef sfr){
        if(value != null && value.hasFields()){
            return value.getFieldMap().getOrDefault(sfr, null);
        }
        return null;
    }

    public void addField(SootFieldRef sfr, TabbyVariable var){
        if(value != null){
            if(value.getFieldMap() != null){
                value.getFieldMap().put(sfr, var);
            }else{
                Map<SootFieldRef, TabbyVariable> fieldMap = new HashMap<>();
                fieldMap.put(sfr, var);
                value.setFieldMap(fieldMap);
            }
        }
    }

    public void removeField(SootFieldRef sfr){
        if(value != null){
            if(value.getFieldMap() != null){
                value.getFieldMap().remove(sfr);
            }
        }
    }

    public TabbyVariable getElement(int index){
        if(value != null && value.getType() instanceof ArrayType){
            return value.getElement(index);
        }
        return null;
    }

    public void addElement(int index, TabbyVariable var){ // 只要数组中的某个值可控，那么默认当前变量可控
        if(value != null && value.getType() instanceof ArrayType){
            isPolluted = isPolluted() || var.isPolluted();
            value.addElement(index, var);
        }
    }

    public void removeElement(int index){
        if(value != null && value.getType() instanceof ArrayType){
            value.removeElement(index);
        }
    }


}
