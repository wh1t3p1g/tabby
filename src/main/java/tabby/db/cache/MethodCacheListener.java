package tabby.db.cache;

import lombok.extern.slf4j.Slf4j;
import org.ehcache.event.CacheEvent;
import org.ehcache.event.CacheEventListener;
import tabby.db.bean.ref.ClassReference;

/**
 * @author wh1t3P1g
 * @since 2021/1/7
 */
@Slf4j
public class MethodCacheListener implements CacheEventListener<String, ClassReference> {
    @Override
    public void onEvent(CacheEvent<? extends String, ? extends ClassReference> cacheEvent) {
        log.info("Event '{}' fired for key '{}' with value {}", cacheEvent.getType(), cacheEvent.getKey(), cacheEvent.getNewValue());
    }
}
