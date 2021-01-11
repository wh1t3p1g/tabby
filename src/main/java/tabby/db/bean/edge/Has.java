package tabby.db.bean.edge;

import lombok.Getter;
import lombok.Setter;
import tabby.db.bean.ref.ClassReference;
import tabby.db.bean.ref.MethodReference;
import tabby.db.converter.ClassRef2StringConverter;
import tabby.db.converter.MethodRef2StringConverter;

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
//@RelationshipEntity(type="HAS")
@Entity
@Table(name = "Has")
public class Has {

    @Id
    private String id;

//    @StartNode
    @Convert(converter = ClassRef2StringConverter.class)
    private ClassReference classRef;

//    @EndNode
    @Convert(converter = MethodRef2StringConverter.class)
    private MethodReference methodRef;

    public static Has newInstance(ClassReference classRef, MethodReference methodRef){
        Has has = new Has();
        has.setId(UUID.randomUUID().toString());
        has.setClassRef(classRef);
        has.setMethodRef(methodRef);
        return has;
    }

}
