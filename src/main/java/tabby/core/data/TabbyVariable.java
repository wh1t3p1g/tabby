package tabby.core.data;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import soot.*;
import soot.jimple.FieldRef;
import soot.jimple.StaticFieldRef;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author wh1t3P1g
 * @since 2020/11/26
 */
@Getter
@Setter
@Slf4j
public class TabbyVariable {

    private String uuid;
    private String name;
    private Value origin;
    private boolean isThis = false;
    private boolean isParam = false;
    private int paramIndex;
    // 上面的内容，在变量生成的时候就不会改变了
    // 后续会改变的是，当前参数的value
    private TabbyValue value = null;
    private TabbyVariable owner = null;
    private String firstPollutedVarRelatedType = null;

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

    public static TabbyVariable makeLocalInstance(Local local){
        TabbyValue tabbyValue = TabbyValue.newInstance(local);
        return new TabbyVariable(local, tabbyValue);
    }

    public static TabbyVariable makeStaticFieldInstance(StaticFieldRef staticFieldRef){
        SootField sootField = staticFieldRef.getField();
        TabbyVariable field = makeFieldInstance(null, sootField);
        field.setOrigin(staticFieldRef);
        return field;
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

        if(baseVar != null && baseVar.isPolluted(-1)){
            tabbyValue.setPolluted(true);
            String type = baseVar.getValue().getRelatedType();
            if(type != null && type.contains(sootField.getSignature())){
                tabbyValue.setRelatedType(type);
            }else{
                tabbyValue.setRelatedType(type + "|" + sootField.getSignature());
            }
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
    public void assign(TabbyVariable var, boolean remain){
        // copy value
        if(var != null && var.getValue() != null){
            if(isPolluted(-1) && remain){
                return;
            }
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
            element.assign(var, false);
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
            fieldVar.assign(var, false);
        }else{
            fieldMap.put(sfr, var);
        }
    }

    public boolean containsPollutedVar(List<TabbyVariable> queue){
        if(queue.contains(this)){
            return value.isPolluted();
        }else{
            queue.add(this);
        }
        if(value.isPolluted()){
            firstPollutedVarRelatedType = value.getRelatedType();
            return true;
        }else if(elements != null && elements.size() >0){
            for(TabbyVariable element:elements.values()){
                if(element.containsPollutedVar(queue)){
                    firstPollutedVarRelatedType = element.getFirstPollutedVarRelatedType();
                    return true;
                }
            }
        }else if(fieldMap != null && fieldMap.size() >0){
            for(TabbyVariable field:fieldMap.values()){
                if(field.containsPollutedVar(queue)){
                    firstPollutedVarRelatedType = field.getFirstPollutedVarRelatedType();
                    return true;
                }
            }
        }
        return false;
    }

    public boolean isPolluted(int index){
        if(value.isPolluted() && index == -1){
            return true;
        }else if(index != -1){
            if(elements.size() > index){
                TabbyVariable var = elements.get(index);
                return var.isPolluted(-1);
            }
        }
        return false;
    }

    public void clearVariableStatus(){
        value.setPolluted(false);
        value.setRelatedType(null);
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

    public SootField getSootField(String sfr){

        Pattern pattern = Pattern.compile("<(.*):\\s(.*)\\s(.*)>");
        Matcher m = pattern.matcher(sfr);
        if(m.find()){
            String classname = m.group(1);
            String fieldname = m.group(3);
            classname = classname.replace("'", "");// 处理'
            fieldname = fieldname.replace("'",""); // 处理'
            SootClass cls = Scene.v().getSootClass(classname);
            try{
                return cls.getFieldByName(fieldname);
            }catch (Exception e){
                e.printStackTrace();
                log.warn(sfr+" field not found!");
            }
        }

        return null;
    }

    public void removeField(String sfr){
        fieldMap.remove(sfr);
    }

    public void addField(String sfr, TabbyVariable var){
        fieldMap.put(sfr, var);
    }

    public TabbyVariable getOrAddField(TabbyVariable baseVar, SootField sf){
        TabbyVariable fieldVar = baseVar.getField(sf.getSignature());
        if(fieldVar == null){
            fieldVar = makeFieldInstance(baseVar, sf);
            baseVar.assign(sf.getSignature(), fieldVar);
        }
        return fieldVar;
    }

    public TabbyVariable deepClone(List<TabbyVariable> clonedVars){
        TabbyVariable clonedVar = null;
        // try to find from cache
        if(clonedVars.contains(this)) {
            return this;
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
        clonedVar.setValue(value==null ?null:value.deepClone() );

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        TabbyVariable that = (TabbyVariable) o;

        if(elements.size() != that.elements.size()) return false;
        if(fieldMap.size() != that.fieldMap.size()) return false;

        EqualsBuilder builder = new EqualsBuilder()
                .append(isThis, that.isThis)
                .append(isParam, that.isParam)
                .append(paramIndex, that.paramIndex)
                .append(name, that.name)
                .append(origin, that.origin)
                .append(value, that.value);

        if(!builder.isEquals()) return false;

        for(Map.Entry<Integer, TabbyVariable> entry:elements.entrySet()){
            if(!equals(entry.getValue(), that.elements.get(entry.getKey()))){
                return false;
            }
        }

        for(Map.Entry<String, TabbyVariable> entry:fieldMap.entrySet()){
            if(!equals(entry.getValue(), that.fieldMap.get(entry.getKey()))){
                return false;
            }
        }

        return true;
    }

    public boolean equals(TabbyVariable var1, TabbyVariable var2){// 只检查一层
        if(var1 == var2) return true;

        if(var1 == null || var2 == null) return false;

        EqualsBuilder builder = new EqualsBuilder()
                .append(var1.isThis, var2.isThis)
                .append(var1.isParam, var2.isParam)
                .append(var1.paramIndex, var2.paramIndex)
                .append(var1.name, var2.name)
                .append(var1.origin, var2.origin)
                .append(var1.value, var2.value);

        return builder.isEquals();
    }

    @Override
    public int hashCode() {
        HashCodeBuilder builder = new HashCodeBuilder(17, 37)
                .append(name).append(origin).append(isThis)
                .append(isParam).append(paramIndex).append(value);

        for(TabbyVariable element:elements.values()){ // 只检查一层
            builder.append(element.name).append(element.origin)
                    .append(element.isThis).append(element.isParam)
                    .append(element.paramIndex).append(element.value);
        }

        for(TabbyVariable field:fieldMap.values()){ // 只检查一层
            builder.append(field.name).append(field.origin)
                    .append(field.isThis).append(field.isParam)
                    .append(field.paramIndex).append(field.value);
        }

        return builder.toHashCode();
    }


}
