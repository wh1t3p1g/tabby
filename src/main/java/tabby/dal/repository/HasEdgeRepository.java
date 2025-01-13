package tabby.dal.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tabby.common.bean.edge.Has;

/**
 * @author wh1t3P1g
 * @since 2021/1/8
 */
@Repository
public interface HasEdgeRepository extends CrudRepository<Has, String> {

    @Query(value = "CALL CSVWRITE(:path, 'SELECT * FROM HAS')", nativeQuery = true)
    void save2Csv(@Param("path") String path);

    @Transactional
    @Modifying
    @Query(value = "INSERT IGNORE INTO has (`id`, `class_id`, `method_ref`) " +
            "values (:id, :classId, :methodRef)", nativeQuery = true)
    void save(@Param("id") String id,
              @Param("classId") String classId,
              @Param("methodRef") String methodRef);
}
