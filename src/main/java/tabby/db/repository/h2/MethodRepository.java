package tabby.db.repository.h2;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import tabby.db.bean.ref.MethodReference;

import java.util.List;

/**
 * @author wh1t3P1g
 * @since 2021/1/8
 */
public interface MethodRepository extends CrudRepository<MethodReference, String> {

    @Query(value = "select * from METHODS where SIGNATURE = :signature limit 1", nativeQuery = true)
    MethodReference findMethodReferenceBySignature(String signature);

    @Query(value = "CALL CSVWRITE(:path, 'SELECT * FROM METHODS')", nativeQuery=true)
    void save2Csv(@Param("path") String path);

    @Query(value = "select * from METHODS where CLASSNAME like 'sun.%' or CLASSNAME like 'java.%'", nativeQuery = true)
    List<MethodReference> findAllNecessaryMethodRefs();
}
