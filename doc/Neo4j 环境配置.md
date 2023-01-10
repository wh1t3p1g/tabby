## #0 前言

当前文档适用于 Neo4j v4.x，如需配置 Neo4j v5.x，参考 [Neo4j 环境配置 V5]

## #1 下载最新版 Neo4j

可以从官网下载相应的包， [Download](https://neo4j.com/download/)

也可以尝试使用 docker，Neo4j 官方提供相应的 docker，但根据实际测试，在性能上没有上面那种好。

## #2 下载 APOC 插件

下载 Neo4j 的 APOC 插件 [apoc-4.2.0.0-all.jar](https://github.com/neo4j-contrib/neo4j-apoc-procedures/releases/download/4.2.0.0/apoc-4.2.0.0-all.jar) ，可根据具体的 Neo4j 版本选择对应的 APOC 版本。

关于 APOC 插件的版本选择方法：Neo4j 数据库版本的前两位对应 APOC 插件的版本
比如 Neo4j 数据库版本为 v4.2.1，则选择 APOC 插件 v4.2.x.x 版本

## #3 新建 Neo4j 图数据库

当前版本可自定义账号密码，新建 Neo4j 数据库项目后将账号密码填入 `conf/settings.properties`
```
tabby.neo4j.username                      = neo4j  
tabby.neo4j.password                      = password  
tabby.neo4j.url                           = bolt://127.0.0.1:7687
```
默认 Neo4j 的账户名为 `neo4j`

## #4 配置新建的图数据库

打开 Neo4j 配置文件，添加以下配置
```
apoc.import.file.enabled=true
apoc.import.file.use_neo4j_config=false
```
修改配置文件中的以下配置，这里的配置可以根据实际使用的机器内存进行修改，以下配置是我 16g 内存下的配置
```
dbms.memory.heap.initial_size=1G
dbms.memory.heap.max_size=4G
dbms.memory.pagecache.size=4G
dbms.security.procedures.unrestricted=jwt.security.*,apoc.*
```
另外，也可以通过官方的 neo4j-admin 来推荐配置内存大小，https://neo4j.com/docs/operations-manual/current/tools/neo4j-admin/neo4j-admin-memrec/
![](assets/Neo4j%20环境配置/image-20230110233453437.jpeg)

打开插件的目录，把下载的 apoc 插件的 jar 文件复制到该目录下
![](assets/Neo4j%20环境配置/image-20230110233530857.jpeg)

![](assets/Neo4j%20环境配置/image-20230110233552632.jpeg)

同时，编译 tabby-path-finder 项目，将编译后的 jar 包也放入 plugins 目录

## #5 检查配置是否完成
启动图数据库并打开 Neo4j Brower

查询 `CALL apoc.help('all')`
![](assets/Neo4j%20环境配置/image-20230110233659101.jpeg)
查询 `CALL tabby.help('all')`
![](assets/Neo4j%20环境配置/image-20230110233750744.png)

## #6 图数据库索引配置（必做项）

为了加快导入/删除的速度，这里提前对节点进行索引建立
```
CREATE CONSTRAINT c1 IF NOT EXISTS ON (c:Class) ASSERT c.ID IS UNIQUE;
CREATE CONSTRAINT c2 IF NOT EXISTS ON (c:Class) ASSERT c.NAME IS UNIQUE;
CREATE CONSTRAINT c3 IF NOT EXISTS ON (m:Method) ASSERT m.ID IS UNIQUE;
CREATE CONSTRAINT c4 IF NOT EXISTS ON (m:Method) ASSERT m.SIGNATURE IS UNIQUE;
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
