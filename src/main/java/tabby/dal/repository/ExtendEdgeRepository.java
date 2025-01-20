package tabby.dal.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tabby.common.bean.edge.Extend;

/**
 * @author wh1t3P1g
 * @since 2021/1/8
 */
@Repository
public interface ExtendEdgeRepository extends CrudRepository<Extend, String> {

    @Query(value = "CALL CSVWRITE(:path, 'SELECT * FROM EXTEND')", nativeQuery = true)
    void save2Csv(@Param("path") String path);

    @Transactional
    @Modifying
    @Query(value = "INSERT IGNORE INTO extend (`id`, `source`, `target`) " +
            "values (:id, :source, :target)", nativeQuery = true)
    void save(@Param("id") String id,
              @Param("source") String source,
              @Param("target") String target);
}
