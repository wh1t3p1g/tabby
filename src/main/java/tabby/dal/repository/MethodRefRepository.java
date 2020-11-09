package tabby.dal.repository;

import org.springframework.data.neo4j.annotation.Query;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;
import tabby.dal.bean.ref.MethodReference;

import java.util.UUID;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Repository
public interface MethodRefRepository extends Neo4jRepository<MethodReference, UUID> {

    MethodReference findMethodReferenceBySignature(String signature);

    @Query("CALL apoc.periodic.iterate(\"CALL apoc.load.csv('file://\"+$path+\"', {header:true, mapping:{ isStatic: {type:'boolean'}, hasParameters:{type:'boolean'}, parameters:{array:true, arraySep:'|'}}}) YIELD map AS row RETURN row\", \"MERGE(m:Method {uuid:row.uuid} ) ON CREATE SET m = row\", {batchSize:1000, iterateList:true, parallel:true})")
    void loadMethodRefFromCSV(String path);
}
