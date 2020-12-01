package tabby;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.retry.annotation.EnableRetry;
import tabby.config.SootConfiguration;
import tabby.core.Analyser;
import tabby.dal.service.MethodRefService;

import java.io.File;

@SpringBootApplication
@EnableCaching
@EnableRetry
@EntityScan("tabby.dal.bean")
@EnableNeo4jRepositories("tabby.dal.repository")
public class TabbyApplication {

    @Autowired
    private Analyser analyser;
    @Autowired
    private MethodRefService methodRefService;

    public static void main(String[] args) {
        SpringApplication.run(TabbyApplication.class, args).close();
    }

    @Bean
    CommandLineRunner run(){
        return args -> {
            SootConfiguration.initSootOption();
            String path = String.join(File.separator, System.getProperty("user.dir"), "cases", "testcases");
            analyser.runSootAnalysis(path, false);
        };
    }
}
