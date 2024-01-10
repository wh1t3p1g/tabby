package tabby.dal.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tabby.common.bean.edge.Interfaces;

/**
 * @author wh1t3P1g
 * @since 2021/1/8
 */
@Repository
public interface InterfacesEdgeRepository extends CrudRepository<Interfaces, String> {

    @Query(value = "CALL CSVWRITE(:path, 'SELECT * FROM INTERFACES')", nativeQuery=true)
    void save2Csv(@Param("path") String path);

    @Query(value = "select count(*) from INTERFACES", nativeQuery=true)
    int countAll();
}
