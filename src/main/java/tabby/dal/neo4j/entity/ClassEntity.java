package tabby.dal.neo4j.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.Node;


/**
 * @author wh1t3P1g
 * @since 2021/4/25
 */
@Node("Class")
public class ClassEntity {
    @Id
    private String id;
}
