package tabby.core.discover;

import tabby.neo4j.bean.ref.MethodReference;

import java.util.List;

/**
 * @author wh1t3P1g
 * @since 2020/11/21
 */
public interface Discover {

    /**
     * 获取所有的节点，填充到startNodes里
     */
    List<String> startNodes();

    /**
     * 判断当前函数节点是否为 最后的节点，可以是source函数 也可以是sink函数
     * @param methodRef 待检测函数
     * @return 是否为最后的节点
     */
    boolean isEndNodes(MethodReference methodRef);

    /**
     * 主逻辑
     */
    void run();

    /**
     * 对当前函数做限制
     * 比如 需要满足某些条件 才能继续往下走
     * 类似 当前函数的类 必须是实现serializable类的
     * @param target 待检测函数
     * @return 是否需要继续往下走 true则不允许往下走，false则允许往下走
     */
    boolean constraint(MethodReference target);

    /**
     * 以当前target为起点，找到所有可控的调用关系
     * @param target 起点函数
     * @return 返回所有可控的调用关系
     */
    List<String> spread(MethodReference target);

    /**
     * 检查当前两个函数之间的边是否可控
     * @param source
     * @param target
     * @return
     */
    boolean check(MethodReference source, MethodReference target);
}
