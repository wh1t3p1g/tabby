package tabby.dal.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tabby.common.bean.edge.Call;

/**
 * @author wh1t3P1g
 * @since 2021/1/8
 */
@Repository
public interface CallEdgeRepository extends CrudRepository<Call, String> {
    @Query(value = "CALL CSVWRITE(:path, 'SELECT * FROM CALL')", nativeQuery = true)
    void save2Csv(@Param("path") String path);

    @Transactional
    @Modifying
    @Query(value =
            "INSERT IGNORE INTO call " +
                    "(`invoker_type`, `line_num`, " +
                    "`is_caller_this_field_obj`, `polluted_position`, " +
                    "`types`, `constants`, " +
                    "`source`, `target`, `id`) " +
                    "values (:invokerType, :lineNum, " +
                    ":isCallerThisFieldObj, :pollutedPosition, " +
                    ":types, :constants, " +
                    ":source, :target, :id)", nativeQuery = true)
    void save(@Param("id") String id,
              @Param("source") String source,
              @Param("target") String target,
              @Param("lineNum") int lineNum,
              @Param("invokerType") String invokerType,
              @Param("isCallerThisFieldObj") boolean isCallerThisFieldObj,
              @Param("pollutedPosition") String pollutedPosition,
              @Param("types") String types,
              @Param("constants") String constants);

}
