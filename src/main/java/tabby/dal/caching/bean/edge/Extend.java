package tabby.dal.caching.bean.edge;

import lombok.Getter;
import lombok.Setter;
import tabby.dal.caching.bean.ref.ClassReference;
import tabby.dal.caching.converter.ClassRef2StringConverter;

import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
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
