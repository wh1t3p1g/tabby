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
import tabby.config.GlobalConfiguration;
import tabby.core.Analyser;
import tabby.exception.JDKVersionErrorException;
import tabby.util.FileUtils;
import tabby.util.JavaVersion;

import javax.annotation.Resource;
import java.io.File;

@Slf4j
@SpringBootApplication
@EntityScan({"tabby.dal.caching.bean","tabby.dal.neo4j.entity"})
@EnableNeo4jRepositories("tabby.dal.neo4j.repository")
public class App {

    private String target = null;

    private boolean isJDKProcess = false;
    private boolean withAllJDK = false;
    private boolean isSaveOnly = false;
    private boolean excludeJDK = false;
    private boolean isJDKOnly = false;
    private boolean checkFatJar = false;

    @Autowired
    private Analyser analyser;

    @Resource
    private ApplicationArguments arguments;

    public static void main(String[] args) {
        SpringApplication.run(App.class, args).close();
    }

    private void applyOptions() {
        if (arguments.containsOption("isJDKProcess")) {
            isJDKProcess = true;
        }
        if (arguments.containsOption("withAllJDK") || arguments.containsOption("isJDKOnly")) {
            withAllJDK = true;
        }
        if (arguments.containsOption("vv")) {
            GlobalConfiguration.DEBUG = true;
        }
        if (arguments.containsOption("isSaveOnly")) {
            isSaveOnly = true;
        }
        if (arguments.containsOption("excludeJDK")) {
            excludeJDK = true;
        }
        if (arguments.containsOption("isJDKOnly")) {
            isJDKOnly = true;
        }
        if (arguments.containsOption("checkFatJar")){
            checkFatJar = true;
        }
        if(arguments.getNonOptionArgs().size() == 1){
            target = arguments.getNonOptionArgs().get(0);
            // 支持绝对路径 issue 7
            if(!FileUtils.fileExists(target)){
                target = String.join(File.separator, System.getProperty("user.dir"), target);
                if(!FileUtils.fileExists(target)){
                    throw new IllegalArgumentException("target not exists!");
                }
            }
        }
        // check options
        if(isJDKOnly || isSaveOnly){
            // only process JDK dependencies
            // only save caches
        }else if(target != null){
            // process target JAR/WAR/CLASS
        }else{
            throw new IllegalArgumentException("Options Illegal!");
        }
    }

    @Bean
    CommandLineRunner run(){
        return args -> {
            try{
                if(!JavaVersion.isJDK8()){
                    throw new JDKVersionErrorException("Error JDK version. Please using JDK8.");
                }
                applyOptions();
                analyser.run(target, isJDKProcess, withAllJDK, isSaveOnly, excludeJDK, isJDKOnly, checkFatJar);
            }catch (IllegalArgumentException e){
                log.error(e.getMessage() +
                        "\nPlease use java -jar tabby target_directory [--isJDKOnly｜--isJDKProcess|--isSaveOnly|--excludeJDK] !" +
                        "\ntarget_directory 为相对路径" +
                        "\n--isJDKOnly出现时，仅处理JDK的内容" +
                        "\n--excludeJDK出现时，不添加当前运行jre环境" +
                        "\n--isJDKProcess出现时，将处理当前运行jre环境的分析" +
                        "\nExample: java -jar tabby cases/jars --isJDKProcess" +
                        "\nOthers: https://github.com/wh1t3p1g/tabby/wiki/Tabby%E9%A3%9F%E7%94%A8%E6%8C%87%E5%8C%97");
            }catch (JDKVersionErrorException e){
                log.error(e.getMessage());
            }

        };
    }
}
