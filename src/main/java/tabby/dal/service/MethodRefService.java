package tabby.dal.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tabby.common.bean.ref.MethodReference;
import tabby.dal.repository.MethodRepository;

import java.util.concurrent.CompletableFuture;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Slf4j
@Service
public class MethodRefService {

    @Autowired
    private MethodRepository methodRepository;

    public MethodReference getMethodRefBySignature(String signature) {
        return methodRepository.findMethodReferenceBySignature(signature);
    }

    public void save(MethodReference ref) {
        methodRepository.save(ref);
    }

    @Async("tabby-saver")
    public CompletableFuture<Boolean> save(Iterable<MethodReference> refs) {
        methodRepository.saveAll(refs);
        return CompletableFuture.completedFuture(true);
    }

    public void save2Csv(String filepath) {
        methodRepository.save2Csv(filepath);
    }

    public int countAll() {
        return methodRepository.countAll();
    }
}
