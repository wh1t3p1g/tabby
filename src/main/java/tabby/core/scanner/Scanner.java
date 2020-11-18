package tabby.core.scanner;

/**
 * @author wh1t3P1g
 * @since 2020/11/17
 */
public interface Scanner<T> {


    void run(T targets);

    /**
     * 收集类信息
     * @param targets 待收集的类名/函数
     */
    void collect(T targets);

    /**
     * build relationships
     */
    void build();

    /**
     * save to cache file
     * then cache file to neo4j
     */
    void save();
}
