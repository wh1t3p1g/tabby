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
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import sun.misc.Signal;
import tabby.common.utils.FileUtils;
import tabby.config.GlobalConfiguration;
import tabby.core.Analyser;

@Slf4j
@SpringBootApplication
@EnableScheduling
@EntityScan({"tabby.common.bean"})
public class App {

    @Autowired
    private Analyser analyser;

    public static void main(String[] args) {
        if (args.length == 2 && "--config".equals(args[0])) {
            GlobalConfiguration.CONFIG_FILE_PATH = FileUtils.getRealPath(args[1]);
        }
        GlobalConfiguration.init();
        SpringApplication.run(App.class, args).close();
    }

    public void setJavaHome() {
        // set java home
        if (GlobalConfiguration.TARGET_JAVA_HOME == null || !GlobalConfiguration.IS_USING_SETTING_JRE) {
            String javaHome = System.getProperty("java.home");
            if (javaHome == null) {
                javaHome = System.getenv("JAVA_HOME");
            }
            if (javaHome != null) {
                GlobalConfiguration.TARGET_JAVA_HOME = javaHome;
            }
        }

        log.info("Target java.home: " + GlobalConfiguration.TARGET_JAVA_HOME);
    }

    public void setLogDebugLevel() {
        LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        if (GlobalConfiguration.DEBUG) {
            loggerContext.getLogger("tabby").setLevel(Level.DEBUG);
        }
    }

    @Bean
    CommandLineRunner run() {
        return args -> {
            try {
                GlobalConfiguration.initConfig();
                setJavaHome();
                setLogDebugLevel();
                Signal.handle(new Signal("INT"),  // SIGINT
                        signal -> {
                            log.error("Force Stop by control+c");
                            stopThreads(GlobalConfiguration.tabbyCollectorExecutor);
                            stopThreads(GlobalConfiguration.tabbySaverExecutor);
                            System.exit(0);
                        });
                analyser.run();
            } catch (IllegalArgumentException e) {
                log.error(e.getMessage() + ", Please check your settings.properties file.");
            }

            if (GlobalConfiguration.GLOBAL_FORCE_STOP) {
                log.info("OOM ERROR!");
            } else {
                log.info("Done. Bye!");
            }
        };
    }

    public static void stopThreads(ThreadPoolTaskExecutor executor) {
        if (executor != null && executor.getActiveCount() > 0) {
            executor.shutdown();
        }
    }
}
