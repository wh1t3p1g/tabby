package tabby.dal.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tabby.common.bean.edge.Alias;
import tabby.common.bean.edge.Call;
import tabby.common.bean.edge.Extend;
import tabby.common.bean.edge.Interfaces;
import tabby.dal.repository.*;

/**
 * @author wh1t3P1g
 * @since 2021/1/8
 */
@Service
@Slf4j
public class RelationshipsService {

    @Autowired
    private AliasEdgeRepository aliasEdgeRepository;
    @Autowired
    private CallEdgeRepository callEdgeRepository;
    @Autowired
    private ExtendEdgeRepository extendEdgeRepository;
    @Autowired
    private HasEdgeRepository hasEdgeRepository;
    @Autowired
    private InterfacesEdgeRepository interfacesEdgeRepository;

    public void saveHasEdge(String id, String classId, String methodId) {
        hasEdgeRepository.save(id, classId, methodId);
    }

    public void saveCallEdge(Call edge) {
        callEdgeRepository.save(edge);
    }

    public void saveInterfaceEdge(Interfaces edge) {
        interfacesEdgeRepository.save(edge.getId(), edge.getSource(), edge.getTarget());
    }

    public void saveExtendEdge(Extend edge) {
        extendEdgeRepository.save(edge.getId(), edge.getSource(), edge.getTarget());
    }

    public void saveAliasEdge(Alias edge) {
        aliasEdgeRepository.save(edge.getId(), edge.getSource(), edge.getTarget());
    }

    public void saveAllCallEdges(Iterable<Call> edges) {
        callEdgeRepository.saveAll(edges);
    }

    public void saveAliasToCsv(String filepath) {
        aliasEdgeRepository.save2Csv(filepath);
    }

    public void saveHasToCsv(String filepath) {
        hasEdgeRepository.save2Csv(filepath);
    }

    public void saveExtendToCsv(String filepath) {
        extendEdgeRepository.save2Csv(filepath);
    }

    public void saveCallToCsv(String filepath) {
        callEdgeRepository.save2Csv(filepath);
    }

    public void saveInterfaceToCsv(String filepath) {
        interfacesEdgeRepository.save2Csv(filepath);
    }

    public void count() {
        long has = hasEdgeRepository.count();
        long call = callEdgeRepository.count();
        long extend = extendEdgeRepository.count();
        long alias = aliasEdgeRepository.count();
        long interfaces = interfacesEdgeRepository.count();
        long edges = has + call + extend + alias + interfaces;
        log.info("Total {}, has count: {}, call count: {}, extend count: {}, alias count: {}, interfaces count: {}",
                edges, has, call, extend, alias, interfaces);
    }
}
