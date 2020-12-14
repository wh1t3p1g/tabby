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
    private Type type;
    private String typeName;
    private TabbyValue value;
    private Value origin;

    private TabbyVariable(){
    }

    private TabbyVariable(Value sootValue, TabbyValue tabbyValue){
        name = sootValue.toString();
        type = sootValue.getType();
        typeName = type.toString();
        if(sootValue instanceof FieldRef){
            name = typeName + "." + ((FieldRef) sootValue).getFieldRef().name();
        }
        value = tabbyValue;
        origin = sootValue;
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
        clonedVar.setType(type);
        clonedVar.setOrigin(origin);
        clonedVar.setValue(value != null ? value.clone(deepClone, clonedVars) : null);
        return clonedVar;
    }

    public void assign(TabbyValue value){
        this.value = value.clone(false, new ArrayList<>());
    }

    public void assign(TabbyVariable var){
        if(var.getValue() != null){
            this.value = var.getValue().clone(false, new ArrayList<>());
        }
    }

    public TabbyVariable getField(SootFieldRef sfr){
        if(value != null && value.hasFields()){
            return value.getFieldMap().getOrDefault(sfr, null);
        }
        return null;
    }

    public void addField(SootFieldRef sfr, TabbyVariable var){
        if(value != null){
            value.setRelatedType(var.value.getRelatedType());
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


    public boolean isPolluted(){
        if(value != null){
            return value.isPolluted();
        }
        return false;
    }

    public TabbyVariable findFieldVarByName(String name){
        String fieldName = typeName + "." + name;
        if(value.hasFields()){
            for(TabbyVariable var:value.getFieldMap().values()){
                if(fieldName.equals(var.getName())){
                    return var;
                }
            }
        }
        return null;
    }

    public void modify(String position, String related){
        if(value == null) return;
        if(position.startsWith("param") || position.equals("return")){
            if("clear".equals(related)){
                value.setPolluted(false);
                value.setRelatedType(null);
            }else{
                value.setRelatedType(related);
            }
        }else if(position.startsWith("this")){
            String name = position.split("\\.")[1];
            TabbyVariable fieldVar = findFieldVarByName(name);
            if(fieldVar != null){
                if("clear".equals(related)){
                    fieldVar.value.setPolluted(false);
                    fieldVar.value.setRelatedType(null);
                }else{
                    fieldVar.value.setRelatedType(related);
                }
            }
        }

    }
}
