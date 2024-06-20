package tabby.common.bean.converter;

import com.google.gson.reflect.TypeToken;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tabby.common.utils.JsonUtils;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author wh1t3P1g
 * @since 2021/1/8
 */
@Converter
public class Map2JsonStringForAnnotationsConverter implements AttributeConverter<Map<String, Map<String, Set<String>>>, String> {

    @Override
    public String convertToDatabaseColumn(Map<String, Map<String, Set<String>>> attribute) {
        if(attribute == null){
            return "{}";
        }

        return JsonUtils.toJsonWithReplace(attribute);
    }

    @Override
    public Map<String, Map<String, Set<String>>> convertToEntityAttribute(String dbData) {
        if(dbData == null || "".equals(dbData)){
            return new HashMap<>();
        }
        Type objectType = new TypeToken<Map<String, Map<String, Set<String>>>>(){}.getType();
        return new ConcurrentHashMap<>(JsonUtils.fromJsonWithReplace(dbData, objectType));
    }
}
