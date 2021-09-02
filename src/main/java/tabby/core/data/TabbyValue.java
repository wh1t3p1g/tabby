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

    private TabbyStatus status = new TabbyStatus();

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

    public TabbyValue(Type type, String relatedType){
        uuid = UUID.randomUUID();
        this.type = type;
        typeName = type.toString();

        isArray = isArrayType(type);
        status.setPolluted(true);
        status.setType(relatedType);
    }

    public static TabbyValue newInstance(Local value){
        return new TabbyValue(value);
    }

    public TabbyValue deepClone(){
        // try to clone value
        TabbyValue newValue = new TabbyValue();
        newValue.setUuid(uuid);
        newValue.setField(isField);
        newValue.setArray(isArray);
        newValue.setStatic(isStatic);
        newValue.setType(type);
        newValue.setTypeName(typeName);
        newValue.setOrigin(origin);
        newValue.setStatus(status.clone());

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

    public String getRelatedType(){
        return status.getFirstPollutedType();
    }

    public void setRelatedType(String type){
        status.setType(type);
    }

    public boolean isPolluted(){
        return status.isPolluted();
    }

    public void setPolluted(boolean polluted){
        status.setPolluted(polluted);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        TabbyValue that = (TabbyValue) o;

        return new EqualsBuilder()
                .append(isArray, that.isArray)
                .append(isField, that.isField)
                .append(isStatic, that.isStatic)
                .append(type, that.type).append(typeName, that.typeName)
//                .append(status.isPolluted, that.status.isPolluted).isEquals();
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37)
                .append(type).append(typeName).append(isArray)
                .append(isField).append(isStatic)
//                .append(status.isPolluted).toHashCode();
                .toHashCode();
        // TODO 关于污点追踪这块的实现，其实是存在缺陷的
        //  当前使用的relatedType并不是一个很好的方案
        //  等当前事情过去，再想个合适的方法
    }
}
