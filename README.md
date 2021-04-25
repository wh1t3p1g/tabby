# tabby
![Platforms](https://img.shields.io/badge/Platforms-OSX-green.svg)
![Java version](https://img.shields.io/badge/Java-8-blue.svg)
![License](https://img.shields.io/badge/License-apache%202-green.svg)

TABBY is a Java Code Analysis Tool based on [Soot](https://github.com/soot-oss/soot).

It can parse JAR/WAR/CLASS files to CPG (Code Property Graph) based on [Neo4j](https://neo4j.com/).

TABBY是一款针对Java语言的静态代码分析工具。

它使用静态分析框架 [Soot](https://github.com/soot-oss/soot) 作为语义提取工具，将JAR/WAR/CLASS文件转化为代码属性图。
并使用 [Neo4j](https://neo4j.com/) 图数据库来存储生成的代码属性图CPG。

## #1 使用方法

使用Tabby需要有以下环境：
- JDK8的环境
- 可用的Neo4j图数据库 [Neo4j环境配置](https://github.com/wh1t3p1g/tabby/wiki/Neo4j%E7%8E%AF%E5%A2%83%E9%85%8D%E7%BD%AE)
- Neo4j Browser 或者其他可以进行Neo4j可视化的工具

具体的使用方法参见：[Tabby食用指北](https://github.com/wh1t3p1g/tabby/wiki/Tabby%E9%A3%9F%E7%94%A8%E6%8C%87%E5%8C%97)

## #2 Tabby的适用人群
开发Tabby的初衷是想要提高代码审计的效率，尽可能的减少人工检索的工作量

使用tabby生成的代码属性图（当前版本1.1.0）可以完成以下的工作场景：

- 挖掘目标代码文件中潜藏的Java反序列化利用链
- 搜索符合特定条件的函数、类，譬如检索调用了危险函数的静态函数
- 从某个端点到sink点的调用路径，用于检索可能的漏洞触发路径（如weblogic xmldecoder相关从processRequest到XMLDecoder.readObject的路径）

以前对Jar/War/Class的分析方法，往往为先反编译成java文件，然后再通过人工搜索特定函数来进行分析。

而有了Tabby之后，我们可以先生成相应的代码属性图，然后使用Neo4j的查询语法来进行特定函数的搜索，特定条件的利用路径检索等

## #3 成果

- [现有利用链覆盖](https://github.com/wh1t3p1g/tabby/wiki/%E7%8E%B0%E6%9C%89%E5%88%A9%E7%94%A8%E9%93%BE%E8%A6%86%E7%9B%96)
- CVE-2021-21346
- CVE-2021-21351
- 子项目： Java反序列化利用框架 [ysomap](https://github.com/wh1t3p1g/ysomap)

## #4 问题

#### 1. 关于代码属性图的设计思路？

[1] Martin M, Livshits B, Lam M S. Finding application errors and security flaws using PQL: a program query language[J]. Acm Sigplan Notices, 2005, 40(10): 365-383.

[2] Yamaguchi F, Golde N, Arp D, et al. Modeling and discovering vulnerabilities with code property graphs[C]//2014 IEEE Symposium on Security and Privacy. IEEE, 2014: 590-604.

[3] Backes M, Rieck K, Skoruppa M, et al. Efficient and flexible discovery of php application vulnerabilities[C]//2017 IEEE european symposium on security and privacy (EuroS&P). IEEE, 2017: 334-349.

如上三篇论文在代码属性图的构建方案上做了相关尝试，但这些方案均不适用于Java语言这种面向对象语言。为什么？

首先，我们希望代码属性图最终能达成什么样的效果？对我来说，我希望我能利用代码属性图找到完整的路径，从而无需代码的实现去做可达路径的查找

所以，依据这个想法，我们需要解决的一点是Java语言的多态特性。在反序列化利用链中，可以发现的是很多利用链均是不等数量的gadget"拼接"起来，而这个"拼接"的操作就是多态特性所有具体实现函数的枚举

但是在图上来看，其实不同的gadget之间其实是分裂的

为了解决上面的问题，我提出了面向Java语言的代码属性图构建方案，包括类关系图、函数别名图、精确的函数调用图。

这其中函数别名图将所有的函数实现关系进行了聚合，这样在图的层面来看，ALIAS依赖边连接了不同的gadget，从而解决了Java多态的问题。

具体的细节可以看我的毕业论文，或是直接看代码。

#### 2. 设计的代码属性图存在哪些问题？

Tabby的实现肯定会存在分析遗漏或错误的情况，但当前版本的tabby生成的代码属性图可以覆盖大多数现有的利用链，详见成果部分

从程序分析的角度，tabby的实现必然会存在可控性分析遗漏的问题，有时候遗漏会造成精确函数调用图的不精确，这部分将持续进行更新优化。

而从使用体验来看，函数别名图的使用会导致如下情况的误报
```java
class B {
    public void func(){

    }
}
class A extends B{
    public void func(){}

    public void func1(){
        A a = this.func();
    }
}
class C extends B{
    public void func(){}
}
```
假设A对象的func继承了B对象，并且重载了函数func。那么此时会出现什么问题？

首先，func1函数中会存在函数调用`func1-[:CALL]>A.func`，并且func函数存在ALIAS依赖边关系`A.func-[:ALIAS]-B.func`

那么，从图检索的角度来看，会存在这样一条通路`func1-[:CALL]>A.func-[:ALIAS]-B.func-[:ALIAS]-C.func`

但是，我们看代码，这条通路肯定是不可能的，因为A.func1实际调用的是A.func，并不存在本身对象被替换为C对象的可能。

所以此时也就造成了误报。那么怎么解决这个误报问题呢？这里就看第4个问题吧

#### 3. 我该怎么利用Tabby生成的代码属性图

Tabby生成的代码属性图实际上是由类关系图、函数别名图和精确的函数调用图组成的。它并不会直接输出类似利用链的联通路径，需要你使用相关的图查询语法进行查询而得出。

Tabby生成的代码属性图支持两种模式，一是人工判断，二是编写污点分析的自动化利用脚本。

首先，对于人工判断，利用图查询语言边查询，边人工对照具体代码来进行分析。这里其实工作量是比较大的，所以也提供了自动化的机制

然后，是自动化脚本的方式。Tabby对每一条函数调用边CALL，均计算了当前调用本身的可控性，具体参数为CALL边的POLLUTED_POSITION

举个例子，当POLLUTED_POSITION为[0,-1,-2,1]时，其中数组的index分别指向调用者本身、函数参数集等，数据的值指代的是当前受污点的变量指向

以当前例子来说明，数据的第一个位置指代的是当前函数的调用者本身的执行，当前为0，0指代调用者来自函数参数

数组第二个位置指代的是调用函数的参数的第一个参数为-1，-1指代类属性

数组第三个位置指代的是调用函数的参数的第二个参数为-2，-2指代当前位置的变量不可控

数组第四个位置指代的是调用函数的参数的第三个参数为1，1指代当前位置的变量来自函数参数的第2个

即数组内容
- -2 => 不可控
- -1 => 类属性
- 0-n => 函数参数的位置

利用这些信息，可以进行从底向上的污点分析。sink函数处提供了先验知识，通过与调用边的POLLUTED_POSITION进行比较得出当前调用是否是可控的

#### 4. 关于自动化的利用，看起来很复杂，会不会出相关的案例

对于检索出来的可联通路径，我们还需要进行进一步的判断。这里可以人工直接跟着代码去分析判断，也可以使用上面的自动化分析方案进行通路的分析（这部分也能直接解决前面函数别名图的误报问题，即提前判断下一个节点是否是允许具体实现枚举的）

当前还没有想好怎么来实现这部分的代码，可能是编写Neo4j的UDF来完成自动化利用，也可能是直接tabby实现，这里暂时TODO

#### 5. 关于性能问题
tabby本来预想的是以阶段性的方式，以分析后的基础库分析结果为基础，后续分析依赖h2数据库中的先验知识。但是实际在使用的时候h2数据库的性能还是不太行，这一部分以后有时间对构架重新编写吧

所以这里推荐的使用方式是依赖内存去进行一次性的分析：每次分析前先将上一次的分析结果(cache/graphdb.mv.db以及rules/ignore.json)删除，然后再进行代码属性图的生成。

tabby实验的时候大概6gb的内存可以处理4w+类

然后关于运行速度，tabby当前仍是以单进程顺序分析的方式进行的，且本质上分析任务是计算IO类型的，多线程是否能提高效率这里存疑。

目前，tabby对JDK 19个Jar文件（3w多个类）分析需要7分种多点（450s左右），所以可以take a cup of coffee XD

我的测试机是比较老版本的mac pro，所以以上测试数据可以作为一个参考。

#### 6. 常见报错

常见报错主要是soot产生的

- 基础类缺失，这部分可以从soot的报错信息看到具体补救方式，tabby提供了basicClasses.json用于解决这一问题
- soot getBody convert error，这个错误暂无解决方案，是soot的解析问题，只能将当前这个会报错的jar文件移除。譬如weblogic 12g的wlthint3client.jar文件会有这个问题，只能等soot更新。
- 其他由tabby产生的bug，譬如空指针异常，可以直接提issue给我并附上产生错误的jar文件。

此外，tabby主要经过了MACOX的测试，暂未在其他的平台进行测试。嗯，不确定win平台行不行（主要是获取jdk依赖的方式需要适配）。

#### 7. 使用小trick

其实，在属性图生成的过程中，许多代码分析其实是无用的，但是由于程序没办法判断是否是无用的，所以该全量分析就得全量分析。

但是，如果遇到及其消耗内存或cpu计算能力的情况（即卡在了函数处理进度处）

可以使用以下方法对分析进行优化：

1. 运行jar时加上debug，`-Dlogging.level.tabby=DEBUG`，然后看它最终在那个函数处消耗特别大或就卡在那里了
2. 打开IDEA，加lib，找到具体的实现，如果这个函数经过人工分析后，是认为可以被忽略的，那么添加至knowledge库
3. 在`knowledges.json`，添加ignore规则，比如致远的一个函数`<com.seeyon.ctp.common.parser.BytesEncodingDetect: void initialize_frequencies()>`，ignore规则如下

```json
{"name": "com.seeyon.ctp.common.parser.BytesEncodingDetect", "rules":[
    {"function": "initialize_frequencies", "type": "ignore", "actions":{}, "polluted":[], "signatures":[]}
  ]}
```

4. 添加完ignore规则后，再运行tabby就可以跳过该函数的分析

## #5 初衷&致谢

当初，在进行利用链分析的过程中，深刻认识到这一过程是能被自动化所代替的（不管是Java还是PHP）。但是，国内很少有这方面工具的开源。GI工具实际的检测效果其实并不好，为此，依据我对程序分析的理解，开发了tabby工具。我对tabby工具期望不单单只是在利用链挖掘的应用，也希望后续能从漏洞分析的角度利用tabby的代码属性图进行分析。我希望tabby能给国内的Java安全研究人员带来新的工作模式。

当然，当前版本的tabby仍然存在很多问题可以优化，希望有程序分析经验的师傅能一起加入tabby的建设当中，有啥问题可以直接联系我哦！

如果tabby给你的工作带来了便利，请不要吝啬你的🌟哦！

如果你使用tabby并挖到了漏洞，非常欢迎提供相关的成功案例XD

如果你有能力一起建设，也可以一起交流，或直接PR，或直接issue

- 优秀的静态分析框架[soot](https://github.com/soot-oss/soot)
- [gadgetinspector](https://github.com/JackOfMostTrades/gadgetinspector)
- [ysoserial](https://github.com/frohoff/ysoserial) 和 [marshalsec](https://github.com/mbechler/marshalsec)
