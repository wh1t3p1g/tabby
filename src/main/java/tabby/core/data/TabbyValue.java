package tabby.core.data;

import lombok.Data;
import soot.ArrayType;
import soot.SootFieldRef;
import soot.Type;
import soot.Value;
import soot.jimple.FieldRef;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author wh1t3P1g
 * @since 2020/11/26
 */
@Data
public class TabbyValue implements Serializable {

    private String uuid = UUID.randomUUID().toString();
    private Type type;
    private String typeName;
    private Value origin;
    // status


    private boolean isArray = false;

    private boolean isField = false;
    private boolean isStatic = false;
    // params


    // polluted
    private boolean isPolluted = false;
    // polluted positions like param-0,param-1,field-name1,this
    private String relatedType;
    private String preRelatedType;

    // fields
    private Map<SootFieldRef, TabbyVariable> fieldMap = new HashMap<>();
    // arrays
    private Map<Integer, TabbyVariable> elements = new HashMap<>();

    public TabbyValue(){}

    public TabbyValue(Value value){
        type = value.getType();
        typeName = type.toString();
        origin = value;

        isArray = isArrayType(value.getType());

        if (value instanceof FieldRef){
            FieldRef fr = (FieldRef) value;
            isField = true;
            isStatic = fr.getFieldRef().isStatic();
        }
    }

    public static TabbyValue newInstance(Value value){
        return new TabbyValue(value);
    }

    public TabbyValue deepClone(List<TabbyVariable> clonedVars){
        // try to clone value
        TabbyValue newValue = new TabbyValue();
        newValue.setUuid(uuid);
        newValue.setPolluted(isPolluted);
        newValue.setRelatedType(relatedType);
        newValue.setField(isField);
        newValue.setArray(isArray);
        newValue.setStatic(isStatic);
        newValue.setType(type);
        newValue.setTypeName(typeName);
        newValue.setOrigin(origin);

        Map<Integer, TabbyVariable> newElements = new HashMap<>();
        Map<SootFieldRef, TabbyVariable> newFields = new HashMap<>();

        for(Map.Entry<Integer, TabbyVariable> entry : elements.entrySet()){
            TabbyVariable var = entry.getValue();
            newElements.put(entry.getKey(), var != null ? var.deepClone(clonedVars) : null);
        }

        for (Map.Entry<SootFieldRef, TabbyVariable> entry : fieldMap.entrySet()) {
            SootFieldRef sfr = entry.getKey();
            TabbyVariable field = entry.getValue();
            newFields.put(sfr, field != null ? field.deepClone(clonedVars) : null);
        }
        newValue.setElements(newElements);
        newValue.setFieldMap(newFields);
        return newValue;
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
