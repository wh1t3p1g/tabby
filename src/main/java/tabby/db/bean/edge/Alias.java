package tabby.db.bean.edge;

import lombok.Getter;
import lombok.Setter;
import tabby.db.bean.ref.MethodReference;
import tabby.db.converter.MethodRef2StringConverter;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author wh1t3P1g
 * @since 2020/11/22
 */
@Getter
@Setter
//@RelationshipEntity(type="ALIAS")
@Entity
@Table(name = "Alias")
public class Alias {

    @Id
    private String id;

//    @StartNode
    @Convert(converter = MethodRef2StringConverter.class)
    private MethodReference source;

//    @EndNode
    @Convert(converter = MethodRef2StringConverter.class)
    private MethodReference target;

    public static Alias newInstance(MethodReference source, MethodReference target){
        Alias alias = new Alias();
        alias.setId(UUID.randomUUID().toString());
        alias.setSource(source);
        alias.setTarget(target);
        return alias;
    }

    public List<String> toCSV(){
        List<String> csv = new ArrayList<>();
        csv.add(id);
        csv.add(source.getId());
        csv.add(target.getId());
        return csv;
    }
}
