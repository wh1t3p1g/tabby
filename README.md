# tabby-tmp
A CAT called tabby ( Code Analysis Tool )

## 覆盖现有利用链

-[x] cc5
-[x] jdbcRowSetImpl fastjson
-[x] cc10,cc6
-[x] cc9
-[x] cc7
-[x] cc2,4
-[x] cc8
-[x] 


cc:3.2.1 利用链全覆盖 除了cc1,cc3 还未适配AnnotationInvocationHandler
TreeMap.put
javax.naming.ldap.Rdn$RdnEntry.compareTo
    com.sun.org.apache.xpath.internal.objects.XString.equal
        javax.swing.MultiUIDefaults.toString
            UIDefaults.get
                UIDefaults.getFromHashTable
                    UIDefaults$LazyValue.createValue
                    SwingLazyValue.createValue
                        javax.naming.InitialContext.doLookup()

match (from:Method {isSink:true,name:"forName"})
match (to:Method {name:"hashCode"})  
call apoc.algo.allSimplePaths(from, to, "<CALL|ALIAS", 12) yield path
return * limit 20


match ()-[r:CALL]->()
where r.uuid in "80061a93-993c-4519-903c-b70d9f934dd7"  or r.uuid="0b2c35d6-5f7e-4d3c-914f-766ad19c9034"
delete r


实验jar包收集
maven popular top 100 81个 https://mvnrepository.com/popular?p=10 1月6日 10.58
用户数均超过3000
中间件jar包
有名框架 spring struts



## 可能遇到的问题
### soot can't find the class: xxxx
补充你的待检测jar包 直到符合分析条件
https://repo.spring.io/release/org/springframework/spring/4.1.4.RELEASE/
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
CREATE CONSTRAINT ON (c:Class) ASSERT c.ID IS UNIQUE;
CREATE CONSTRAINT ON (m:Method) ASSERT m.ID IS UNIQUE;
CREATE INDEX FOR (n:Class) ON (n.NAME);
CREATE INDEX FOR (n1:Method) ON (n1.SIGNATURE,n1.SUB_SIGNATURE);
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

这里重要的是记录可控的来源，那么记在什么位置比较合适？记到methodRef的位置上

在利用链中，存在两种类型
1. 调用了sink函数的函数，这里我们只需判断是否符合我们sink函数的污染要求
2. 中间作为桥梁的函数，这里需要判断入参、this是否可控

而我们的指针分析是正向的，哪些情况是可以忽略，不做分析的

最终我们要达成的分析状态是怎么样的？
1. 检索出所有调用sink函数的函数
2. 通过保存的分析状态，判断这里调用sink函数的参数是否是可控的
3. 如果是可控的，找到可控对应的位置/来源，继续往上层找
4. 直到最后到达source点


TODO ： BeanComparator compare 的 alias边没有做好