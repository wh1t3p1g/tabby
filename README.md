![img.png](logo.png)
# 
![Java version](https://img.shields.io/badge/Java-17-blue.svg)
![License](https://img.shields.io/badge/License-MIT-green.svg)
![Blackhat](https://img.shields.io/badge/Blackhat-Arsenal%202024-red.svg)

TABBY 是一款针对 Java 语言的静态代码分析工具，可用于快速发现多种类型的 Java 语言相关的漏洞。

TABBY 使用静态分析框架 [Soot](https://github.com/soot-oss/soot) 作为语义提取工具，将JAR/WAR/CLASS文件转化为代码属性图，
并使用 [Neo4j](https://neo4j.com/) 图数据库来存储生成的代码属性图CPG。

此外，通过扩展 Neo4j 的[路径遍历逻辑](https://github.com/wh1t3p1g/tabby-path-finder)，TABBY 可以使用简单的 cypher 语句即可完成复杂污点分析输出潜在的漏洞调用链路。

## #1 使用方法

使用 Tabby 需要有以下环境：
- JAVA 环境
- 可用的 Neo4j 图数据库
- Neo4j Browser 或者其他 Neo4j 可视化的工具或者 Tabby 的 IDEA [插件](https://github.com/wh1t3p1g/tabby-intellij-plugin)

具体的使用方法参见： [Tabby Quick Start](https://www.yuque.com/wh1t3p1g/tp0c1t/lf12lg69ngh47akx)

## #2 Tabby的适用人群

开发 Tabby 的初衷是想要提高代码审计的效率，尽可能的减少人工检索的工作量

使用 tabby 生成的代码属性图可以完成以下的工作场景：

- 挖掘目标项目中的反序列化利用链，支持大多数序列化机制，包括 Java 原生序列化机制、Hessian、XStream 等
- 挖掘目标项目中的常见 Web 漏洞，支持分析 WAR/JAR/FATJAR/JSP/CLASS 文件
- 搜索符合特定条件的函数、类，譬如检索调用了危险函数的静态函数

利用 tabby 生成后的代码属性图，在 Neo4j 图数据库中进行动态自定义漏洞挖掘/利用链挖掘。

## #3 成果

- [现有利用链覆盖](https://github.com/wh1t3p1g/tabby/wiki/%E7%8E%B0%E6%9C%89%E5%88%A9%E7%94%A8%E9%93%BE%E8%A6%86%E7%9B%96)
- papers && slides
    - KCon 2022 [Tabby: Java Code Review Like A Pro](https://github.com/wh1t3p1g/tabby/blob/v2/papers/tabby%20java%20code%20review%20like%20a%20pro.pdf)
    - KCon 议题补充 [基于代码属性图的自动化漏洞挖掘实践](https://blog.0kami.cn/blog/2023/%E5%9F%BA%E4%BA%8E%E4%BB%A3%E7%A0%81%E5%B1%9E%E6%80%A7%E5%9B%BE%E7%9A%84%E8%87%AA%E5%8A%A8%E5%8C%96%E6%BC%8F%E6%B4%9E%E6%8C%96%E6%8E%98%E5%AE%9E%E8%B7%B5/)
    - DSN 2023 [Tabby: Automated Gadget Chain Detection for Java Deserialization Vulnerabilities](https://ieeexplore.ieee.org/document/10202660)
    - BlackHat EU 2024 [Tabby: Simplifying the Art of Java Vulnerability Hunting](https://github.com/wh1t3p1g/tabby/blob/v2/papers/Tabby%20Simplifying%20the%20Art%20of%20Java%20Vulnerability%20Hunting.pdf)
    - ICASSP 2025 [VulKiller: Java Web Vulnerability Detection with Code Property Graph and Large Language Models]()
- CVEs
  - CVE-2021-21346 [如何高效的挖掘 Java 反序列化利用链？](https://blog.0kami.cn/2021/03/14/java-how-to-find-gadget-chains/)
  - CVE-2021-21351
  - CVE-2021-39147 [如何高效地捡漏反序列化利用链？](https://www.anquanke.com/post/id/251814)
  - CVE-2021-39148
  - CVE-2021-39152 [m0d9](http://m0d9.me/2021/08/29/XStream%E5%8F%8D%E5%BA%8F%E5%88%97%E5%8C%96%EF%BC%88%E4%B8%89%EF%BC%89%E2%80%94%E2%80%94Tabby%20CVE%E4%B9%8B%E6%97%85/)
  - CVE-2021-43297
  - CVE-2022-39198 [yemoli](https://yml-sec.top/2022/12/30/%E4%BB%8Ecve-2022-39198%E5%88%B0%E6%98%A5%E7%A7%8B%E6%9D%AFdubboapp/#CVE-2022-39198%E6%8C%96%E6%8E%98)
  - CVE-2023-23638

## #4 常见问题

- [常见问题](https://www.yuque.com/wh1t3p1g/tp0c1t/ueduxuz6fmxhpoyb)

如果使用中存在其他问题，欢迎在 [discussions](https://github.com/wh1t3p1g/tabby/discussions) 提问！

如果使用中发现了 Tabby 实现上的 bug，欢迎在 [issues](https://github.com/wh1t3p1g/tabby-path-finder/issues) 提交相关错误详情！

## #5 初衷&致谢

当初，在进行利用链分析的过程中，深刻认识到这一过程是能被自动化所代替的（不管是 Java 还是 PHP）。但是，国内很少有这方面工具的开源。GI 工具实际的检测效果其实并不好，为此，依据我对程序分析的理解，开发了 tabby 工具。我对 tabby 工具期望不单单只是在利用链挖掘的应用，也希望后续能从漏洞分析的角度利用 tabby 的代码属性图进行分析。我希望 tabby 能给国内的 Java 安全研究人员带来新的工作模式。

当然，当前版本的 tabby 仍然存在很多问题可以优化，希望有程序分析经验的师傅能一起加入 tabby 的建设当中，有啥问题可以直接联系我哦！

如果 tabby 给你的工作带来了便利，请不要吝啬你的🌟哦！

如果你使用 tabby 并挖到了漏洞，非常欢迎提供相关的成功案例 XD

如果你有能力一起建设，也可以一起交流，或直接 PR，或直接 issue

- 优秀的静态分析框架 [soot](https://github.com/soot-oss/soot)
- [gadgetinspector](https://github.com/JackOfMostTrades/gadgetinspector)
- [ysoserial](https://github.com/frohoff/ysoserial) 和 [marshalsec](https://github.com/mbechler/marshalsec)
