package tabby.db.repository.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import tabby.db.bean.node.HasNode;

/**
 * @author wh1t3P1g
 * @since 2021/1/6
 */
@Repository
public interface HasNodeRepository extends MongoRepository<HasNode, String> {

}
