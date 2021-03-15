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

    @Query("CALL apoc.periodic.iterate(\"CALL apoc.load.csv('file://\"+$path+\"', {header:true, mapping:{ IS_SINK: {type:'boolean'}, IS_SOURCE: {type:'boolean'}, IS_STATIC: {type:'boolean'}, IS_POLLUTED: {type:'boolean'}, HAS_PARAMETERS:{type:'boolean'}, IS_INITIALED: { type: 'boolean'}, ACTION_INITIALED: { type: 'boolean'}, IS_IGNORE: { type: 'boolean'}, IS_SERIALIZABLE:{type:'boolean'}, MODIFIERS:{type:'int'}, PARAMETER_SIZE:{type:'int'}}}) YIELD map AS row RETURN row\", \"MERGE(m:Method {ID:row.ID} ) ON CREATE SET m = row\", {batchSize:5000, iterateList:true, parallel:true})")
    void loadMethodRefFromCSV(String path);

    @Query("CALL apoc.periodic.iterate(\"CALL apoc.load.csv('file://\"+$path+\"', {header:true}) YIELD map AS row RETURN row\",\"MATCH ( m1:Method {ID:row.SOURCE} ) MATCH ( m2:Method {ID:row.TARGET }) MERGE (m1)-[e:CALL {ID:row.ID, LINE_NUM:row.LINE_NUM, INVOKER_TYPE:row.INVOKER_TYPE, POLLUTED_POSITION:row.POLLUTED_POSITION, REAL_CALL_TYPE:row.REAL_CALL_TYPE }]->(m2)\", {batchSize:5000, iterateList:true, parallel:false})")
    void loadCallEdgeFromCSV(String path);

    @Query("CALL apoc.periodic.iterate(\"CALL apoc.load.csv('file://\"+$path+\"', {header:true}) YIELD map AS row RETURN row\",\"MATCH ( m1:Method {ID:row.SOURCE} ) MATCH ( m2:Method {ID:row.TARGET }) MERGE (m1)-[e:ALIAS {ID:row.ID}]-(m2)\", {batchSize:1000, iterateList:true, parallel:false})")
    void loadAliasEdgeFromCSV(String path);
}