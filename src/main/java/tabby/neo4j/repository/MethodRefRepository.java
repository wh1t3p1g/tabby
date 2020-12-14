package tabby.neo4j.repository;

import org.springframework.data.neo4j.annotation.Query;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tabby.neo4j.bean.ref.MethodReference;

import java.util.List;
import java.util.UUID;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Repository
public interface MethodRefRepository extends Neo4jRepository<MethodReference, UUID> {

    MethodReference findMethodReferenceBySignature(String signature);

    @Query("match (m:Method {signature: $signature}) -[:ALIAS*]- (m1:Method) return m1.signature")
    List<String> findAllAliasMethods(String signature);

    @Query("match (m:Method {signature:$sig}) <-[:CALL]- (m1:Method) return m1.signature")
    List<String> findAllCall(String sig);

    @Query("match (m:Method {isSink:true}) return m.signature")
    List<String> findAllSinks();

    @Query("match (m:Method) -[:CALL]-> (target:Method {signature: $signature}) return m.signature")
    List<String> findAllByInvokedMethodSignature(String signature);

    @Query("match (m:Method) where size((m)-[:CALL]->()) = $outing  return m.signature skip $skip limit $limit")
    List<String> findAllMethodRefByOutingCount(int outing, int skip, int limit);

    @Query("match (m:Method) where size((m)-[:CALL]->()) = $outing  return count(m)")
    int countAllMethodRefByOutingCount(@Param("outing") int outing);

    @Query("CALL apoc.periodic.iterate(\"CALL apoc.load.csv('file://\"+$path+\"', {header:true, mapping:{ isStatic: {type:'boolean'}, hasParameters:{type:'boolean'}, isSink: { type: 'boolean'}, parameters:{array:true, arraySep:'|'}}}) YIELD map AS row RETURN row\", \"MERGE(m:Method {uuid:row.uuid} ) ON CREATE SET m = row\", {batchSize:5000, iterateList:true, parallel:true})")
    void loadMethodRefFromCSV(String path);

    @Query("CALL apoc.periodic.iterate(\"CALL apoc.load.csv('file://\"+$path+\"', {header:true}) YIELD map AS row RETURN row\",\"MATCH ( m1:Method {uuid:row.source} ) MATCH ( m2:Method {uuid:row.target }) MERGE (m1)-[e:CALL {uuid:row.uuid, lineNum:row.lineNum, realCallType:row.realCallType, invokerType: row.invokerType }]->(m2)\", {batchSize:5000, iterateList:true, parallel:false})")
    void loadCallEdgeFromCSV(String path);

    @Query("CALL apoc.periodic.iterate(\"CALL apoc.load.csv('file://\"+$path+\"', {header:true}) YIELD map AS row RETURN row\",\"MATCH ( m1:Method {uuid:row.source} ) MATCH ( m2:Method {uuid:row.target }) MERGE (m1)-[e:ALIAS {uuid:row.uuid}]-(m2)\", {batchSize:1000, iterateList:true, parallel:false})")
    void loadAliasEdgeFromCSV(String path);
}