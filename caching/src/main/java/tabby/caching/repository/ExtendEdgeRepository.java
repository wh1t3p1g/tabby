package tabby.caching.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import tabby.caching.bean.edge.Extend;

/**
 * @author wh1t3P1g
 * @since 2021/1/8
 */
public interface ExtendEdgeRepository extends CrudRepository<Extend, String> {

    @Query(value = "CALL CSVWRITE(:path, 'SELECT * FROM EXTEND')", nativeQuery=true)
    void save2Csv(@Param("path") String path);

    @Query(value = "select count(*) from EXTEND", nativeQuery=true)
    int countAll();
}
