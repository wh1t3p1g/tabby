# TABBY

[English Version](https://github.com/wh1t3p1g/tabby/blob/master/README_EN.md)

![Platforms](https://img.shields.io/badge/Platforms-OSX-green.svg)
![Java version](https://img.shields.io/badge/Java-8+-blue.svg)
![License](https://img.shields.io/badge/License-apache%202-green.svg)

TABBY is a Java Code Analysis Tool based on [Soot](https://github.com/soot-oss/soot) .

It can parse JAR/WAR/CLASS files to CPG (Code Property Graph) based on [Neo4j](https://neo4j.com/) .

TABBY 是一款针对 Java 语言的静态代码分析工具，相关工作已被接收发表在  The 53rd Annual IEEE/IFIP International Conference on Dependable Systems and Networks (DSN 2023)，会议论文录用名单详见[DSN2023](https://dsn2023.dei.uc.pt/program_research.html)。

TABBY使用静态分析框架 [Soot](https://github.com/soot-oss/soot) 作为语义提取工具，将JAR/WAR/CLASS文件转化为代码属性图。
并使用 [Neo4j](https://neo4j.com/) 图数据库来存储生成的代码属性图CPG。

Note: 如果使用中存在什么问题，欢迎在 [discussions](https://github.com/wh1t3p1g/tabby/discussions) 提问！

Note: Welcome to new a discussion at [discussions](https://github.com/wh1t3p1g/tabby/discussions) about TABBY!

## #1 使用方法

使用 Tabby 需要有以下环境：
- JAVA 环境
- 可用的 Neo4j 图数据库
- Neo4j Browser 或者其他 Neo4j 可视化的工具

具体的使用方法参见： [Tabby 食用指北](https://github.com/wh1t3p1g/tabby/blob/master/doc/Tabby%20%E9%A3%9F%E7%94%A8%E6%8C%87%E5%8C%97.md)

## #2 Tabby 的适用人群
开发 Tabby 的初衷是想要提高代码审计的效率，尽可能的减少人工检索的工作量

使用 tabby 生成的代码属性图（当前版本 1.2.0）可以完成以下的工作场景：

- 挖掘目标项目中的反序列化利用链，支持大多数序列化机制，包括 Java 原生序列化机制、Hessian、XStream 等
- 挖掘目标项目中的常见 Web 漏洞，支持分析 WAR/JAR/FATJAR/JSP/CLASS 文件
- 搜索符合特定条件的函数、类，譬如检索调用了危险函数的静态函数

利用 tabby 生成后的代码属性图，在 Neo4j 图数据库中进行动态自定义漏洞挖掘/利用链挖掘。

## #3 成果

- [现有利用链覆盖](https://github.com/wh1t3p1g/tabby/wiki/%E7%8E%B0%E6%9C%89%E5%88%A9%E7%94%A8%E9%93%BE%E8%A6%86%E7%9B%96)
- CVE-2021-21346 [如何高效的挖掘 Java 反序列化利用链？](https://blog.0kami.cn/2021/03/14/java-how-to-find-gadget-chains/)
- CVE-2021-21351
- CVE-2021-39147 [如何高效地捡漏反序列化利用链？](https://www.anquanke.com/post/id/251814)
- CVE-2021-39148
- CVE-2021-39152 [m0d9](http://m0d9.me/2021/08/29/XStream%E5%8F%8D%E5%BA%8F%E5%88%97%E5%8C%96%EF%BC%88%E4%B8%89%EF%BC%89%E2%80%94%E2%80%94Tabby%20CVE%E4%B9%8B%E6%97%85/)
- CVE-2021-43297
- 子项目：Java 反序列化利用框架 [ysomap](https://github.com/wh1t3p1g/ysomap)
- 子项目：具备数据流分析的 Neo4j 扩展 [tabby-path-finder](https://github.com/wh1t3p1g/tabby-path-finder)
- 设计原理
  - KCon 2022 [Tabby: Java Code Review Like A Pro](https://github.com/knownsec/KCon/blob/master/2022/tabby%20java%20code%20review%20like%20a%20pro%E3%80%90KCon2022%E3%80%91.pdf)
  - [基于代码属性图的自动化漏洞挖掘实践](https://blog.0kami.cn/blog/2023/%E5%9F%BA%E4%BA%8E%E4%BB%A3%E7%A0%81%E5%B1%9E%E6%80%A7%E5%9B%BE%E7%9A%84%E8%87%AA%E5%8A%A8%E5%8C%96%E6%BC%8F%E6%B4%9E%E6%8C%96%E6%8E%98%E5%AE%9E%E8%B7%B5/)
## #4 问题

#### 1. 关于代码属性图的设计思路？

[1] Martin M, Livshits B, Lam M S. Finding application errors and security flaws using PQL: a program query language[J]. Acm Sigplan Notices, 2005,40(10): 365-383.

[2] Yamaguchi F, Golde N, Arp D, et al. Modeling and discovering vulnerabilities with code property graphs[C]//2014 IEEE Symposium on Security and Privacy. IEEE, 2014:590-604.

[3] Backes M, Rieck K, Skoruppa M, et al. Efficient and flexible discovery of php application vulnerabilities[C]//2017 IEEE european symposium on security and privacy (EuroS&P). IEEE, 2017:334-349.

如上三篇论文在代码属性图的构建方案上做了相关尝试，但这些方案均不适用于 Java 语言这种面向对象语言。为什么？

首先，我们希望代码属性图最终能达成什么样的效果？对我来说，我希望我能利用代码属性图找到完整的路径，从而无需代码的实现去做可达路径的查找

所以，依据这个想法，我们需要解决的一点是 Java 语言的多态特性。在反序列化利用链中，可以发现的是很多利用链均是不等数量的 gadget"拼接"起来，而这个"拼接"的操作就是多态特性所有具体实现函数的枚举

但是在图上来看，其实不同的 gadget 之间其实是分裂的

为了解决上面的问题，我提出了面向 Java 语言的代码属性图构建方案，包括类关系图、函数别名图、精确的函数调用图。

这其中函数别名图将所有的函数实现关系进行了聚合，这样在图的层面来看，ALIAS 依赖边连接了不同的 gadget，从而解决了 Java 多态的问题。

具体的细节可以看我的毕业论文，或是直接看代码。

#### 2. 设计的代码属性图存在哪些问题？

Tabby 的实现肯定会存在分析遗漏或错误的情况，但当前版本的 tabby 生成的代码属性图可以覆盖大多数现有的利用链，详见成果部分

从程序分析的角度，tabby 的实现必然会存在可控性分析遗漏的问题，有时候遗漏会造成精确函数调用图的不精确，这部分将持续进行更新优化。

而从使用体验来看，函数别名图的使用会导致如下情况的误报
```java  
class B {  
    public void func(){  
    }
}  
class A extends B{  
    public void func(){}  
    public void func1(){        
        this.func();    
    }
}  
class C extends B{  
    public void func(){}
}  
```
假设 A 对象的 func 继承了 B 对象，并且重载了函数 func。那么此时会出现什么问题？

首先，func1 函数中会存在函数调用 `func1-[:CALL]>A.func`，并且 func 函数存在 ALIAS 依赖边关系 `A.func-[:ALIAS]-B.func`

那么，从图检索的角度来看，会存在这样一条通路 `func1-[:CALL]>A.func-[:ALIAS]-B.func-[:ALIAS]-C.func`

但是，我们看代码，这条通路肯定是不可能的，因为 A.func1 实际调用的是 A.func，并不存在本身对象被替换为 C 对象的可能。

所以此时也就造成了误报。那么怎么解决这个误报问题呢？这里就看第 4 个问题吧

#### 3. 我该怎么利用 Tabby 生成的代码属性图

Tabby 生成的代码属性图实际上是由类关系图、函数别名图和精确的函数调用图组成的。它并不会直接输出类似利用链的联通路径，需要你使用相关的图查询语法进行查询而得出。

Tabby 生成的代码属性图支持两种模式，一是人工判断，二是编写污点分析的自动化利用脚本。

首先，对于人工判断，利用图查询语言边查询，边人工对照具体代码来进行分析。这里其实工作量是比较大的，所以也提供了自动化的机制

然后，是自动化脚本的方式。Tabby 对每一条函数调用边 CALL，均计算了当前调用本身的可控性，具体参数为 CALL 边的 POLLUTED_POSITION

举个例子，当 POLLUTED_POSITION 为`[0,-1,-3,1]`时，其中数组的 index 分别指向调用者本身、函数参数集等，数据的值指代的是当前受污点的变量指向

以当前例子来说明，数据的第一个位置指代的是当前函数的调用者本身的执行，当前为 0，0 指代调用者来自函数参数

数组第二个位置指代的是调用函数的参数的第一个参数为-1，-1 指代类属性

数组第三个位置指代的是调用函数的参数的第二个参数为-3，-3 指代当前位置的变量不可控

数组第四个位置指代的是调用函数的参数的第三个参数为 1，1 指代当前位置的变量来自函数参数的第 2 个

即数组内容
- -3 => 不可控
- -1 => 类属性
- 0-n => 函数参数的位置

利用这些信息，可以进行从底向上的污点分析。sink 函数处提供了先验知识，通过与调用边的 POLLUTED_POSITION 进行比较得出当前调用是否是可控的

#### 4. 关于自动化的利用，看起来很复杂，会不会出相关的案例

对于检索出来的可联通路径，我们还需要进行进一步的判断。这里可以人工直接跟着代码去分析判断，也可以使用上面的自动化分析方案进行通路的分析（这部分也能直接解决前面函数别名图的误报问题，即提前判断下一个节点是否是允许具体实现枚举的）

ps: 目前已实现了 Neo4j 扩展 tabby-path-finder 用于自动化利用

#### 5. 关于性能问题
tabby 本来预想的是以阶段性的方式，以分析后的基础库分析结果为基础，后续分析依赖 h2 数据库中的先验知识。但是实际在使用的时候 h2 数据库的性能还是不太行，这一部分以后有时间对构架重新编写吧

所以这里推荐的使用方式是依赖内存去进行一次性的分析：每次分析前先将上一次的分析结果(cache/graphdb.mv.db 以及 rules/ignore.json)删除，然后再进行代码属性图的生成。

tabby 实验的时候大概 6gb 的内存可以处理 4w+类

然后关于运行速度，tabby 当前仍是以单进程顺序分析的方式进行的，且本质上分析任务是计算 IO 类型的，多线程是否能提高效率这里存疑。

目前，tabby 对 JDK 19 个 Jar 文件（3w 多个类）分析需要 7 分种多点（450s 左右），所以可以 take a cup of coffee XD

我的测试机是比较老版本的 mac pro，所以以上测试数据可以作为一个参考。

#### 6. 常见报错

常见报错主要是 soot 产生的

- 基础类缺失，这部分可以从 soot 的报错信息看到具体补救方式，tabby 提供了 basicClasses.json 用于解决这一问题
- soot getBody convert error，这个错误暂无解决方案，是 soot 的解析问题，只能将当前这个会报错的 jar 文件移除。譬如 weblogic 12g 的 wlthint3client.jar 文件会有这个问题，只能等 soot 更新。
- 其他由 tabby 产生的 bug，譬如空指针异常，可以直接提 issue 给我并附上产生错误的 jar 文件。

此外，tabby 主要经过了 MACOX 的测试，暂未在其他的平台进行测试。嗯，不确定 win 平台行不行（主要是获取 jdk 依赖的方式需要适配）。

#### 7. 使用小 trick

其实，在属性图生成的过程中，许多代码分析其实是无用的，但是由于程序没办法判断是否是无用的，所以该全量分析就得全量分析。

但是，如果遇到及其消耗内存或 cpu 计算能力的情况（即卡在了函数处理进度处）

可以使用以下方法对分析进行优化：

1. 运行 jar 时加上 debug，`-Dlogging.level.tabby=DEBUG`，然后看它最终在那个函数处消耗特别大或就卡在那里了
2. 打开 IDEA，加 lib，找到具体的实现，如果这个函数经过人工分析后，是认为可以被忽略的，那么添加至 knowledge 库 （sinks.json && system.json）
3. 在 `system.json`，添加 ignore 规则，比如致远的一个函数 `<com.seeyon.ctp.common.parser.BytesEncodingDetect: void initialize_frequencies()>`，ignore 规则如下

```json  
{"name": "com.seeyon.ctp.common.parser.BytesEncodingDetect", "rules":[  
    {"function": "initialize_frequencies", "type": "ignore", "vul": "","actions":{}, "polluted":[], "signatures":[]}  ]}  
```

4. 添加完 ignore 规则后，再运行 tabby 就可以跳过该函数的分析

## #5 初衷&致谢

当初，在进行利用链分析的过程中，深刻认识到这一过程是能被自动化所代替的（不管是 Java 还是 PHP）。但是，国内很少有这方面工具的开源。GI 工具实际的检测效果其实并不好，为此，依据我对程序分析的理解，开发了 tabby 工具。我对 tabby 工具期望不单单只是在利用链挖掘的应用，也希望后续能从漏洞分析的角度利用 tabby 的代码属性图进行分析。我希望 tabby 能给国内的 Java 安全研究人员带来新的工作模式。

当然，当前版本的 tabby 仍然存在很多问题可以优化，希望有程序分析经验的师傅能一起加入 tabby 的建设当中，有啥问题可以直接联系我哦！

如果 tabby 给你的工作带来了便利，请不要吝啬你的🌟哦！

如果你使用 tabby 并挖到了漏洞，非常欢迎提供相关的成功案例 XD

如果你有能力一起建设，也可以一起交流，或直接 PR，或直接 issue

2021.12.02 Updated:

目前看，tabby 确实能发现一些现实环境中的安全问题。

但算法实现存在漏报（个人认为是比较严重的问题），目前回过头看，代码实现也过于 ugly。

目前决定不重改当前构架，新版思路已实现 1/4，但开源时间无法预知 XD

2023.01.10 Updated:

目前，已完成 tabby 2.0 的实现，正在做一些效果测试，开源时间待定 XD

- 优秀的静态分析框架 [soot](https://github.com/soot-oss/soot)
- [gadgetinspector](https://github.com/JackOfMostTrades/gadgetinspector)
- [ysoserial](https://github.com/frohoff/ysoserial) 和 [marshalsec](https://github.com/mbechler/marshalsec)
