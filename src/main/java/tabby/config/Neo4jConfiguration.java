package tabby.config;

import org.neo4j.driver.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static tabby.config.GlobalConfiguration.*;

/**
 * @author wh1t3p1g
 * @since 2022/9/5
 */
@Configuration
public class Neo4jConfiguration {

    @Bean
    public Driver driver() {
        AuthToken authToken = AuthTokens.basic(NEO4J_USERNAME, NEO4J_PASSWORD);
        Config config = Config.builder().build();
        return GraphDatabase.driver(NEO4J_URL, authToken, config);
    }
}
