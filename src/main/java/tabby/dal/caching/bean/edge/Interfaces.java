package tabby.dal.caching.bean.edge;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
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
@Table(name = "Interfaces")
public class Interfaces {

    @Id
    private String id;

    @Convert(converter = ClassRef2StringConverter.class)
    private ClassReference source;

    @Convert(converter = ClassRef2StringConverter.class)
    private ClassReference target;

    public static Interfaces newInstance(ClassReference source, ClassReference target){
        Interfaces interfaces = new Interfaces();
        interfaces.setId(UUID.randomUUID().toString());
        interfaces.setSource(source);
        interfaces.setTarget(target);
        return interfaces;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        Interfaces that = (Interfaces) o;

        return new EqualsBuilder().append(source.getName(), that.source.getName()).append(target.getName(), that.target.getName()).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(source.getName()).append(target.getName()).toHashCode();
    }
}
