package tabby.db.repository.h2;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import tabby.db.bean.edge.Alias;

/**
 * @author wh1t3P1g
 * @since 2021/1/8
 */
public interface AliasEdgeRepository extends CrudRepository<Alias, String> {

    @Query(value = "CALL CSVWRITE(:path, 'SELECT * FROM ALIAS')", nativeQuery=true)
    void save2Csv(@Param("path") String path);

}
