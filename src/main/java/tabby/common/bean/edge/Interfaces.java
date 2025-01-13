package tabby.common.bean.edge;

import com.google.common.base.Objects;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import tabby.common.bean.ref.ClassReference;
import tabby.common.utils.HashingUtils;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Getter
@Setter
@Entity
@Table(name = "Interfaces", indexes = {@Index(columnList = "id")})
public class Interfaces implements Edge {

    @Id
    private String id;

    private String source;
    private String target;

    public static Interfaces newInstance(ClassReference source, ClassReference target) {
        Interfaces interfaces = new Interfaces();
        interfaces.setSource(source.getId());
        interfaces.setTarget(target.getId());
        interfaces.setId(HashingUtils.hashString(interfaces.getSource() + interfaces.getTarget()));
        return interfaces;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Interfaces that = (Interfaces) o;
        return Objects.equal(source, that.source) && Objects.equal(target, that.target);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(source, target);
    }
}
