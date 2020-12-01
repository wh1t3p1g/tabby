package tabby.core.data;

import lombok.Data;
import soot.*;
import soot.jimple.FieldRef;

import java.io.Serializable;
import java.util.*;

/**
 * @author wh1t3P1g
 * @since 2020/11/26
 */
@Data
public class TabbyValue implements Serializable {

    private Value origin;
    private Type type;
    private String typeName;
    // field
    private boolean isStatic = false;
    private boolean isField = false;
    private String staticFieldDeclaringClass;
    private Map<SootFieldRef, TabbyVariable> fieldMap;

    // array
    private List<TabbyVariable> elements;

    // method return
    private String methodSignature;
    private Set<Integer> controllableArgs = new HashSet<>();
    private boolean isMethodReturn = false;
    private TabbyVariable base;

    // param
    private boolean isParam = false;
    private int paramIndex;
    private Set<Integer> polluted = new HashSet<>(); // 标记可能污染的点，由下一层函数分析后决定，跟gadgetinspector一样 0表示对象的类属性，1-n 表示函数参数位置

    public TabbyValue(){}

    private TabbyValue(Value value) {
        this.origin = value;
        this.type = value != null ? value.getType(): null;
        this.typeName = type == null ? null : type.toString();

        if (type instanceof RefLikeType) {
            fieldMap = new HashMap<>();
        }
        if (type instanceof ArrayType) {
            elements = Collections.emptyList();
        }
        if (value instanceof FieldRef){
            FieldRef fr = (FieldRef) value;
            isField = true;
            isStatic = fr.getFieldRef().isStatic();
            if(isStatic){
                staticFieldDeclaringClass = fr.getFieldRef().declaringClass().toString();
            }
        }
    }

    public static TabbyValue newInstance(Value value){
        return new TabbyValue(value);
    }

    public static TabbyValue newInstance(SootMethod method){
        TabbyValue val = new TabbyValue();
        val.setMethodReturn(true);
        val.setMethodSignature(method.getSignature());
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
        newValue.setType(type);
        newValue.setTypeName(typeName);
        newValue.setField(isField);
        newValue.setStatic(isStatic);
        newValue.setOrigin(origin);
        newValue.setControllableArgs(controllableArgs);
        newValue.setPolluted(polluted);
        newValue.setMethodSignature(methodSignature);
        newValue.setMethodReturn(isMethodReturn);
        if(base != null){
            newValue.setBase(deepClone ? base.clone(deepClone, clonedVars) : base);
        }
        // try to clone elements and fieldMap
        if(deepClone){
            if(newValue.isArray() && elements != null){
                newValue.setElements(new ArrayList<>(elements.size()));
                for (TabbyVariable element : elements) {
                    newValue.getElements().add(element != null ? element.clone(deepClone, clonedVars) : null);
                }
            }
            if(hasFields()){
                newValue.setFieldMap(new HashMap<>());
                for (Map.Entry<SootFieldRef, TabbyVariable> entry : fieldMap.entrySet()) {
                    SootFieldRef sfr = entry.getKey();
                    TabbyVariable field = entry.getValue();
                    newValue.getFieldMap().put(sfr, field != null ? field.clone(deepClone, clonedVars) : null);
                }
            }

        }else{
            if(elements != null){
                newValue.setElements(new ArrayList<>(elements));
            }
            if(fieldMap != null){
                newValue.setFieldMap(new HashMap<>(fieldMap));
            }
        }

        return newValue;
    }

    public void asFixedArray(int size) {
        if (!isArray()) {
            throw new IllegalStateException("not a array: " + toString());
        }
        elements = new ArrayList<>(size);
        for (int i = size; i > 0; i--) {
            elements.add(null);
        }
    }

    public boolean isArray(){
        return type instanceof ArrayType;
    }

    public boolean hasFields(){
        return fieldMap != null && !fieldMap.isEmpty();
    }

    public void addElement(int index, TabbyVariable var){
        if(elements != null && elements.size() > index){
            elements.add(index, var);
        }
    }

    public void removeElement(int index){
        if(elements != null && elements.size() > index){
            elements.add(index, null);
        }
    }

    public TabbyVariable getElement(int index){
        if(elements != null && elements.size() > index){
            return elements.get(index);
        }
        return null;
    }

    /**
     * a.f = variable
     * @param sfr
     * @param var
     */
    public void assign(SootFieldRef sfr, TabbyVariable var){
        if(fieldMap != null){
            fieldMap.put(sfr, var.clone(false, new ArrayList<>()));
        }
    }

    /**
     * a[i] = variable
     * @param index
     * @param var
     */
    public void assign(int index, TabbyVariable var){
        if(!isArray()){
            throw new IllegalStateException("not a array: " + toString());
        }
        if(elements != null && index < elements.size()){
            elements.add(index, var.clone(false, new ArrayList<>()));
        }
    }
}
