package tabby.core.data;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import soot.ArrayType;
import soot.Local;
import soot.Type;
import soot.Value;

import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

/**
 * @author wh1t3P1g
 * @since 2020/11/26
 */
@Getter
@Setter
public class TabbyValue implements Serializable {

    private UUID uuid;
    private Type type;
    private String typeName;
    private Value origin;
    // status
    private boolean isArray = false;
    private boolean isField = false;
    private boolean isStatic = false;

    // polluted
    private boolean isPolluted = false;
    // polluted positions like param-0,param-1,field-name1,this
    private String relatedType = null;
    private Date date = new Date(System.currentTimeMillis());

    public TabbyValue(){
        uuid = UUID.randomUUID();
    }

    public TabbyValue(Local value){
        uuid = UUID.randomUUID();
        type = value.getType();
        typeName = type.toString();
        origin = value;

        isArray = isArrayType(value.getType());
    }

    public static TabbyValue newInstance(Local value){
        return new TabbyValue(value);
    }

    public TabbyValue deepClone(){
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

    public boolean before(TabbyValue value){
        return date.before(value.getDate());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        TabbyValue that = (TabbyValue) o;

        return new EqualsBuilder().append(isArray, that.isArray).append(isField, that.isField).append(isStatic, that.isStatic).append(isPolluted, that.isPolluted).append(type, that.type).append(typeName, that.typeName).append(relatedType, that.relatedType).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(type).append(typeName).append(isArray).append(isField).append(isStatic).append(isPolluted).append(relatedType).toHashCode();
    }
}
