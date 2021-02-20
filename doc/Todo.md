实验jar包收集
maven popular top 100 81个 https://mvnrepository.com/popular?p=10 1月6日 10.58
用户数均超过3000
中间件jar包
有名框架 spring struts


扩展一个basicClass的文件
解决
Caused by: java.lang.RuntimeException: This operation requires resolving level HIERARCHY but io.netty.channel.ChannelFutureListener is at resolving level DANGLING
If you are extending Soot, try to add the following call before calling soot.Main.main(..):
Scene.v().addBasicClass(io.netty.channel.ChannelFutureListener,HIERARCHY);
