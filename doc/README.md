把插件 neo配置好后，用他自带的浏览器执行
```
CREATE CONSTRAINT ON (c:Class) ASSERT c.ID IS UNIQUE;
CREATE CONSTRAINT ON (m:Method) ASSERT m.ID IS UNIQUE;
CREATE INDEX FOR (n:Class) ON (n.NAME);
CREATE INDEX FOR (n1:Method) ON (n1.SIGNATURE,n1.SUB_SIGNATURE);
```
接下来依次导入csv文件,替换$path
导入Methods
```
CALL apoc.periodic.iterate("CALL apoc.load.csv('file://$path', {header:true, mapping:{ IS_STATIC: {type:'boolean'}, HAS_PARAMETERS:{type:'boolean'}, IS_SINK: { type: 'boolean'}, IS_SOURCE: { type: 'boolean'}, IS_POLLUTED: { type: 'boolean'}, IS_SERIALIZABLE: {type:'boolean'} }}) YIELD map AS row RETURN row", "MERGE(m:Method {ID:row.ID} ) ON CREATE SET m = row", {batchSize:5000, iterateList:true, parallel:true})
```
导入Classes
```
CALL apoc.periodic.iterate("CALL apoc.load.csv('file://$path', {header:true, mapping:{ HAS_SUPER_CLASS: {type:'boolean'}, HAS_INTERFACES: {type:'boolean'}, IS_INTERFACE: {type:'boolean'}, SERIALIZABLE:{type:'boolean'}}}) YIELD map AS row RETURN row","MERGE (c:Class {NAME:row.NAME}) ON CREATE SET c = row", {batchSize:5000, iterateList:true, parallel:true})
```
导入边信息
```
// extends
CALL apoc.periodic.iterate("CALL apoc.load.csv('file://$path', {header:true}) YIELD map AS row RETURN row","MATCH( c1:Class {ID:row.SOURCE} ) MATCH ( c2:Class { ID:row.TARGET } ) MERGE (c1) -[e:EXTENDS { ID:row.ID }] -> (c2)", {batchSize:1000, iterateList:true, parallel:false})
// interface
CALL apoc.periodic.iterate("CALL apoc.load.csv('file://$path', {header:true}) YIELD map AS row RETURN row","MATCH( c1:Class {ID:row.SOURCE} ) MATCH ( c2:Class { ID:row.TARGET } ) MERGE (c1) -[e:INTERFACE { ID:row.ID }] -> (c2)", {batchSize:1000, iterateList:true, parallel:false})
// has
CALL apoc.periodic.iterate("CALL apoc.load.csv('file://$path', {header:true}) YIELD map AS row RETURN row","MATCH(c:Class{ID:row.CLASS_REF}) MATCH(m:Method { ID:row.METHOD_REF }) MERGE (c) -[e:HAS { ID:row.ID }]-> (m)", {batchSize:1000, iterateList:true, parallel:false})
// call
CALL apoc.periodic.iterate("CALL apoc.load.csv('file://$path', {header:true, mapping:{ IS_POLLUTED: {type: 'boolean'} }}) YIELD map AS row RETURN row","MATCH ( m1:Method {ID:row.SOURCE} ) MATCH ( m2:Method {ID:row.TARGET }) MERGE (m1)-[e:CALL {ID:row.ID, LINE_NUM:row.LINE_NUM, IS_POLLUTED:row.IS_POLLUTED, POLLUTED_POSITION:row.POLLUTED_POSITION, REAL_CALL_TYPE:row.REAL_CALL_TYPE, INVOKER_TYPE: row.INVOKER_TYPE }]->(m2)", {batchSize:5000, iterateList:true, parallel:false})
// alias
CALL apoc.periodic.iterate("CALL apoc.load.csv('file://$path', {header:true}) YIELD map AS row RETURN row","MATCH ( m1:Method {ID:row.SOURCE} ) MATCH ( m2:Method {ID:row.TARGET }) MERGE (m1)-[e:ALIAS {ID:row.ID}]-(m2)", {batchSize:1000, iterateList:true, parallel:false})
```


怎么找利用链
```
match (from:Method {IS_SINK:true})
match (to:Method {NAME:"readObject"})  
call apoc.algo.allSimplePaths(from, to, "<CALL|ALIAS", 12) yield path
return * limit 20
```
如果人工判断call边有问题，删除固定边
```
match ()-[c:CALL]->() where c.ID in ["cebe8399-45a0-43ee-b877-0ba04b8d1537"] delete c
```