package tabby.core.data;

import lombok.Data;
import soot.Local;
import soot.SootFieldRef;
import soot.Value;
import soot.jimple.FieldRef;

import java.util.List;
import java.util.UUID;

/**
 * @author wh1t3P1g
 * @since 2020/11/26
 */
@Data
public class TabbyVariable {

    private String uuid = UUID.randomUUID().toString();
    private String name;
    private Value origin;
    private boolean isThis = false;
    private boolean isParam = false;
    private int paramIndex;
    // 上面的内容，在变量生成的时候就不会改变了
    // 后续会改变的是，当前参数的value
    private TabbyValue value;

    private TabbyVariable(){
    }

    private TabbyVariable(Value sootValue, TabbyValue tabbyValue){
        if(sootValue instanceof Local){
            name = ((Local) sootValue).getName();
        }else if(sootValue instanceof FieldRef){
            FieldRef fr = (FieldRef) sootValue;
            name = tabbyValue.getTypeName() + "|" + fr.getFieldRef().name();
        }else{
            name = sootValue.toString();
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

    public static TabbyVariable makeFieldInstance(TabbyVariable baseVar, SootFieldRef fieldRef){
        TabbyValue tabbyValue = new TabbyValue();
        TabbyVariable tabbyVariable = new TabbyVariable();
        tabbyValue.setType(fieldRef.type());
        tabbyValue.setTypeName(fieldRef.type().toString());
        tabbyValue.setField(true);
        tabbyValue.setStatic(fieldRef.isStatic());
        tabbyVariable.setName(baseVar.getValue().getTypeName() + "|" + fieldRef.name());

        if(baseVar.isPolluted()){
            tabbyValue.setPolluted(true);
            tabbyValue.setRelatedType(baseVar.getValue().getRelatedType());
        }
        tabbyVariable.setValue(tabbyValue);
        return tabbyVariable;
    }

    public static TabbyVariable makeAnyNewRightInstance(Value v){
        TabbyValue tabbyValue = new TabbyValue(v);
        TabbyVariable var = makeRandomInstance();
        var.setOrigin(v);
        var.setValue(tabbyValue);
        return var;
    }

    public static TabbyVariable makeRandomInstance(){
        TabbyValue tabbyValue = new TabbyValue();
        TabbyVariable tabbyVariable = new TabbyVariable();
        tabbyVariable.setName("Temp Variable-"+UUID.randomUUID());
        tabbyVariable.setValue(tabbyValue);
        return tabbyVariable;
    }

    /**
     * case a = b
     * 赋值不用深度copy，根据java的特性默认是不深度copy的
     * 那么 a = b; a,b的value都是同步更新的
     * @param var
     */
    public void assign(TabbyVariable var){
        // copy value
        if(var.getValue() != null){
            this.value = var.getValue();
        }
    }

    /**
     * case a[index] = b
     * @param index
     * @param var
     */
    public void assign(int index, TabbyVariable var){
        TabbyVariable element = value.getElements().get(index);
        if(element != null){
            element.assign(var);
        }else{
            addElement(index, var);
        }
    }

    /**
     * case a.f = b
     * @param sfr
     * @param var
     */
    public void assign(SootFieldRef sfr, TabbyVariable var){
        TabbyVariable fieldVar = value.getFieldMap().get(sfr);
        if(fieldVar != null){
            fieldVar.assign(var);
        }
    }

    public boolean isPolluted(){
        if(value != null){
            return value.isPolluted();
        }
        return false;
    }

    public TabbyVariable findFieldVarByName(String name){
        for(TabbyVariable var : value.getFieldMap().values()){
            if(name.equals(var.getName())){
                return var;
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
                value.setPolluted(true);
                value.setRelatedType(related);
            }
        }else if(position.startsWith("this")){
            String name = position.split("\\|")[1];
            TabbyVariable fieldVar = findFieldVarByName(name);
            if(fieldVar != null){
                if("clear".equals(related)){
                    fieldVar.value.setPolluted(false);
                    fieldVar.value.setRelatedType(null);
                }else{
                    fieldVar.value.setPolluted(true);
                    fieldVar.value.setRelatedType(related);
                }
            }
        }

    }

    public String getName(){
        if(name != null){
            if(value.isField()){
                return name.split("\\|")[1];
            }else{
                return name;
            }
        }
        return null;
    }

    public void clearVariableStatus(){
        value.setPolluted(false);
        value.setRelatedType("clear");
        value.getFieldMap().clear();
        value.getElements().clear();
    }

    public void clearElementStatus(int index){
        TabbyVariable element = value.getElements().get(index);
        element.clearVariableStatus();
    }

    public TabbyVariable getElement(int index){
        return value.getElements().getOrDefault(index, null);
    }

    public void removeElement(int index){
        value.getElements().remove(index);
    }

    public void addElement(int index, TabbyVariable var){
        value.getElements().put(index, var);
    }

    public TabbyVariable getField(SootFieldRef sfr){
        return value.getFieldMap().getOrDefault(sfr, null);
    }

    public void removeField(SootFieldRef sfr){
        value.getFieldMap().remove(sfr);
    }

    public void addField(SootFieldRef sfr, TabbyVariable var){
        value.getFieldMap().put(sfr, var);
    }

    public TabbyVariable getOrAddField(TabbyVariable baseVar, SootFieldRef sfr){
        TabbyVariable fieldVar = baseVar.getField(sfr);
        if(fieldVar == null){
            fieldVar = makeFieldInstance(baseVar, sfr);
            if(baseVar.isPolluted()){
                fieldVar.getValue().setPolluted(true);
                fieldVar.getValue().setRelatedType(baseVar.getValue().getRelatedType()+"|"+fieldVar.getName());
            }
            baseVar.assign(sfr, fieldVar);
        }
        return fieldVar;
    }

    public TabbyVariable deepClone(List<TabbyVariable> clonedVars){
        TabbyVariable clonedVar = null;
        // try to find from cache
        if(clonedVars.contains(this)) {
            return clonedVars.get(clonedVars.indexOf(this));
        }else{
            clonedVars.add(this);
        }
        // try to clone value
        clonedVar = new TabbyVariable();
        clonedVar.setUuid(uuid);
        clonedVar.setName(name);
        clonedVar.setOrigin(origin);
        clonedVar.setParam(isParam);
        clonedVar.setParamIndex(paramIndex);
        clonedVar.setThis(isThis);
        clonedVar.setValue(value != null ? value.deepClone(clonedVars) : null);
        return clonedVar;
    }

    public void setPreRelatedType(){
        if(isPolluted()){
            value.setPolluted(false);
            value.setPreRelatedType(value.getRelatedType());
            value.setRelatedType(null);
        }
        value.getElements().forEach((index, element) -> {
            if(element.isPolluted()){
                element.getValue().setPolluted(false);
                element.getValue().setPreRelatedType(element.getValue().getRelatedType());
                element.getValue().setRelatedType(null);
            }
        });
        value.getFieldMap().forEach((sfr, field) -> {
            if(field.isPolluted()){
                field.getValue().setPolluted(false);
                field.getValue().setPreRelatedType(field.getValue().getRelatedType());
                field.getValue().setRelatedType(null);
            }
        });
    }
}
