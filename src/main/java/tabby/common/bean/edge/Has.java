package tabby.common.bean.edge;

import com.google.common.base.Objects;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import tabby.common.bean.converter.MethodRef2StringConverter;
import tabby.common.bean.ref.ClassReference;
import tabby.common.bean.ref.MethodReference;
import tabby.common.utils.HashingUtils;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Getter
@Setter
@Entity
@Table(name = "Has", indexes = {@Index(columnList = "id")})
public class Has implements Edge {

    @Id
    private String id;

    private String classId;

    @Convert(converter = MethodRef2StringConverter.class)
    private MethodReference methodRef;

    public static Has newInstance(ClassReference classRef, MethodReference methodRef) {
        Has has = new Has();
        has.setClassId(classRef.getId());
        has.setMethodRef(methodRef);
        has.setId(HashingUtils.hashString(has.getClassId() + has.getMethodRef().getSignature()));
        return has;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Has has = (Has) o;
        return Objects.equal(classId, has.classId) && Objects.equal(methodRef.getSignature(), has.methodRef.getSignature());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(classId, methodRef.getSignature());
    }
}
