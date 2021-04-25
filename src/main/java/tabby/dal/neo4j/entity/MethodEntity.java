package tabby.dal.neo4j.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.neo4j.core.schema.Node;

/**
 * @author wh1t3P1g
 * @since 2021/4/25
 */
@Node("Method")
public class MethodEntity {

    @Id
    private String id;
}
