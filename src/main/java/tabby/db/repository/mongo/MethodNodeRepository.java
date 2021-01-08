package tabby.db.repository.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import tabby.db.bean.ref.MethodReference;

/**
 * @author wh1t3P1g
 * @since 2021/1/6
 */
@Repository
public interface MethodNodeRepository extends MongoRepository<MethodReference, String> {

    MethodReference findMethodNodeBySignature(String signature);
}
