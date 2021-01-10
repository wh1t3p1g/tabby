package tabby.db.bean.edge;

import lombok.Getter;
import lombok.Setter;
import tabby.db.bean.ref.ClassReference;
import tabby.db.converter.ClassRef2StringConverter;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Getter
@Setter
//@RelationshipEntity(type="EXTEND")
@Entity
@Table(name = "Extend")
public class Extend {

    @Id
    private String id;

//    @StartNode
    @Convert(converter = ClassRef2StringConverter.class)
    private ClassReference source;

//    @EndNode
    @Convert(converter = ClassRef2StringConverter.class)
    private ClassReference target;

    public static Extend newInstance(ClassReference source, ClassReference target){
        Extend extend = new Extend();
        extend.setId(UUID.randomUUID().toString());
        extend.setSource(source);
        extend.setTarget(target);
        return extend;
    }

    public List<String> toCSV(){
        List<String> csv = new ArrayList<>();
        csv.add(id);
        csv.add(source.getId());
        csv.add(target.getId());
        return csv;
    }
}
