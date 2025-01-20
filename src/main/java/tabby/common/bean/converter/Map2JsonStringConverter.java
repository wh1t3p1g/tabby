package tabby.common.bean.converter;

import com.google.gson.reflect.TypeToken;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tabby.common.utils.JsonUtils;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author wh1t3P1g
 * @since 2021/1/8
 */
@Converter
public class Map2JsonStringConverter implements AttributeConverter<Map<String, Object>, String> {

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        if (attribute == null) {
            return "{}";
        }
//        return JsonUtils.toJsonWithReplace(attribute);
        return JsonUtils.toJson(attribute);
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return new HashMap<>();
        }
        Type objectType = new TypeToken<Map<String, Object>>() {
        }.getType();
//        return new ConcurrentHashMap<>(JsonUtils.fromJsonWithReplace(dbData, objectType));
        return new ConcurrentHashMap<>(JsonUtils.fromJson(dbData, objectType));
    }
}
