package tabby.core.discover;

import tabby.core.data.GadgetChain;
import tabby.neo4j.bean.ref.MethodReference;

/**
 * @author wh1t3P1g
 * @since 2020/11/21
 */
public interface Discover {

    /**
     * 获取所有sink函数的signature
     */
    void getSinks();

    /**
     * 获取所有source函数的subSignature
     */
    void getSources();

    /**
     * 证明source 调用 target 是可控的
     * @param source
     * @param target
     * @return 是否可控
     */
    boolean analysis(String position, GadgetChain gadgetChain, MethodReference source, MethodReference target);

    /**
     * 主逻辑
     */
    void run();

    /**
     * 继续往下进行探索
     * 采用递归的方式，会去遍历所有路径
     * @param gadgetChain
     */
    void flowThrough(GadgetChain gadgetChain);

    /**
     * 针对当前的method，获取所有可控的函数调用
     * @param gadgetChain
     */
    void next(GadgetChain gadgetChain);

    /**
     * 判断当前函数是否为source函数
     * @param method
     * @return
     */
    boolean isSource(String method);
}
