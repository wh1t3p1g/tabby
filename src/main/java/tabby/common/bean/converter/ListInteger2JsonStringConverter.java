package tabby.common.bean.converter;

import com.google.gson.reflect.TypeToken;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tabby.common.utils.JsonUtils;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * @author wh1t3P1g
 * @since 2021/1/8
 */
@Converter
public class ListInteger2JsonStringConverter implements AttributeConverter<List<Integer>,String> {

    @Override
    public String convertToDatabaseColumn(List<Integer> attribute) {
        if(attribute == null){
            return "";
        }

        return JsonUtils.toJson(attribute);
    }

    @Override
    public List<Integer> convertToEntityAttribute(String dbData) {
        if(dbData == null || "".equals(dbData)){
            return new ArrayList<>();
        }
        Type objectType = new TypeToken<List<Integer>>(){}.getType();
        return JsonUtils.fromJson(dbData, objectType);
    }
}
