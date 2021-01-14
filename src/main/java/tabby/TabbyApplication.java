package tabby;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import org.springframework.retry.annotation.EnableRetry;
import tabby.config.SootConfiguration;
import tabby.core.Analyser;
import tabby.util.FileUtils;

import javax.annotation.Resource;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@SpringBootApplication
@EnableRetry
@EntityScan("tabby.db.bean")
@EnableNeo4jRepositories("tabby.db.repository.neo4j")
public class TabbyApplication {

    @Autowired
    private Analyser analyser;

    private String target;

    private boolean isJDKProcess = false;

    @Resource
    private ApplicationArguments arguments;

    public static void main(String[] args) {
        SpringApplication.run(TabbyApplication.class, args).close();
    }

    @Bean
    CommandLineRunner run(){
        return args -> {
            try{
                if(arguments.containsOption("isJDKProcess")){
                    isJDKProcess = true;
                }
                if(arguments.containsOption("isSaveOnly")){
                    analyser.save();
                    return;
                }

                Map<String, String> jdkDependencies = analyser.getJdkDependencies();
                Map<String, String> classpaths = null;
                if(arguments.containsOption("excludeJDK")){
                    classpaths = new HashMap<>();
                }else{
                    classpaths = new HashMap<>(jdkDependencies);
                }
                Map<String, String> targets = new HashMap<>();

                if(arguments.containsOption("isJDKOnly")){
                    targets.putAll(jdkDependencies);
                }else if(arguments.getNonOptionArgs().size() == 1){
                    target = arguments.getNonOptionArgs().get(0);
                    String path = String.join(File.separator, System.getProperty("user.dir"), target);
                    if(!FileUtils.fileExists(path)){
                        throw new IllegalArgumentException("target not exists!");
                    }
                    Map<String, String> files = FileUtils.getTargetDirectoryJarFiles(path);
                    classpaths.putAll(files);
                    targets.putAll(files);
                    if(isJDKProcess){
                        targets.putAll(jdkDependencies);
                    }
                }else{
                    throw new IllegalArgumentException("target not set!");
                }

                SootConfiguration.initSootOption();
                analyser.runSootAnalysis(targets, new ArrayList<>(classpaths.values()) );
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
