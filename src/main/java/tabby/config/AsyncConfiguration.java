package tabby.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * @author wh1t3P1g
 * @since 2021/4/23
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfiguration {

    public static int CORE_POOL_SIZE = -1;

    @Bean("tabby-collector")
    public Executor master() {
        int corePoolSize = (int) (CORE_POOL_SIZE / 0.8);
        int maxPoolSize = (int) (CORE_POOL_SIZE / 0.6);
        ThreadPoolTaskExecutor executor = makeExecutor(corePoolSize, maxPoolSize,"tabby-collector");
        GlobalConfiguration.tabbyCollectorExecutor = executor;
        return executor;
    }

    @Bean("tabby-saver")
    public Executor saver() {
        int poolSize = min(max(CORE_POOL_SIZE/2, 2), 4);
        int corePoolSize = poolSize+1;
        int maxPoolSize = poolSize+2;
        ThreadPoolTaskExecutor executor = makeExecutor(corePoolSize, maxPoolSize, "tabby-saver");
        GlobalConfiguration.tabbySaverExecutor = executor;
        return executor;
    }

    private ThreadPoolTaskExecutor makeExecutor(int corePoolSize, int maxPoolSize, String prefix){
        log.info("Open {} size for thread pool {}", corePoolSize, prefix);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(maxPoolSize * 1000);
        executor.setKeepAliveSeconds(300);
        executor.setThreadNamePrefix(prefix+"-");
        executor.setAwaitTerminationSeconds(1);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
