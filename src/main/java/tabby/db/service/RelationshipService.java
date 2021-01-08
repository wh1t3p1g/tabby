package tabby.db.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tabby.db.bean.node.*;
import tabby.db.repository.mongo.*;

import java.util.Set;

/**
 * @author wh1t3P1g
 * @since 2021/1/6
 */
@Service
public class RelationshipService {

    @Autowired
    private CallNodeRepository callNodeRepository;
    @Autowired
    private AliasNodeRepository aliasNodeRepository;
    @Autowired
    private InterfacesNodeRepository interfacesNodeRepository;
    @Autowired
    private ExtendNodeRepository extendNodeRepository;
    @Autowired
    private HasNodeRepository hasNodeRepository;

    @Async
    public <T> void insert(T ref){
        if(ref instanceof CallNode){
            callNodeRepository.insert((CallNode) ref);
        }else if(ref instanceof HasNode){
            hasNodeRepository.insert((HasNode) ref);
        }else if(ref instanceof ExtendNode){
            extendNodeRepository.insert((ExtendNode) ref);
        }else if(ref instanceof InterfacesNode){
            interfacesNodeRepository.insert((InterfacesNode) ref);
        }else if(ref instanceof AliasNode){
            aliasNodeRepository.insert((AliasNode) ref);
        }
    }

    @Async
    public void batchInsertHasNodes(Set<HasNode> ref){
        hasNodeRepository.insert(ref);
    }
    @Async
    public void batchInsertExtendNodes(Set<ExtendNode> ref){
        extendNodeRepository.insert(ref);
    }
    @Async
    public void batchInsertCallNodes(Set<CallNode> ref){
        callNodeRepository.insert(ref);
    }
    @Async
    public void batchInsertInterfaceNodes(Set<InterfacesNode> ref){
        interfacesNodeRepository.insert(ref);
    }
    @Async
    public void batchInsertAliasNodes(Set<AliasNode> ref){
        aliasNodeRepository.insert(ref);
    }

}
