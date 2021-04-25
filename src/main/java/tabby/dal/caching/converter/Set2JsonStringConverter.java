package tabby.dal.caching.converter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

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
public class Set2JsonStringConverter implements AttributeConverter<Set<String>,String> {

    private static Gson gson = new Gson();

    @Override
    public String convertToDatabaseColumn(Set<String> attribute) {
        if(attribute == null){
            return "";
        }
        return gson.toJson(attribute);
    }

    @Override
    public Set<String> convertToEntityAttribute(String dbData) {
        if(dbData == null || "".equals(dbData)){
            return new HashSet<>();
        }
        Type objectType = new TypeToken<Set<String>>(){}.getType();
        return gson.fromJson(dbData, objectType);
    }
}
