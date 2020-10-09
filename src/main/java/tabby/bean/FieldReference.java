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
@NodeEntity(label="Field")
public class FieldReference {

    @Id
    @GeneratedValue
    private Long id;

    private String name;

    private int modifiers;

    private String type; // 类型

}
