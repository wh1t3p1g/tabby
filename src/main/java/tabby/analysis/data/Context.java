package tabby.analysis.data;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import tabby.analysis.container.ValueContainer;
import tabby.common.bean.ref.MethodReference;
import tabby.config.GlobalConfiguration;
import tabby.core.container.DataContainer;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 函数的域表示
 * Context起到域范围的限制，一个主域为函数的起始
 *
 * @author wh1t3P1g
 * @since 2020/11/24
 */
@Slf4j
@Data
public class Context implements AutoCloseable {

    private String methodSignature; // 当前函数签名
    private String topMethodSignature; // 递归分析时的最上层函数签名 需要继承给sub context
    private MethodReference methodReference; // 当前method reference
    private boolean interProcedural = false; // 是否开启过程间分析，默认为过程内分析 需要继承给sub context
    private Context preContext;// preContext指向之前的函数context
    private Context subContext;
    private Set<String> preMethodSignatures = new HashSet<>();

    private SimpleObject[] preObjects; // object with base and args
    private SimpleObject[] curObjects; // object with base and args
    private int depth; // 当前函数调用深度，限制无限循环的情况

    private boolean containsOutOfMemOptions = false;
    // new int[n] n可控
    // new Obj[n] n可控
    // new int[][n] n可控

    private ValueContainer container; // 需要继承给sub context
    private DataContainer dataContainer;
    private boolean forceStop = false;
    private boolean analyseTimeout = false;
    private boolean isNormalExit = true;
    private boolean isClosed = false;
    private LocalDateTime start;
    private long startTime;
    private long subTime; // 过程间分析，去除递归分析子函数所花的时间

    public Context(String methodSignature, MethodReference methodReference,
                   Context preContext, int depth, DataContainer dataContainer) {
        this.container = new ValueContainer(dataContainer);
        this.methodSignature = methodSignature;
        this.methodReference = methodReference;
        this.topMethodSignature = methodSignature;
        this.depth = depth;
        this.preContext = preContext;
        this.dataContainer = dataContainer;
        this.startTime = System.nanoTime();
        this.subTime = startTime;
        this.start = LocalDateTime.now();
        if (preContext != null) {
            this.preMethodSignatures.addAll(preContext.getPreMethodSignatures());
            this.preMethodSignatures.add(preContext.getMethodSignature());
        }
    }

    public boolean isTimeout() {
        long cost = TimeUnit.NANOSECONDS.toMinutes(System.nanoTime() - subTime);
        boolean flag = cost >= GlobalConfiguration.METHOD_TIMEOUT;
        if (flag && !analyseTimeout) { // 只打印最底层的timeout函数分析
            log.error("Method {} analysis timeout, cost {} min, plz check!", methodSignature, cost);
            methodReference.incrementTimeoutTimes();
        }
        return flag;
    }

    public long cost() {
        return System.nanoTime() - startTime;
    }

    public long getSeconds() {
        return Duration.between(start, LocalDateTime.now()).getSeconds();
    }

    public void sub(long cost) {
        subTime += cost;
    }

    public static Context newInstance(
            String methodSignature,
            MethodReference methodReference,
            DataContainer dataContainer) {
        Context context = new Context(methodSignature, methodReference, null, 0, dataContainer);
        int size = Integer.max(0, methodReference.getParameterSize()) + 1;
        context.curObjects = new SimpleObject[size];
        return context;
    }

    /**
     * 创建一个子函数域
     */
    public Context createSubContext(MethodReference methodReference) {
        Context subContext =
                new Context(methodReference.getSignature(), methodReference,
                        this, depth + 1, dataContainer);
        subContext.setContainer(container.createSubContainer());
        subContext.setTopMethodSignature(topMethodSignature);
        subContext.setInterProcedural(interProcedural);
        this.subContext = subContext;
        return subContext;
    }

    public void accept(ValueContainer valueContainer) {
        this.container = valueContainer;
    }

    /**
     * 防止函数进入递归调用
     * 取消采用递归的方式来判断，以内存损耗来代替递归判断
     *
     * @param invokeSignature
     * @return
     */
    public boolean isInRecursion(String invokeSignature) {
        if (invokeSignature.equals(methodSignature)) {
            return true;
        }

        return preMethodSignatures != null && preMethodSignatures.contains(invokeSignature);
    }

    public boolean isOverMaxDepth() {
        return depth + 1 >= GlobalConfiguration.METHOD_MAX_DEPTH;
    }

    @Override
    public void close() {
        // 清除引用
        methodReference = null;
        preContext = null;
        container.clear();
        preMethodSignatures.clear();
        preObjects = null;
        curObjects = null;
        dataContainer = null;
        isClosed = true;
    }

    public boolean isTopContext() {
        return preContext == null;
    }

    public void setAnalyseTimeout(boolean analyseTimeout) {
        this.analyseTimeout = analyseTimeout;

        if (subContext != null && !subContext.isClosed() && !subContext.isAnalyseTimeout()) {
            subContext.setAnalyseTimeout(analyseTimeout);
        }

        if(preContext != null && !preContext.isClosed() && !preContext.isAnalyseTimeout()) {
            preContext.setAnalyseTimeout(analyseTimeout);
        }
    }

    @Override
    public String toString() {
        return methodSignature;
    }
}
