package tabby.db.repository.h2;

import org.springframework.data.repository.CrudRepository;
import tabby.db.bean.edge.Has;

/**
 * @author wh1t3P1g
 * @since 2021/1/8
 */
public interface HasEdgeRepository extends CrudRepository<Has, String> {
}
