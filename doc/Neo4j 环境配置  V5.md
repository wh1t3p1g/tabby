## #1 下载最新版 Neo4j

可以从官网下载相应的包， [Download](https://neo4j.com/download/)

也可以尝试使用 docker，Neo4j 官方提供相应的 docker，但根据实际测试，在性能上没有上面那种好。

## #2 下载 APOC 插件

Neo4j v5 版本 apoc 插件改成了两个部分 `apoc-core` 和 `apoc-extend`，可自行在以下两个库下载
- apoc-core          https://github.com/neo4j/apoc
- apoc-extended  https://github.com/neo4j-contrib/neo4j-apoc-procedures

关于 apoc 插件的版本选择方法：Neo4j 数据库版本的前两位对应 apoc 插件的版本
比如 Neo4j 数据库版本为 v5.3.0，则选择 apoc 插件 v5.3.x 版本

## #3 新建 Neo4j 图数据库

当前版本可自定义账号密码，新建 Neo4j 数据库项目后将账号密码填入 `conf/settings.properties`
```
tabby.neo4j.username                      = neo4j  
tabby.neo4j.password                      = password  
tabby.neo4j.url                           = bolt://127.0.0.1:7687
```
默认 Neo4j 的账户名为 `neo4j`

## #4 配置新建的图数据库

Neo4j v5 拆分了 apoc 的配置信息
首先，配置 neo4j 数据库的配置
```
# 注释下面的配置，允许从本地任意位置载入csv文件
#server.directories.import=import

# 允许 apoc 扩展
dbms.security.procedures.unrestricted=jwt.security.*,apoc.*

# 修改内存相关配置 
# 可以通过官方的neo4j-admin来推荐配置内存大小，https://neo4j.com/docs/operations-manual/current/tools/neo4j-admin/neo4j-admin-memrec/
dbms.memory.heap.initial_size=1G
dbms.memory.heap.max_size=4G
dbms.memory.pagecache.size=4G
```
其次，配置 apoc 的配置，需要找到配置文件目录
![](assets/Neo4j%20环境配置%20%20V5/image-20230110234810527.png)
新建 apoc.conf 文件
```
apoc.import.file.enabled=true
apoc.import.file.use_neo4j_config=false
```

最后，配置一下 apoc 和 tabby 插件，打开 plugins 目录将对应的 jar 复制到该目录
![](assets/Neo4j%20环境配置%20%20V5/image-20230110234134821.png)
上述步骤做完之后，重启数据库

## #5 检查配置是否完成
启动图数据库并打开 Neo4j Brower

查询 `CALL apoc.help('all')`
![](assets/Neo4j%20环境配置/image-20230110233659101.jpeg)
查询 `CALL tabby.help('tabby')`
![](assets/Neo4j%20环境配置/image-20230110233750744.png)

## #6 图数据库索引配置（必做项）

为了加快导入/删除的速度，这里提前对节点进行索引建立
```
CREATE CONSTRAINT c1 IF NOT EXISTS FOR (c:Class) REQUIRE c.ID IS UNIQUE;
CREATE CONSTRAINT c2 IF NOT EXISTS FOR (c:Class) REQUIRE c.NAME IS UNIQUE;
CREATE CONSTRAINT c3 IF NOT EXISTS FOR (m:Method) REQUIRE m.ID IS UNIQUE;
CREATE CONSTRAINT c4 IF NOT EXISTS FOR (m:Method) REQUIRE m.SIGNATURE IS UNIQUE;
CREATE INDEX index1 IF NOT EXISTS FOR (m:Method) ON (m.NAME);
CREATE INDEX index2 IF NOT EXISTS FOR (m:Method) ON (m.CLASSNAME);
CREATE INDEX index3 IF NOT EXISTS FOR (m:Method) ON (m.NAME, m.CLASSNAME);
CREATE INDEX index4 IF NOT EXISTS FOR (m:Method) ON (m.NAME, m.NAME0);
CREATE INDEX index5 IF NOT EXISTS FOR (m:Method) ON (m.SIGNATURE);
CREATE INDEX index6 IF NOT EXISTS FOR (m:Method) ON (m.NAME0);
CREATE INDEX index7 IF NOT EXISTS FOR (m:Method) ON (m.NAME0, m.CLASSNAME);
:schema //查看表库
:sysinfo //查看数据库信息
```
如果经过很多的导入/删除操作，图数据库占用了很多的硬盘存储，那么可以将原有的图数据库删除，重新按照上面的步骤新建图数据库。
删除所有约束
```
DROP CONSTRAINT c1;
DROP CONSTRAINT c2;
DROP CONSTRAINT c3;
DROP CONSTRAINT c4;
DROP INDEX index1;
DROP INDEX index2;
DROP INDEX index3;
DROP INDEX index4;
DROP INDEX index5;
DROP INDEX index6;
DROP INDEX index7;
```
