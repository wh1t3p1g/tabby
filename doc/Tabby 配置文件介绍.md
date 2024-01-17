## #1 总览

Tabby >= 1.1.0 版本后，支持使用配置文件的方式来分析文件（原命令行方式已失效）。
```properties
# need to modify
tabby.build.target                        = cases/commons-collections-3.2.1.jar
tabby.build.libraries                     = libs
tabby.build.mode                          = gadget
tabby.output.directory                    = ./output/dev

# debug
tabby.debug.details                       = false

# jdk settings
tabby.build.isJDKProcess                  = false
tabby.build.withAllJDK                    = false
tabby.build.excludeJDK                    = false
tabby.build.isJDKOnly                     = false

# dealing fatjar
tabby.build.checkFatJar                   = true

# pointed-to analysis
tabby.build.isFullCallGraphCreate         = false
tabby.build.thread.timeout                = 2
tabby.build.isNeedToCreateIgnoreList      = false
```

后面将详细讲解需要改变的配置文件（没有讲到的配置，正常不需要改变配置内容）

## #2 详细介绍

#### 配置 jdk 依赖是否参与分析
```properties
# jdk settings
tabby.build.isJRE9Module                  = false # 指代下述javaHome版本是否 >= 9
tabby.build.javaHome                      = /Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home
tabby.build.isJDKProcess                  = false # 分析过程是否加入基础的2个jdk依赖
tabby.build.withAllJDK                    = false # 分析过程是否加入全量的jdk依赖
tabby.build.isJDKOnly                     = false # 分析过程是否仅分析jdk依赖，不会去分析target目录下的文件
```
如果 javaHome 所指向的路径是 jdk >= 9, 第一次生成jre_libs目录会花一点时间，请耐心等待。

#### 配置分析目标

```properties
# targets to analyse
tabby.build.target                        = cases/java-sec-code-1.0.0.jar # 给定待分析目标，可以是文件夹也可以是单个文件
tabby.build.libraries                     = libs # 不需要全量分析的依赖文件目录，加快分析速度
tabby.build.mode                          = web # 分析类型 web 或 gadget，web模式会剔除常见jar包的全量分析，gadget模式会对target目录下的文件进行全量分析
```

## #3 常见配置场景
下面没有提及到的配置，保持默认即可
##### 利用链挖掘
挖掘 jdk 里面的利用链
```properties
# enables
tabby.build.isJDKOnly                     = true
tabby.build.mode                          = gadget
```
挖掘 target 目录文件的利用链
```properties
tabby.build.isJDKProcess                  = true
# targets to analyse
tabby.build.target                        = target
tabby.build.libraries                     = libs
tabby.build.mode                          = gadget
```

##### 常见漏洞挖掘
```properties
# targets to analyse
tabby.build.target                        = target
tabby.build.libraries                     = libs
tabby.build.mode                          = web
```