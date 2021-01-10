package tabby.db.converter;

import tabby.config.GlobalConfiguration;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
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

        return (Map<String, String>) GlobalConfiguration.GSON.fromJson(dbData, Map.class);
    }
}
