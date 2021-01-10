package tabby.db.repository.h2;

import org.springframework.data.repository.CrudRepository;
import tabby.db.bean.edge.Alias;

/**
 * @author wh1t3P1g
 * @since 2021/1/8
 */
public interface AliasEdgeRepository extends CrudRepository<Alias, String> {

//    @Query("CALL CSVWRITE('?1', 'SELECT * FROM ALIAS');")
//    void save2CSV(String path);

}
