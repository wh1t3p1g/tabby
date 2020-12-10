package tabby.neo4j.repository;

import org.springframework.data.neo4j.annotation.Query;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;
import tabby.neo4j.bean.ref.ClassReference;

import java.util.UUID;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Repository
public interface ClassRefRepository extends Neo4jRepository<ClassReference, UUID> {

    ClassReference findClassReferenceByName(String name);

    @Query("CALL apoc.periodic.iterate(\"CALL apoc.load.csv('file://\"+$path+\"', {header:true, mapping:{ hasSuperClass: {type:'boolean'}, hasInterfaces: {type:'boolean'}, isInterface: {type:'boolean'}, interfaces: {array:true, arraySep:'|'}, fields: {array:true, arraySep:'|'}}}) YIELD map AS row RETURN row\",\"MERGE (c:Class {uuid:row.uuid}) ON CREATE SET c = row\", {batchSize:5000, iterateList:true, parallel:true})")
    void loadClassRefFromCSV(String path);

    @Query("CALL apoc.periodic.iterate(\"CALL apoc.load.csv('file://\"+$path+\"', {header:true}) YIELD map AS row RETURN row\",\"MATCH( c1:Class {uuid:row.source} ) MATCH ( c2:Class { uuid:row.target } ) MERGE (c1) -[e:EXTENDS { uuid:row.uuid }] -> (c2)\", {batchSize:1000, iterateList:true, parallel:false})")
    void loadExtendEdgeFromCSV(String path);

    @Query("CALL apoc.periodic.iterate(\"CALL apoc.load.csv('file://\"+$path+\"', {header:true}) YIELD map AS row RETURN row\",\"MATCH( c1:Class {uuid:row.source} ) MATCH ( c2:Class { uuid:row.target } ) MERGE (c1) -[e:INTERFACE { uuid:row.uuid }] -> (c2)\", {batchSize:1000, iterateList:true, parallel:false})")
    void loadInterfacesEdgeFromCSV(String path);

    @Query("CALL apoc.periodic.iterate(\"CALL apoc.load.csv('file://\"+$path+\"', {header:true}) YIELD map AS row RETURN row\",\"MATCH(c:Class{uuid:row.classRef}) MATCH(m:Method { uuid:row.MethodRef }) MERGE (c) -[e:HAS { uuid:row.uuid }]-> (m)\", {batchSize:1000, iterateList:true, parallel:false})")
    void loadHasEdgeFromCSV(String path);

}
