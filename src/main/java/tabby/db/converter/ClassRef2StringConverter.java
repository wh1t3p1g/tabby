package tabby.db.converter;

import tabby.db.bean.ref.ClassReference;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

/**
 * @author wh1t3P1g
 * @since 2021/1/8
 */
@Converter
public class ClassRef2StringConverter implements AttributeConverter<ClassReference,String> {
    @Override
    public String convertToDatabaseColumn(ClassReference attribute) {
        if(attribute == null){
            return "";
        }

        return attribute.getId();
    }

    @Override
    public ClassReference convertToEntityAttribute(String dbData) {
        if(dbData == null || "".equals(dbData)){
            return null;
        }
        ClassReference classRef = new ClassReference();
        classRef.setId(dbData);
        return classRef;
    }
}
