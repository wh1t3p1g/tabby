package tabby.dal.service;

import org.springframework.cache.annotation.CacheConfig;
import org.springframework.stereotype.Service;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Service
@CacheConfig(cacheNames = {"methodRef"})
public class MethodRefService {
}
