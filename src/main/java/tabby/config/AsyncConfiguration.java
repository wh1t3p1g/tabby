package tabby.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

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

    private ThreadPoolTaskExecutor makeExecutor(int corePoolSize, int maxPoolSize, String prefix){
        log.info("Open {} size for thread pool {}", corePoolSize, prefix);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(maxPoolSize * 1000);
        executor.setKeepAliveSeconds(300);
        executor.setAwaitTerminationSeconds(1);
        executor.setThreadNamePrefix(prefix+"-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
