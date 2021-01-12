package tabby.db.converter;

import com.google.gson.reflect.TypeToken;
import tabby.config.GlobalConfiguration;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

/**
 * @author wh1t3P1g
 * @since 2021/1/8
 */
@Converter
public class SetInteger2JsonStringConverter implements AttributeConverter<Set<Integer>,String> {
    @Override
    public String convertToDatabaseColumn(Set<Integer> attribute) {
        if(attribute == null){
            return "[]";
        }
        return GlobalConfiguration.GSON.toJson(attribute);
    }

    @Override
    public Set<Integer> convertToEntityAttribute(String dbData) {
        if(dbData == null || "".equals(dbData)){
            return new HashSet<>();
        }
        Type objectType = new TypeToken<Set<Integer>>(){}.getType();
        return GlobalConfiguration.GSON.fromJson(dbData, objectType);
    }
}
