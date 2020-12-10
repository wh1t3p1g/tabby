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

https://github.com/Fraunhofer-AISEC/codyze 是否存在参考价值

污染的可能性种类：
1. 函数的参数
2. 对象本身可控，那么他的类属性就可控了

我们可以排除的是什么？纯无法传递的函数确认
1. 函数无参数，并且对象本身又不可控
2. 可污染的两种情况涉及的对象，都不参与函数内部逻辑

最终保存的分析状态是什么？
1. 记录调用关系时，记录当前调用对象是否可控，函数的入参是否可控，
2. 如果函数存在返回值，这个返回值是否可控
3. 并且这个可控对应的位置/来源是什么

最终我们要达成的分析状态是怎么样的？
1. 检索出所有调用sink函数的函数
2. 通过保存的分析状态，判断这里调用sink函数的参数是否是可控的
3. 如果是可控的，找到可控对应的位置/来源，继续往上层找
4. 直到最后到达source点