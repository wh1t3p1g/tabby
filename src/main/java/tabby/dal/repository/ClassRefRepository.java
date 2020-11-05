package tabby.dal.repository;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;
import tabby.dal.bean.ref.ClassReference;

import java.util.UUID;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Repository
public interface ClassRefRepository extends Neo4jRepository<ClassReference, UUID> {

    ClassReference findClassReferenceByName(String name);
}
