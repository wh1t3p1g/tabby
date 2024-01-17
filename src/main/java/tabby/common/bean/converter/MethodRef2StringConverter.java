package tabby.common.bean.converter;

import tabby.common.bean.ref.MethodReference;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * @author wh1t3P1g
 * @since 2021/1/8
 */
@Converter
public class MethodRef2StringConverter implements AttributeConverter<MethodReference,String> {
    @Override
    public String convertToDatabaseColumn(MethodReference attribute) {
        if(attribute == null){
            return "";
        }

        return attribute.getId();
    }

    @Override
    public MethodReference convertToEntityAttribute(String dbData) {
        if(dbData == null || "".equals(dbData)){
            return null;
        }
        MethodReference methodRef = new MethodReference();
        methodRef.setId(dbData);
        return methodRef;
    }
}
