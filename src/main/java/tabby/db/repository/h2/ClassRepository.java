package tabby.db.repository.h2;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import tabby.db.bean.ref.ClassReference;

import java.util.List;

/**
 * @author wh1t3P1g
 * @since 2021/1/8
 */
public interface ClassRepository extends CrudRepository<ClassReference, String> {

    @Query(value = "select * from CLASSES where NAME = :name limit 1", nativeQuery = true)
    ClassReference findClassReferenceByName(String name);

    @Query(value="select count(*) from CLASSES", nativeQuery=true)
    int countAll();

    @Query(value = "CALL CSVWRITE(:path, 'SELECT * FROM CLASSES')", nativeQuery=true)
    void save2Csv(@Param("path") String path);

    @Query(value = "select * from CLASSES where NAME like 'sun.%' or NAME like 'java.%'", nativeQuery = true)
    List<ClassReference> findAllNecessaryClassRefs();

}
