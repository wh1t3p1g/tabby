package tabby.dal.neo4j.repository;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;
import tabby.dal.neo4j.entity.ClassEntity;


/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Repository
public interface ClassRefRepository extends Neo4jRepository<ClassEntity, String> {

    @Query("CALL apoc.periodic.iterate(\"CALL apoc.load.csv('file://\"+$path+\"', " +
            "{header:true, ignore: ['CHILD_CLASSNAMES','IS_PHANTOM','IS_INITIALED', 'FIELDS'], mapping:{ " +
            "IS_INTERFACE: {type:'boolean'}, " +
            "IS_ABSTRACT: {type:'boolean'}, " +
            "HAS_SUPER_CLASS: {type:'boolean'}, " +
            "HAS_INTERFACES: {type:'boolean'}, " +
            "IS_STRUTS_ACTION: {type:'boolean'}, " +
            "HAS_DEFAULT_CONSTRUCTOR: {type:'boolean'}, " +
            "IS_SERIALIZABLE:{type:'boolean'}}}) YIELD map AS row RETURN row\",\"MERGE (c:Class {NAME:row.NAME}) ON CREATE SET c = row\", {batchSize:5000, iterateList:true, parallel:true}) yield total")
    int loadClassRefFromCSV(String path);

    @Query("CALL apoc.periodic.iterate(\"CALL apoc.load.csv('file://\"+$path+\"', {header:true}) YIELD map AS row RETURN row\",\"MATCH( c1:Class {NAME:row.SOURCE} ) MATCH ( c2:Class { NAME:row.TARGET } ) MERGE (c1) -[e:EXTENDS { ID:row.ID }] -> (c2)\", {batchSize:1000, iterateList:true, parallel:false}) yield total")
    int loadExtendEdgeFromCSV(String path);

    @Query("CALL apoc.periodic.iterate(\"CALL apoc.load.csv('file://\"+$path+\"', {header:true}) YIELD map AS row RETURN row\",\"MATCH( c1:Class {NAME:row.SOURCE} ) MATCH ( c2:Class { NAME:row.TARGET } ) MERGE (c1) -[e:INTERFACE { ID:row.ID }] -> (c2)\", {batchSize:1000, iterateList:true, parallel:false}) yield total")
    int loadInterfacesEdgeFromCSV(String path);

    @Query("CALL apoc.periodic.iterate(\"CALL apoc.load.csv('file://\"+$path+\"', {header:true}) YIELD map AS row RETURN row\",\"MATCH(c:Class{NAME:row.CLASS_REF}) MATCH(m:Method { ID:row.METHOD_REF }) MERGE (c) -[e:HAS { ID:row.ID }]-> (m)\", {batchSize:1000, iterateList:true, parallel:false}) yield total")
    int loadHasEdgeFromCSV(String path);

    @Query("CALL apoc.periodic.iterate(\"match (n) return n\",\"detach delete n\", {batchSize:10000, iterateList:true, parallel:false}) yield total")
    int clearAll();

}
