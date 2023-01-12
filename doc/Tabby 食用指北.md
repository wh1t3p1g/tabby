## #0 环境配置

Neo4j v4.x 参考 [Neo4j 环境配置](https://github.com/wh1t3p1g/tabby/blob/master/doc/Neo4j%20%E7%8E%AF%E5%A2%83%E9%85%8D%E7%BD%AE.md)

Neo4j v5.x 参考 [Neo4j 环境配置 V5](https://github.com/wh1t3p1g/tabby/blob/master/doc/Neo4j%20%E7%8E%AF%E5%A2%83%E9%85%8D%E7%BD%AE%20%20V5.md)

ps: tabby 对数据库稳定性要求不高，可尝试新版的 Neo4j 5.x

## #1 前置基础

Tabby 减少了在代码层面的人工检索，但仍然需要你有以下基础：
- Java 语言的基础，知道你目的是什么！譬如知道该将什么函数作为 sink 函数
- 相关漏洞的基础，比如 Java 反序列化利用链的原理
- Neo4j 的 [查询语法基础](https://neo4j.com/docs/cypher-manual/current/clauses/)

## #2 功能介绍

Tabby 实现了将 Jar/War/Class/Jsp 文件转化为代码属性图的功能（类似 codeql），你可以在生成的代码属性图中去寻找你想要的利用路径。

这里可以用 Tabby 来完成：
- Java 反序列化利用链的查找
- 符合特定条件的函数查找
- 其他，开动你的小脑瓜子，在源码层面探索未知问题！

## #3 基础使用方法
配置 `conf/settings.properties` 文件，参考 [Tabby 配置文件介绍](https://github.com/wh1t3p1g/tabby/blob/master/doc/Tabby%20%E9%85%8D%E7%BD%AE%E6%96%87%E4%BB%B6%E4%BB%8B%E7%BB%8D.md)

运行方式
```bash
java -Xmx6g -jar tabby.jar
```
或使用项目里的 `run.sh` 文件
内存大小根据实际情况进行调整

## #4 使用案例分享

- [现有利用链覆盖](https://github.com/wh1t3p1g/tabby/wiki/%E7%8E%B0%E6%9C%89%E5%88%A9%E7%94%A8%E9%93%BE%E8%A6%86%E7%9B%96)
- [如何高效的挖掘 Java 反序列化利用链？](https://blog.0kami.cn/2021/03/14/java-how-to-find-gadget-chains/)
- [如何高效地捡漏反序列化利用链？](https://www.anquanke.com/post/id/251814)
- [XStream反序列化（三）——Tabby CVE之旅](https://m0d9.me/2021/08/29/XStream%E5%8F%8D%E5%BA%8F%E5%88%97%E5%8C%96%EF%BC%88%E4%B8%89%EF%BC%89%E2%80%94%E2%80%94Tabby%20CVE%E4%B9%8B%E6%97%85/) by m0d9