# TABBY
![Platforms](https://img.shields.io/badge/Platforms-OSX-green.svg)
![Java version](https://img.shields.io/badge/Java-8%2b-blue.svg)
![License](https://img.shields.io/badge/License-apache%202-green.svg)

TABBY is a Java Code Analysis Tool based on [Soot](https://github.com/soot-oss/soot). 

It can parse JAR files to CPG (Code Property Graph) based on [Neo4j](https://neo4j.com/).

TABBY是一款针对Java语言的静态代码分析工具。

它使用静态分析框架 [Soot](https://github.com/soot-oss/soot) 作为语义提取工具，将JAR文件转化为代码属性图。
并使用 [Neo4j](https://neo4j.com/) 图数据库来存储生成的代码属性图CPG。

## #1 使用方法

使用Tabby需要有以下环境：
- JDK8的环境
- 可用的Neo4j图数据库 [Neo4j环境配置](https://github.com/wh1t3p1g/tabby/wiki/Neo4j%E7%8E%AF%E5%A2%83%E9%85%8D%E7%BD%AE)
- Neo4j Browser

具体的使用方法参见：[Tabby食用指北](https://github.com/wh1t3p1g/tabby/wiki/Tabby%E9%A3%9F%E7%94%A8%E6%8C%87%E5%8C%97)

## #2 Tabby的适用人群
开发Tabby的初衷是想要提高代码审计的效率，不管你是Java开发，还是专职的安全工程师，
都可以用Tabby生成的代码属性图（当前版本1.0）来完成以下的工作场景：

- 挖掘目标jar包中潜藏的Java反序列化利用链
- 搜索符合特定条件的函数、类等

以前对Jar的分析方法，往往为先反编译成java文件，然后再通过人工搜索特定函数来进行分析。

而有了Tabby之后，我们可以先生成相应jar包的代码属性图，然后使用Neo4j的查询语法来进行特定函数的搜索，特定条件的利用路径检索等

## #3 成果

- [现有利用链覆盖](https://github.com/wh1t3p1g/tabby/wiki/%E7%8E%B0%E6%9C%89%E5%88%A9%E7%94%A8%E9%93%BE%E8%A6%86%E7%9B%96)
- CVE-xxx
- 子项目： Java反序列化利用框架 [ysomap](https://github.com/wh1t3p1g/ysomap)

## #4 问题

#### 1. 关于代码属性图的实现思路
Tabby的实现是基于指针分析算法的数据流污点分析

由指针分析算法进行过程内分析，并以基于摘要的过程间分析算法分析函数间的调用关系。

当前业界并没有一个整体的规范来说明代码属性图需要包含哪些信息，tabby生成的代码属性图也算是一种对代码属性图的摸索方案。

在这里Tabby生成的代码属性图主要包含**类信息**、**类间信息**、**函数调用信息**，你可以通过代码属性图来查找符合特定条件的函数、漏洞等

#### 2. Tabby生成的代码属性图可靠吗？

Tabby的实现肯定会存在分析遗漏或错误的情况，但当前版本的tabby生成的代码属性图可以覆盖大多数现有的利用链，详见成果部分

Tabby的实现效果仍然有提升的空间，一方面需要大家多使用来积累各种异常cases，另一方面我也会不定期更新优化tabby

## #5 致谢

如果Tabby给你的工作带来了便利，请不要吝啬你的🌟哦！

- 优秀的静态分析框架[soot](https://github.com/soot-oss/soot)
- [gadgetinspector](https://github.com/JackOfMostTrades/gadgetinspector)
- [ysoserial](https://github.com/frohoff/ysoserial) 和 [marshalsec](https://github.com/mbechler/marshalsec)
