package tabby.core.data;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import soot.*;
import soot.jimple.FieldRef;
import soot.jimple.StaticFieldRef;

import java.util.*;

/**
 * @author wh1t3P1g
 * @since 2020/11/26
 */
@Getter
@Setter
public class TabbyVariable {

    private String uuid;
    private String name;
    private Value origin;
    private boolean isThis = false;
    private boolean isParam = false;
    private int paramIndex;
    // 上面的内容，在变量生成的时候就不会改变了
    // 后续会改变的是，当前参数的value
    private TabbyValue value;
    private TabbyVariable owner = null;

    // fields
    private Map<String, TabbyVariable> fieldMap = new HashMap<>();
    // arrays
    private Map<Integer, TabbyVariable> elements = new HashMap<>();


    private TabbyVariable(){
        uuid = UUID.randomUUID().toString();
    }

    private TabbyVariable(String uuid){
        this.uuid = uuid;
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
        uuid = UUID.randomUUID().toString();
        value = tabbyValue;
        origin = sootValue;
    }

    public static TabbyVariable makeSpecialLocalInstance(Local local, String relatedType){
        TabbyValue tabbyValue = TabbyValue.newInstance(local);
        TabbyVariable tabbyVariable = new TabbyVariable(local, tabbyValue);

        if(!(local.getType() instanceof PrimType) && !(local.getType() instanceof ArrayType)) {
            SootClass cls = ((RefType) local.getType()).getSootClass();
            for (SootField sootField : cls.getFields()) {// construct fields
                TabbyVariable fieldVar = makeFieldInstance(null, sootField);
                fieldVar.getValue().setPolluted(true);
                fieldVar.getValue().setRelatedType(relatedType + "|" + sootField.getName());
                fieldVar.getValue().setRelatedVar(fieldVar);
                tabbyVariable.fieldMap.put(sootField.getName(), fieldVar);
            }
        }
        return tabbyVariable;
    }

    public static TabbyVariable makeLocalInstance(Local local){
        TabbyValue tabbyValue = TabbyValue.newInstance(local);
        TabbyVariable tabbyVariable = new TabbyVariable(local, tabbyValue);
        if(!(local.getType() instanceof PrimType) && !(local.getType() instanceof ArrayType)){
            SootClass cls = ((RefType)local.getType()).getSootClass();
            for(SootField sootField:cls.getFields()){// construct fields
                TabbyVariable fieldVar = makeFieldInstance(null, sootField);
                fieldVar.setOwner(tabbyVariable);
                tabbyVariable.fieldMap.put(sootField.getName(), fieldVar);
            }
        }
        return tabbyVariable;
    }

    public static TabbyVariable makeStaticFieldInstance(StaticFieldRef staticFieldRef){
        SootField sootField = staticFieldRef.getField();
        return makeFieldInstance(null, sootField);
    }

    public static TabbyVariable makeFieldInstance(TabbyVariable baseVar, SootField sootField){
        TabbyValue tabbyValue = new TabbyValue();
        TabbyVariable fieldVar = new TabbyVariable();
        tabbyValue.setType(sootField.getType());
        tabbyValue.setTypeName(sootField.getType().toString());
        tabbyValue.setField(true);
        tabbyValue.setStatic(sootField.isStatic());
        tabbyValue.setArray(TabbyValue.isArrayType(sootField.getType()));
        fieldVar.setName(sootField.getName());
        fieldVar.setOwner(baseVar);
        fieldVar.setValue(tabbyValue);

        if(baseVar != null && baseVar.isPolluted()){
            tabbyValue.setPolluted(true);
            tabbyValue.setRelatedType(baseVar.constructRelatedType() + "|" + sootField.getName());
            tabbyValue.setRelatedVar(fieldVar);
        }

        return fieldVar;
    }


    public static TabbyVariable makeRandomInstance(){
        TabbyValue tabbyValue = new TabbyValue();
        TabbyVariable tabbyVariable = new TabbyVariable();
        tabbyVariable.setName("Temp Variable");
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
        if(var != null && var.getValue() != null){
            value = var.getValue();
            elements = var.getElements();
            fieldMap = var.getFieldMap();
        }
    }

    /**
     * case a[index] = b
     * @param index
     * @param var
     */
    public void assign(int index, TabbyVariable var){
        TabbyVariable element = elements.get(index);
        var.owner = this;
        if(element != null){
            element.assign(var);
        }else{
            boolean flag = true;
            for(TabbyVariable temp: elements.values()){
                if(temp.getOrigin() != null && temp.getOrigin().equals(var.getOrigin())){
                    flag = false;
                    break;
                }
            }
            if(flag){
                addElement(index, var);
            }
        }
    }

    /**
     * case a.f = b
     * @param sfr
     * @param var
     */
    public void assign(String sfr, TabbyVariable var){
        TabbyVariable fieldVar = fieldMap.get(sfr);
        var.owner = this;
        if(fieldVar != null){
            fieldVar.assign(var);
        }else{
            fieldMap.put(sfr, var);
        }
    }

    public boolean isPolluted(){
        if(value != null && value.isPolluted()){
            return true;
        }else if(value != null){
            for(TabbyVariable var: elements.values()){
                if(var.getValue().isPolluted()){
                    return true;
                }
            }

            for(TabbyVariable var: elements.values()){
                if(var.getValue().isPolluted()){
                    return true;
                }
            }
        }
        return false;
    }

    public String constructRelatedType(){
        List<String> retStrs = new LinkedList<>();
        if(owner != null){
            retStrs.add(owner.constructRelatedType());
        }
        if(isThis){
            retStrs.add("this");
        }else if(isParam){
            retStrs.add("param-"+paramIndex);
        }else{
            retStrs.add(name);
        }
        return String.join("|", retStrs);
    }

    public String getName(){
        if(name != null){
            if(value.isField() && name.contains("|")){
                return name.split("\\|")[1];
            }else{
                return name;
            }
        }
        return null;
    }

    public String getPosition(){
        if(isThis){
            return "this";
        }else if(isParam){
            return "param-"+paramIndex;
        }else{
            return null;
        }
    }

    public void clearVariableStatus(){
        value.setPolluted(false);
        value.setRelatedType("clear");
        fieldMap.clear();
        elements.clear();
    }

    public void clearElementStatus(int index){
        TabbyVariable element = elements.get(index);
        if(element != null){
            element.clearVariableStatus();
        }
    }

    public void clearFieldStatus(SootFieldRef sfr){
        TabbyVariable field = fieldMap.get(sfr.name());
        if(field != null){
            field.clearVariableStatus();
        }
    }

    public TabbyVariable getElement(int index){
        return elements.getOrDefault(index, null);
    }

    public void removeElement(int index){
        elements.remove(index);
    }

    public void addElement(int index, TabbyVariable var){
        if(!elements.containsValue(var)){
            elements.put(index, var);
        }
    }

    public TabbyVariable getField(String sfr){
        return fieldMap.getOrDefault(sfr, null);
    }

    public void removeField(String sfr){
        fieldMap.remove(sfr);
    }

    public void addField(String sfr, TabbyVariable var){
        fieldMap.put(sfr, var);
    }

    public TabbyVariable getOrAddField(TabbyVariable baseVar, SootField sf){
        TabbyVariable fieldVar = baseVar.getField(sf.getName());
        if(fieldVar == null){
            fieldVar = makeFieldInstance(baseVar, sf);
            baseVar.assign(sf.getName(), fieldVar);
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
        clonedVar = new TabbyVariable(uuid);
        clonedVar.setName(name);
        clonedVar.setOrigin(origin);
        clonedVar.setParam(isParam);
        clonedVar.setParamIndex(paramIndex);
        clonedVar.setThis(isThis);
        clonedVar.setOwner(owner);
        clonedVar.setValue(value != null ? value.deepClone() : null);

        Map<Integer, TabbyVariable> newElements = new HashMap<>();
        Map<String, TabbyVariable> newFields = new HashMap<>();

        for(Map.Entry<Integer, TabbyVariable> entry : elements.entrySet()){
            TabbyVariable var = entry.getValue();
            newElements.put(entry.getKey(), var != null ? var.deepClone(clonedVars) : null);
        }

        for (Map.Entry<String, TabbyVariable> entry : fieldMap.entrySet()) {
            String sfr = entry.getKey();
            TabbyVariable field = entry.getValue();
            newFields.put(sfr, field != null ? field.deepClone(clonedVars) : null);
        }
        clonedVar.setElements(newElements);
        clonedVar.setFieldMap(newFields);

        return clonedVar;
    }

    public void union(TabbyVariable that){
        Map<String, TabbyVariable> newFieldMap = new HashMap<>(that.getFieldMap());
        Map<Integer, TabbyVariable> newElements = new HashMap<>(that.getElements());
        fieldMap.forEach((sfr, field) -> {
            TabbyVariable thatField = newFieldMap.get(sfr);
            if(thatField != null){
                if(thatField.isPolluted()) return;
                if(field.isPolluted()){
                    newFieldMap.put(sfr, field);
                }
            }else{
                newFieldMap.put(sfr, field);
            }
        });
        elements.forEach((index, element) -> {
            TabbyVariable thatElement = newElements.get(index);
            if(thatElement != null){
                if(thatElement.isPolluted()) return;
                if(element.isPolluted()){
                    newElements.put(index, element);
                }
            }else{
                newElements.put(index, element);
            }
        });

        setElements(newElements);
        setFieldMap(newFieldMap);

    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        TabbyVariable that = (TabbyVariable) o;

        return new EqualsBuilder().append(isThis, that.isThis).append(isParam, that.isParam).append(paramIndex, that.paramIndex).append(name, that.name).append(origin, that.origin).append(value, that.value).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(name).append(origin).append(isThis).append(isParam).append(paramIndex).append(value).toHashCode();
    }
}
