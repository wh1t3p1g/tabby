package tabby;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
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
import tabby.util.FileUtils;

import javax.annotation.Resource;
import java.io.File;

@Slf4j
@SpringBootApplication
@EnableCaching
@EnableRetry
@EntityScan("tabby.neo4j.bean")
@EnableNeo4jRepositories("tabby.neo4j.repository")
public class TabbyApplication {

    @Autowired
    private Analyser analyser;

    private String target;

    private boolean isJDKOnly = false;

    @Resource
    private ApplicationArguments arguments;

    public static void main(String[] args) {
        SpringApplication.run(TabbyApplication.class, args).close();
    }

    @Bean
    CommandLineRunner run(){
        return args -> {
            try{
                if(arguments.containsOption("isJDKOnly")){
                    isJDKOnly = true;
                }
                if(!isJDKOnly && arguments.getNonOptionArgs().size() != 1){
                    throw new IllegalArgumentException("target not set!");
                }
                SootConfiguration.initSootOption();
                if(isJDKOnly){
                    analyser.runSootAnalysis(null, isJDKOnly);
                }else{
                    target = arguments.getNonOptionArgs().get(0);
                    String path = String.join(File.separator, System.getProperty("user.dir"), target);
                    if(FileUtils.fileExists(path)){
                        analyser.runSootAnalysis(path, isJDKOnly);
                    }else{
                        throw new IllegalArgumentException("target not exists!");
                    }
                }
            }catch (IllegalArgumentException e){
                log.error(e.getMessage() +
                        "\nPlease use java -jar tabby target_directory [--isJDKOnly] !" +
                        "\ntarget_directory 为相对路径" +
                        "\n--isJDKOnly出现时，仅处理JDK的内容" +
                        "\nExample: java -jar tabby cases/jars");
            }

        };
    }
}
