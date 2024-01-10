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
import tabby.config.GlobalConfiguration;
import tabby.core.Analyser;
import tabby.common.utils.FileUtils;

@Slf4j
@SpringBootApplication
@EntityScan({"tabby.common.bean"})
public class App {

    @Autowired
    private Analyser analyser;

    public static void main(String[] args) {
        if(args.length == 2 && "--config".equals(args[0])){
            GlobalConfiguration.CONFIG_FILE_PATH = FileUtils.getRealPath(args[1]);
        }
        GlobalConfiguration.init();
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
                GlobalConfiguration.initConfig();
                setLogDebugLevel();
                analyser.run();
            }catch (IllegalArgumentException e){
                log.error(e.getMessage() + ", Please check your settings.properties file.");
            }
            log.info("Done. Bye!");
        };
    }
}
