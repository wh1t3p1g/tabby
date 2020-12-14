package tabby.core.data;

import lombok.Data;
import soot.ArrayType;
import soot.SootFieldRef;
import soot.Type;
import soot.Value;
import soot.jimple.FieldRef;

import java.io.Serializable;
import java.util.*;

/**
 * @author wh1t3P1g
 * @since 2020/11/26
 */
@Data
public class TabbyValue implements Serializable {

    // status
    private boolean isThis = false;
    private boolean isParam = false;
    private boolean isField = false;
    private boolean isArray = false;
    private boolean isStatic = false;
    private boolean isPolluted = false;

    // fields
    private Map<SootFieldRef, TabbyVariable> fieldMap = new HashMap<>();

    // arrays
    private Map<Integer, TabbyVariable> elements = new HashMap<>();

    // params
    private int paramIndex;
    // polluted positions like param-0,param-1,field-name1,this
    private String relatedType;

    public TabbyValue(){}

    public static TabbyValue newInstance(Value value){
        TabbyValue val = new TabbyValue();
        val.setArray(isArrayType(value.getType()));
        if (value instanceof FieldRef){
            FieldRef fr = (FieldRef) value;
            val.setField(true);
            val.setStatic(fr.getFieldRef().isStatic());
        }
        return val;
    }

    /**
     * 会有递归clone的问题
     * @param deepClone
     * @return
     */
    public TabbyValue clone(boolean deepClone, List<TabbyVariable> clonedVars){
        // try to clone value
        TabbyValue newValue = new TabbyValue();
        newValue.setField(isField);
        newValue.setArray(isArray);
        newValue.setParam(isParam);
        newValue.setPolluted(isPolluted);
        newValue.setStatic(isStatic);
        newValue.setParamIndex(paramIndex);
        newValue.setRelatedType(relatedType);
        // try to clone elements and fieldMap
        if(deepClone){
            Map<Integer, TabbyVariable> newElements = new HashMap<>();
            Map<SootFieldRef, TabbyVariable> newFields = new HashMap<>();

            for(Map.Entry<Integer, TabbyVariable> entry:newElements.entrySet()){
                TabbyVariable var = entry.getValue();
                newElements.put(entry.getKey(), var != null ? var.clone(deepClone, clonedVars) : null);
            }

            for (Map.Entry<SootFieldRef, TabbyVariable> entry : fieldMap.entrySet()) {
                SootFieldRef sfr = entry.getKey();
                TabbyVariable field = entry.getValue();
                newFields.put(sfr, field != null ? field.clone(deepClone, clonedVars) : null);
            }
            newValue.setElements(newElements);
            newValue.setFieldMap(newFields);
        }else{
            if(elements != null){
                newValue.setElements(new HashMap<>(elements));
            }
            if(fieldMap != null){
                newValue.setFieldMap(new HashMap<>(fieldMap));
            }
        }

        return newValue;
    }

    public boolean hasFields(){
        return fieldMap != null && !fieldMap.isEmpty();
    }

    public boolean hasElements(){
        return elements != null && !elements.isEmpty();
    }

    public void addElement(int index, TabbyVariable var){
        if(elements != null && isArray){
            elements.put(index, var);
        }
    }

    public void removeElement(int index){
        if(elements != null){
            elements.remove(index);
        }
    }

    public TabbyVariable getElement(int index){
        if(elements != null){
            return elements.getOrDefault(index, null);
        }
        return null;
    }

    public static boolean isArrayType(Type type){
        if(type instanceof ArrayType){
            return true;
        }else if("java.util.List".equals(type.toString())
                    && "java.util.Collection".equals(type.toString())
        ){
            return true;
        }
        return false;
    }
}
