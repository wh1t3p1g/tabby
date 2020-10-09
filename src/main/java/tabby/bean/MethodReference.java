package tabby.bean;

import lombok.Data;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;

/**
 * @author wh1t3P1g
 * @since 2020/10/9
 */
@Data
@NodeEntity(label="Method")
public class MethodReference {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    private String signature;

    private boolean isStatic;

    private ClassReference.Handle classRef;

    // 后续添加分析后的数据字段
}
