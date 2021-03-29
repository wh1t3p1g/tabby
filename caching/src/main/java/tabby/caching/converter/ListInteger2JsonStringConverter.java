package tabby.caching.converter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * @author wh1t3P1g
 * @since 2021/1/8
 */
@Converter
public class ListInteger2JsonStringConverter implements AttributeConverter<List<Integer>,String> {

    private static Gson gson = new Gson();

    @Override
    public String convertToDatabaseColumn(List<Integer> attribute) {
        if(attribute == null){
            return "";
        }

        return gson.toJson(attribute);
    }

    @Override
    public List<Integer> convertToEntityAttribute(String dbData) {
        if(dbData == null || "".equals(dbData)){
            return new ArrayList<>();
        }
        Type objectType = new TypeToken<List<Integer>>(){}.getType();
        return gson.fromJson(dbData, objectType);
    }
}
