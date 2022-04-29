package tabby.dal.neo4j.repository;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;
import tabby.dal.neo4j.entity.MethodEntity;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Repository
public interface MethodRefRepository extends Neo4jRepository<MethodEntity, String> {

    @Query("CALL apoc.periodic.iterate(\"CALL apoc.load.csv('file://\"+$path+\"', " +
            "{header:true, mapping:{ " +
            "IS_SINK: {type:'boolean'}, " +
            "IS_SOURCE: {type:'boolean'}, " +
            "IS_CONTAINS_SOURCE: {type:'boolean'}, " +
            "IS_STATIC: {type:'boolean'}, " +
            "IS_ENDPOINT: {type:'boolean'}, " +
            "HAS_PARAMETERS:{type:'boolean'}, " +
            "IS_INITIALED: { type: 'boolean'}, " +
            "IS_FROM_ABSTRACT_CLASS: { type: 'boolean'}, " +
            "IS_GETTER:{type:'boolean'}, " +
            "IS_SETTER:{type:'boolean'}, " +
            "IS_PUBLIC:{type:'boolean'}, " +
            "IS_ABSTRACT:{type:'boolean'}, " +
            "IS_ACTION_INITIALED:{type:'boolean'}, " +
            "HAS_DEFAULT_CONSTRUCTOR:{type:'boolean'}, " +
            "IS_ACTION_CONTAINS_SWAP:{type:'boolean'}, " +
            "IS_CONTAINS_OUT_OF_MEM_OPTIONS:{type:'boolean'}, " +
            "IS_IGNORE: { type: 'boolean'}, IS_SERIALIZABLE:{type:'boolean'}, " +
            "MODIFIERS:{type:'int'}, PARAMETER_SIZE:{type:'int'}}}) YIELD map AS row RETURN row\", \"MERGE(m:Method {ID:row.ID} ) ON CREATE SET m = row\", {batchSize:5000, iterateList:true, parallel:true}) yield total")
    int loadMethodRefFromCSV(String path);

    @Query("CALL apoc.periodic.iterate(\"CALL apoc.load.csv('file://\"+$path+\"', {header:true}) YIELD map AS row RETURN row\",\"MATCH ( m1:Method {ID:row.SOURCE} ) MATCH ( m2:Method {ID:row.TARGET }) MERGE (m1)-[e:CALL {ID:row.ID, LINE_NUM:row.LINE_NUM, INVOKER_TYPE:row.INVOKER_TYPE, POLLUTED_POSITION:row.POLLUTED_POSITION, REAL_CALL_TYPE:row.REAL_CALL_TYPE }]->(m2)\", {batchSize:5000, iterateList:true, parallel:false}) yield total")
    int loadCallEdgeFromCSV(String path);

    @Query("CALL apoc.periodic.iterate(\"CALL apoc.load.csv('file://\"+$path+\"', {header:true}) YIELD map AS row RETURN row\",\"MATCH ( m1:Method {ID:row.SOURCE} ) MATCH ( m2:Method {ID:row.TARGET }) MERGE (m1)-[e:ALIAS {ID:row.ID}]-(m2)\", {batchSize:1000, iterateList:true, parallel:false}) yield total")
    int loadAliasEdgeFromCSV(String path);
}