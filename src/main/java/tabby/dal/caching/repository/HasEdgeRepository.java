package tabby.dal.caching.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tabby.dal.caching.bean.edge.Has;

/**
 * @author wh1t3P1g
 * @since 2021/1/8
 */
@Repository
public interface HasEdgeRepository extends CrudRepository<Has, String> {

    @Query(value = "CALL CSVWRITE(:path, 'SELECT * FROM HAS')", nativeQuery=true)
    void save2Csv(@Param("path") String path);

    @Query(value = "select count(*) from HAS", nativeQuery=true)
    int countAll();
}
