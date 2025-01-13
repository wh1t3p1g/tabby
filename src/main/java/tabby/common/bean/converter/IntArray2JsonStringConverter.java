package tabby.common.bean.converter;

import com.google.gson.reflect.TypeToken;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tabby.common.utils.JsonUtils;

import java.lang.reflect.Type;

/**
 * @author wh1t3P1g
 * @since 2021/1/8
 */
@Converter
public class IntArray2JsonStringConverter implements AttributeConverter<int[][], String> {

    @Override
    public String convertToDatabaseColumn(int[][] attribute) {
        if (attribute == null) {
            return "";
        }

        return JsonUtils.toJson(attribute);
    }

    @Override
    public int[][] convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return new int[0][];
        }
        Type objectType = new TypeToken<int[][]>() {
        }.getType();
        return JsonUtils.fromJson(dbData, objectType);
    }
}
