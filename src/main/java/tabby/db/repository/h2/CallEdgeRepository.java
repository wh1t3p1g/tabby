package tabby.db.repository.h2;

import org.springframework.data.repository.CrudRepository;
import tabby.db.bean.edge.Call;

/**
 * @author wh1t3P1g
 * @since 2021/1/8
 */
public interface CallEdgeRepository extends CrudRepository<Call, String> {
}
