package tabby.common.bean.converter;

import com.google.gson.reflect.TypeToken;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tabby.common.utils.JsonUtils;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author wh1t3P1g
 * @since 2021/1/8
 */
@Converter
public class ListSet2JsonStringConverter implements AttributeConverter<List<Set<String>>, String> {

    @Override
    public String convertToDatabaseColumn(List<Set<String>> attribute) {
        if (attribute == null) {
            return "";
        }

//        return JsonUtils.toJsonWithReplace(attribute);
        return JsonUtils.toJson(attribute);
    }

    @Override
    public List<Set<String>> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return new ArrayList<>();
        }
        Type objectType = new TypeToken<List<Set<String>>>() {
        }.getType();
//        return JsonUtils.fromJsonWithReplace(dbData, objectType);
        return JsonUtils.fromJson(dbData, objectType);
    }
}
