package tabby.db.repository.neo4j;

import org.springframework.data.neo4j.annotation.Query;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Repository
public interface MethodRefRepository extends Neo4jRepository<String, UUID> {

//    MethodReference findMethodReferenceBySignature(String signature);
//
//    @Query("match (m:Method {signature: $signature}) -[:ALIAS*]- (m1:Method) return m1.signature")
//    List<String> findAllAliasMethods(String signature);
//
//    @Query("match (m:Method {signature: $signature}) <-[:CALL {invokerType: $type}]- (m1:Method) return m1.signature")
//    List<String> findAllCallMethods(String signature, String type);
//
//    @Query("match (m:Method {signature:$sig}) <-[:CALL]- (m1:Method) return m1.signature")
//    List<String> findAllCall(String sig);
//
//    @Query("match (m:Method {isSink:true}) return m.signature")
//    List<String> findAllSinks();
//
//    @Query("match (m:Method) -[:CALL]-> (target:Method {signature: $signature}) return m.signature")
//    List<String> findAllByInvokedMethodSignature(String signature);
//
//    @Query("match (m:Method) where size((m)-[:CALL]->()) = $outing  return m.signature skip $skip limit $limit")
//    List<String> findAllMethodRefByOutingCount(int outing, int skip, int limit);
//
//    @Query("match (m:Method) where size((m)-[:CALL]->()) = $outing  return count(m)")
//    int countAllMethodRefByOutingCount(@Param("outing") int outing);

    @Query("CALL apoc.periodic.iterate(\"CALL apoc.load.csv('file://\"+$path+\"', {header:true, mapping:{ IS_STATIC: {type:'boolean'}, HAS_PARAMETERS:{type:'boolean'}, IS_SINK: { type: 'boolean'}, IS_SOURCE: { type: 'boolean'}, IS_POLLUTED: { type: 'boolean'}, IS_SERIALIZABLE:{type:'boolean'} }}) YIELD map AS row RETURN row\", \"MERGE(m:Method {ID:row.ID} ) ON CREATE SET m = row\", {batchSize:5000, iterateList:true, parallel:true})")
    void loadMethodRefFromCSV(String path);

    @Query("CALL apoc.periodic.iterate(\"CALL apoc.load.csv('file://\"+$path+\"', {header:true, mapping:{ IS_POLLUTED: {type: 'boolean'} }}) YIELD map AS row RETURN row\",\"MATCH ( m1:Method {ID:row.SOURCE} ) MATCH ( m2:Method {ID:row.TARGET }) MERGE (m1)-[e:CALL {ID:row.ID, LINE_NUM:row.LINE_NUM, IS_POLLUTED:row.IS_POLLUTED, POLLUTED_POSITION:row.POLLUTED_POSITION, REAL_CALL_TYPE:row.REAL_CALL_TYPE, INVOKER_TYPE: row.INVOKER_TYPE }]->(m2)\", {batchSize:5000, iterateList:true, parallel:false})")
    void loadCallEdgeFromCSV(String path);

    @Query("CALL apoc.periodic.iterate(\"CALL apoc.load.csv('file://\"+$path+\"', {header:true}) YIELD map AS row RETURN row\",\"MATCH ( m1:Method {ID:row.SOURCE} ) MATCH ( m2:Method {ID:row.TARGET }) MERGE (m1)-[e:ALIAS {ID:row.ID}]-(m2)\", {batchSize:1000, iterateList:true, parallel:false})")
    void loadAliasEdgeFromCSV(String path);
}