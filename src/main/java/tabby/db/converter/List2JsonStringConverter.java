package tabby.db.converter;

import tabby.config.GlobalConfiguration;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.util.ArrayList;
import java.util.List;

/**
 * @author wh1t3P1g
 * @since 2021/1/8
 */
@Converter
public class List2JsonStringConverter implements AttributeConverter<List<String>,String> {
    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        if(attribute == null){
            return "";
        }

        return GlobalConfiguration.GSON.toJson(attribute);
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if(dbData == null || "".equals(dbData)){
            return new ArrayList<>();
        }

        return (List<String>) GlobalConfiguration.GSON.fromJson(dbData, List.class);
    }
}
