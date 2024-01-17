package tabby.common.bean.edge;

import com.google.common.base.Objects;
import lombok.Getter;
import lombok.Setter;
import tabby.common.bean.ref.ClassReference;
import tabby.common.utils.HashingUtils;

import jakarta.persistence.*;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Getter
@Setter
@Entity
@Table(name = "Extend", indexes = { @Index(columnList = "id")})
public class Extend implements Edge {

    @Id
    private String id;

    private String source;

    private String target;

    public static Extend newInstance(ClassReference source, ClassReference target){
        Extend extend = new Extend();
        extend.setSource(source.getId());
        extend.setTarget(target.getId());
        extend.setId(HashingUtils.hashString(extend.getSource()+extend.getTarget()));
        return extend;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Extend extend = (Extend) o;
        return Objects.equal(source, extend.source) && Objects.equal(target, extend.target);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(source, target);
    }
}
