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
