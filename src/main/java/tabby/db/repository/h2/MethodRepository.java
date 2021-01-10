package tabby.db.repository.h2;

import org.springframework.data.repository.CrudRepository;
import tabby.db.bean.ref.MethodReference;

/**
 * @author wh1t3P1g
 * @since 2021/1/8
 */
public interface MethodRepository extends CrudRepository<MethodReference, String> {

    MethodReference findMethodReferenceBySignature(String signature);
}
