package tabby.caching.converter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

/**
 * @author wh1t3P1g
 * @since 2021/1/8
 */
@Converter
public class Map2JsonStringConverter implements AttributeConverter<Map<String, String>,String> {

    private static Gson gson = new Gson();


    @Override
    public String convertToDatabaseColumn(Map<String, String> attribute) {
        if(attribute == null){
            return "{}";
        }
        return gson.toJson(attribute);
    }

    @Override
    public Map<String, String> convertToEntityAttribute(String dbData) {
        if(dbData == null || "".equals(dbData)){
            return new HashMap<>();
        }
        Type objectType = new TypeToken<Map<String, String>>(){}.getType();
        return gson.fromJson(dbData, objectType);
    }
}
