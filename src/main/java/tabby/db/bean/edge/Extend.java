package tabby.db.bean.edge;

import lombok.Getter;
import lombok.Setter;
import tabby.db.bean.ref.ClassReference;
import tabby.db.converter.ClassRef2StringConverter;

import javax.persistence.*;
import java.util.UUID;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Getter
@Setter
@Entity
@Table(name = "Extend")
public class Extend {

    @Id
    private String id;

    @Convert(converter = ClassRef2StringConverter.class)
    private ClassReference source;

    @Convert(converter = ClassRef2StringConverter.class)
    private ClassReference target;

    public static Extend newInstance(ClassReference source, ClassReference target){
        Extend extend = new Extend();
        extend.setId(UUID.randomUUID().toString());
        extend.setSource(source);
        extend.setTarget(target);
        return extend;
    }

}
