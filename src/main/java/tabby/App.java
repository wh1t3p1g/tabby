package tabby;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;
import tabby.config.GlobalConfiguration;
import tabby.core.Analyser;

@Slf4j
@SpringBootApplication
@EntityScan({"tabby.dal.caching.bean","tabby.dal.neo4j.entity"})
@EnableNeo4jRepositories("tabby.dal.neo4j.repository")
public class App {

    @Autowired
    private Analyser analyser;

    public static void main(String[] args) {
        GlobalConfiguration.initConfig();
        SpringApplication.run(App.class, args).close();
    }

    public void setLogDebugLevel(){
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        if(GlobalConfiguration.DEBUG) {
            loggerContext.getLogger("tabby").setLevel(Level.DEBUG);
        }
    }

    @Bean
    CommandLineRunner run(){
        return args -> {
            try{
                setLogDebugLevel();
                analyser.run();
            }catch (IllegalArgumentException e){
                log.error(e.getMessage() + ", Please check your settings.properties file.");
            }
//            catch (JDKVersionErrorException e){
//                log.error(e.getMessage());
//            }
            log.info("Done. Bye!");
//            System.exit(0);
        };
    }
}
