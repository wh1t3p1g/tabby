package tabby.db.bean.edge;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import tabby.db.bean.ref.ClassReference;
import tabby.db.bean.ref.MethodReference;
import tabby.db.converter.ClassRef2StringConverter;
import tabby.db.converter.MethodRef2StringConverter;

import javax.persistence.*;
import java.util.UUID;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Getter
@Setter
@Entity
@Table(name = "Has")
public class Has {

    @Id
    private String id;

    @Convert(converter = ClassRef2StringConverter.class)
    private ClassReference classRef;

    @Convert(converter = MethodRef2StringConverter.class)
    private MethodReference methodRef;

    public static Has newInstance(ClassReference classRef, MethodReference methodRef){
        Has has = new Has();
        has.setId(UUID.randomUUID().toString());
        has.setClassRef(classRef);
        has.setMethodRef(methodRef);
        return has;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        Has has = (Has) o;

        return new EqualsBuilder().append(classRef.getName(), has.classRef.getName()).append(methodRef.getSignature(), has.methodRef.getSignature()).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(classRef.getName()).append(methodRef.getSignature()).toHashCode();
    }
}
