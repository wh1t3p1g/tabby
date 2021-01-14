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
public interface ClassRefRepository extends Neo4jRepository<String, UUID> {

    @Query("CALL apoc.periodic.iterate(\"CALL apoc.load.csv('file://\"+$path+\"', {header:true, mapping:{ IS_INTERFACE: {type:'boolean'}, HAS_SUPER_CLASS: {type:'boolean'}, HAS_INTERFACES: {type:'boolean'}, IS_INITIALED: {type:'boolean'}, IS_SERIALIZABLE:{type:'boolean'}}}) YIELD map AS row RETURN row\",\"MERGE (c:Class {NAME:row.NAME}) ON CREATE SET c = row\", {batchSize:5000, iterateList:true, parallel:true})")
    void loadClassRefFromCSV(String path);

    @Query("CALL apoc.periodic.iterate(\"CALL apoc.load.csv('file://\"+$path+\"', {header:true}) YIELD map AS row RETURN row\",\"MATCH( c1:Class {ID:row.SOURCE} ) MATCH ( c2:Class { ID:row.TARGET } ) MERGE (c1) -[e:EXTENDS { ID:row.ID }] -> (c2)\", {batchSize:1000, iterateList:true, parallel:false})")
    void loadExtendEdgeFromCSV(String path);

    @Query("CALL apoc.periodic.iterate(\"CALL apoc.load.csv('file://\"+$path+\"', {header:true}) YIELD map AS row RETURN row\",\"MATCH( c1:Class {ID:row.SOURCE} ) MATCH ( c2:Class { ID:row.TARGET } ) MERGE (c1) -[e:INTERFACE { ID:row.ID }] -> (c2)\", {batchSize:1000, iterateList:true, parallel:false})")
    void loadInterfacesEdgeFromCSV(String path);

    @Query("CALL apoc.periodic.iterate(\"CALL apoc.load.csv('file://\"+$path+\"', {header:true}) YIELD map AS row RETURN row\",\"MATCH(c:Class{ID:row.CLASS_REF}) MATCH(m:Method { ID:row.METHOD_REF }) MERGE (c) -[e:HAS { ID:row.ID }]-> (m)\", {batchSize:1000, iterateList:true, parallel:false})")
    void loadHasEdgeFromCSV(String path);

    @Query("CALL apoc.periodic.iterate(\"match (n) return n\",\"detach delete n\", {batchSize:10000, iterateList:true, parallel:false})")
    void clearAll();

}
