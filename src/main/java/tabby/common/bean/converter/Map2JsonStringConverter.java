package tabby.common.bean.converter;

import com.google.gson.reflect.TypeToken;
import tabby.config.GlobalConfiguration;

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

    @Override
    public String convertToDatabaseColumn(Map<String, String> attribute) {
        if(attribute == null){
            return "{}";
        }
        return GlobalConfiguration.GSON.toJson(attribute);
    }

    @Override
    public Map<String, String> convertToEntityAttribute(String dbData) {
        if(dbData == null || "".equals(dbData)){
            return new HashMap<>();
        }
        Type objectType = new TypeToken<Map<String, String>>(){}.getType();
        return GlobalConfiguration.GSON.fromJson(dbData, objectType);
    }
}
