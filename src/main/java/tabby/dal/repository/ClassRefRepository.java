package tabby.dal.repository;

import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;
import tabby.bean.ref.ClassReference;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Repository
public interface ClassRefRepository extends Neo4jRepository<ClassReference, Long> {

    ClassReference findClassReferenceByName(String name);
}
