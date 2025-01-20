package tabby.common.bean.edge;

import com.google.common.base.Objects;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import tabby.common.bean.ref.MethodReference;
import tabby.common.utils.HashingUtils;

/**
 * @author wh1t3P1g
 * @since 2020/11/22
 */
@Getter
@Setter
@Entity
@Table(name = "Alias", indexes = {@Index(columnList = "id")})
public class Alias implements Edge {

    @Id
    private String id;

    private String source;

    private String target;

    public static Alias newInstance(MethodReference source, MethodReference target) {
        Alias alias = new Alias();
        alias.setSource(source.getId());
        alias.setTarget(target.getId());
        alias.setId(HashingUtils.hashString(alias.getSource() + alias.getTarget()));
        return alias;
    }

//    public Set<MethodReference> getAllTargets(){
//        Set<MethodReference> targets = new HashSet<>();
//        if(target != null){
//            targets.add(target);
//            for(Alias alias: target.getChildAliasEdges()){
//                targets.addAll(alias.getAllTargets());
//            }
//        }
//        return targets;
//    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Alias alias = (Alias) o;
        return Objects.equal(source, alias.source) && Objects.equal(target, alias.target);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(source, target);
    }
}
