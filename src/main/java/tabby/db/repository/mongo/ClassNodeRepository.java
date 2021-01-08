package tabby.db.repository.mongo;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import tabby.db.bean.ref.ClassReference;

/**
 * @author wh1t3P1g
 * @since 2021/1/6
 */
@Repository
public interface ClassNodeRepository extends MongoRepository<ClassReference, String> {


    ClassReference findClassNodeByName(String name);

    boolean existsClassNodeByName(String name);
}
