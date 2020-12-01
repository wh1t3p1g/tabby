# tabby-tmp
A CAT called tabby ( Code Analysis Tool )

# sinks
实例
```
  {"classname": "java.io.FileInputStream", "functions": [{"name": "<init>", "param": [0]}]},
```
不参与污点分析模式，表示FileInputStream类的构造函数为sink点
参与污点分析模式，表示FileInputStream类的构造函数的第一个参数可控时，标记为可控的sink点

上述两种，如果存在某个函数内，则当前函数为不动点

# note
neo4j 第一次运行新库时 耗时长（第二次之后有缓存，会快一点，但时间长了会有很大的硬盘占用，可以删除库再新建）
```
初始化 节点限制 可以极大的加快载入速度
CREATE CONSTRAINT ON (c:Class) ASSERT c.uuid IS UNIQUE;
CREATE CONSTRAINT ON (m:Method) ASSERT m.uuid IS UNIQUE;
CREATE INDEX ON :Class(name);
CREATE INDEX ON :Method(signature,subSignature);
:schema 查看表库
:sysinfo 查看数据库信息
```