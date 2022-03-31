package tabby;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import tabby.config.GlobalConfiguration;
import tabby.core.Analyser;
import tabby.exception.JDKVersionErrorException;
import tabby.util.ArgumentEnum;
import tabby.util.FileUtils;
import tabby.util.JavaVersion;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Properties;

@Slf4j
@SpringBootApplication
@EntityScan({"tabby.dal.caching.bean","tabby.dal.neo4j.entity"})
@EnableNeo4jRepositories("tabby.dal.neo4j.repository")
public class App {

    @Autowired
    private Analyser analyser;

    private Properties props = new Properties();

    public static void main(String[] args) {
        SpringApplication.run(App.class, args).close();
    }

    private void applyOptions() {
        boolean isJDKOnly = "true".equals(props.getProperty(ArgumentEnum.IS_JDK_ONLY.toString(), "false"));

        if(isJDKOnly){
            props.setProperty(ArgumentEnum.WITH_ALL_JDK.toString(), "true");
        }

        GlobalConfiguration.DEBUG = "true".equals(props.getProperty(ArgumentEnum.SET_DEBUG_ENABLE.toString(), "false"));
        GlobalConfiguration.IS_FULL_CALL_GRAPH_CONSTRUCT = "true".equals(props.getProperty(ArgumentEnum.IS_FULL_CALL_GRAPH_CREATE.toString(), "false"));

        String target = props.getProperty(ArgumentEnum.TARGET.toString());

        // 支持绝对路径 issue 7
        if(!isJDKOnly && target != null && !FileUtils.fileExists(target)){
            target = String.join(File.separator, System.getProperty("user.dir"), target);
            if(!FileUtils.fileExists(target)){
                throw new IllegalArgumentException("target not exists!");
            }
        }

        String libraries = props.getProperty(ArgumentEnum.LIBRARIES.toString());
        if(libraries != null){
            if(FileUtils.fileExists(libraries)){
                GlobalConfiguration.LIBS_PATH = libraries;
            }else{
                libraries = String.join(File.separator, System.getProperty("user.dir"), libraries);
                if(FileUtils.fileExists(libraries)){
                    GlobalConfiguration.LIBS_PATH = libraries;
                }
            }
        }
    }

    private void loadProperties(String filepath){
        try(Reader reader = new FileReader(filepath)){
            props.load(reader);
        } catch (IOException e) {
            throw new IllegalArgumentException("Settings.properties file not found!");
        }
    }

    @Bean
    CommandLineRunner run(){
        return args -> {
            try{
                if(!JavaVersion.isJDK8()){
                    throw new JDKVersionErrorException("Error JDK version. Please using JDK8.");
                }
                loadProperties("config/settings.properties");
                applyOptions();
                analyser.run(props);
                log.info("Done. Bye!");
                System.exit(0);
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
