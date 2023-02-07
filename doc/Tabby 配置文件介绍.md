## #1 总览

Tabby >= 1.1.0 版本后，支持使用配置文件的方式来分析文件（原命令行方式已失效）。
```properties
# enables  
tabby.build.enable                        = true  
tabby.load.enable                         = true  
tabby.cache.compress                      = false  
# debug  
tabby.debug.details                       = false  
  
# jdk settings  
tabby.build.isJDKProcess                  = true  
tabby.build.withAllJDK                    = true  
tabby.build.excludeJDK                    = false  
tabby.build.isJDKOnly                     = false  
  
# dealing fatjar  
tabby.build.checkFatJar                   = true  
  
# pointed-to analysis  
tabby.build.isFullCallGraphCreate         = false  
tabby.build.thread.timeout                = 2  
tabby.build.isNeedToCreateIgnoreList      = false  
  
# targets to analyse  
tabby.build.target                        = cases/commons-collections-3.1.jar  
tabby.build.libraries                     = libs  
tabby.build.mode                          = gadget  
  
# db settings  
tabby.cache.directory                     = ./cache
tabby.cache.db.filename                   = dev
tabby.cache.isDockerImportPath            = false
tabby.cache.auto.remove                   = true
tabby.cache.compress.times                = 1
  
tabby.neo4j.username                      = neo4j  
tabby.neo4j.password                      = password  
tabby.neo4j.url                           = bolt://127.0.0.1:7687
```

后面将详细讲解需要改变的配置文件（没有讲到的配置，正常不需要改变配置内容）

## #2 详细介绍

#### 开启 tabby 功能项
```properties
# enables
tabby.build.enable                        = true # 是否开启代码属性图生成
tabby.load.enable                         = true # 是否开启图导入到 neo4j
```
由于是两个独立阶段，所以可以自定义开启不同阶段

#### 配置 jdk 依赖是否参与分析
```properties
# jdk settings
tabby.build.isJDKProcess                  = false # 分析过程是否加入基础的2个jdk依赖
tabby.build.withAllJDK                    = false # 分析过程是否加入全量的jdk依赖
tabby.build.excludeJDK                    = false # 分析过程是否剔除当前运行环境的jdk依赖，此时target目录需要提供需要分析的jdk依赖
tabby.build.isJDKOnly                     = false # 分析过程是否仅分析jdk依赖，不会去分析target目录下的文件
```

#### 配置分析目标

```properties
# targets to analyse
tabby.build.target                        = cases/java-sec-code-1.0.0.jar # 给定待分析目标，可以是文件夹也可以是单个文件
tabby.build.libraries                     = libs # 不需要全量分析的依赖文件目录，加快分析速度
tabby.build.mode                          = web # 分析类型 web 或 gadget，web模式会剔除常见jar包的全量分析，gadget模式会对target目录下的文件进行全量分析
```

#### 配置数据库

```properties
# db settings
tabby.cache.directory                     = ./cache # cache 目录
tabby.cache.db.filename                   = dev # h2 database 名
tabby.cache.isDockerImportPath            = false # 运行环境是否为docker环境
tabby.cache.auto.remove                   = true  # 运行前删除原有db文件
tabby.cache.compress.times                = 1     # 选择 db 文件压缩次数

tabby.neo4j.username                      = neo4j # neo4j 用户名
tabby.neo4j.password                      = password # neo4j 密码
tabby.neo4j.url                           = bolt://127.0.0.1:7687 # neo4j url
```
如果 neo4j 为 docker 环境，则`directory`固定为`./env/import`目录

## #3 常见配置场景
下面没有提及到的配置，保持默认即可
##### 利用链挖掘
挖掘 jdk 里面的利用链
```properties
# enables
tabby.build.enable                        = true
tabby.load.enable                         = true

tabby.build.isJDKOnly                     = true

tabby.build.mode                          = gadget
```
挖掘 target 目录文件的利用链
```properties
# enables
tabby.build.enable                        = true
tabby.load.enable                         = true

tabby.build.isJDKProcess                  = true
# targets to analyse
tabby.build.target                        = target
tabby.build.libraries                     = libs
tabby.build.mode                          = gadget
```

##### 常见漏洞挖掘
```properties
# enables
tabby.build.enable                        = true
tabby.load.enable                         = true

# targets to analyse
tabby.build.target                        = target
tabby.build.libraries                     = libs
tabby.build.mode                          = web
```