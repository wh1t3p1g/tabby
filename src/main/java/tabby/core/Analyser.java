package tabby.core;

import com.google.common.collect.ImmutableSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import soot.CompilationDeathException;
import soot.Main;
import soot.Scene;
import soot.options.Options;
import tabby.analysis.data.Context;
import tabby.common.utils.FileUtils;
import tabby.config.GlobalConfiguration;
import tabby.config.SootConfiguration;
import tabby.core.collector.FileCollector;
import tabby.core.container.DataContainer;
import tabby.core.container.RulesContainer;
import tabby.core.scanner.CallGraphScanner;
import tabby.core.scanner.ClassInfoScanner;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static soot.SootClass.HIERARCHY;

/**
 * @author wh1t3P1g
 * @since 2020/10/10
 */
@Slf4j
@Component
public class Analyser {

    @Autowired
    private DataContainer dataContainer;
    @Autowired
    private ClassInfoScanner classInfoScanner;
    @Autowired
    private CallGraphScanner callGraphScanner;

    @Autowired
    private RulesContainer rulesContainer;
    @Autowired
    private FileCollector fileCollector;


    public void run() throws IOException {
        Map<String, String> dependencies = fileCollector.collectJdkDependencies();

        log.info("Get {} JDK dependencies", dependencies.size());
        log.info("Try to collect all targets");

        Map<String, String> cps = new HashMap<>(dependencies);
        Map<String, String> targets = new HashMap<>();
        // 收集目标
        GlobalConfiguration.rulesContainer = rulesContainer;
        if(!GlobalConfiguration.IS_JDK_ONLY){
            log.info("Target: {}", GlobalConfiguration.TARGET);
            Map<String, String> files = fileCollector.collect(GlobalConfiguration.TARGET);
            cps.putAll(files);
            targets.putAll(files);
        }

        if(GlobalConfiguration.IS_JDK_ONLY
                || GlobalConfiguration.IS_JDK_PROCESS){
            targets.putAll(dependencies);
        }

        // 添加必要的依赖，防止信息缺失，比如servlet依赖
        if(FileUtils.fileExists(GlobalConfiguration.LIBS_PATH)){
            Map<String, String> files = fileCollector.collect(GlobalConfiguration.LIBS_PATH);
            GlobalConfiguration.libraries.putAll(files);
        }

        for(Map.Entry<String, String> entry:GlobalConfiguration.libraries.entrySet()){
            cps.putIfAbsent(entry.getKey(), entry.getValue());
        }

        runSootAnalysis(targets, new ArrayList<>(cps.values()));

        if(!GlobalConfiguration.GLOBAL_FORCE_STOP){
            // 仅当OOM未发生时，保存当前结果到CSV文件
            dataContainer.count();
            // output
            dataContainer.save2CSV();
        }
    }

    public void runSootAnalysis(Map<String, String> targets, List<String> classpaths){
        try{
            SootConfiguration.initSootOption();
            addBasicClasses();
            log.info("Load basic classes");
            if(GlobalConfiguration.IS_USING_SETTING_JRE){
                Scene.v().setSootClassPath(String.join(File.pathSeparator, classpaths));
            }
            Scene.v().loadBasicClasses();
            log.info("Load dynamic classes");
            Scene.v().loadDynamicClasses();
            Scene.v().setSootClassPath(String.join(File.pathSeparator, new HashSet<>(classpaths)));
            // get target filepath
            List<String> realTargets = getTargets(targets);
            if(realTargets.isEmpty()){
                log.info("Nothing to analysis!");
                return;
            }
            Main.v().autoSetOptions();
            log.info("Target {}, Dependencies {}", realTargets.size(), classpaths.size());
            long start = System.nanoTime();
            // 类信息抽取
            classInfoScanner.run(realTargets);
            // 全量函数调用图构建
            callGraphScanner.run();

            rulesContainer.saveStatus();
            long time = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start);
            log.info("Total cost {} min {} seconds."
                    , time/60, time%60);
//            if (!Options.v().oaat()) {
//                PackManager.v().writeOutput();
//            }
        }catch (CompilationDeathException e){
            if (e.getStatus() != CompilationDeathException.COMPILATION_SUCCEEDED) {
                throw e;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<String> getTargets(Map<String, String> targets){
        Set<String> stuff = new HashSet<>();
        List<String> newIgnore = new ArrayList<>();
        targets.forEach((filename, filepath) -> {
            if(!rulesContainer.isIgnore(filename)){
                stuff.add(filepath);
                newIgnore.add(filename);
            }
        });
        rulesContainer.getIgnored().addAll(newIgnore);
        log.info("Total analyse {} targets.", stuff.size());
        Options.v().set_process_dir(new ArrayList<>(stuff));
        return new ArrayList<>(stuff);
    }

    public void addBasicClasses(){
        List<String> basicClasses = rulesContainer.getBasicClasses();
        for(String cls:basicClasses){
            Scene.v().addBasicClass(cls, HIERARCHY);
        }
    }

    @Async("tabby-saver")
    @Scheduled(fixedRate = 2, timeUnit = TimeUnit.MINUTES)
    public void count(){
        if(GlobalConfiguration.tickTock != null){
            GlobalConfiguration.tickTock.ticktockForScheduleTask(dataContainer.getRunningMethods());
            // print current running methods and kill overtime methods
            if(GlobalConfiguration.TIMEOUT_FORCE_STOP){
                if(dataContainer.getRunningMethods() == null || dataContainer.getRunningMethods().isEmpty()) return;
                Set<String> uuids = ImmutableSet.copyOf(dataContainer.getRunningMethods().keySet());
                if(GlobalConfiguration.PRINT_METHODS) {
                    LocalDateTime now = LocalDateTime.now();
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    log.warn("================================{}================================", formatter.format(now));
                }
                for(String uuid: uuids){
                    Context context = dataContainer.getRunningMethodContext(uuid);
                    if(context == null) continue;
                    String method = context.getMethodSignature();
                    long cost = context.getSeconds();
                    if(cost >= GlobalConfiguration.METHOD_TIMEOUT_SECONDS) {
                        context.setAnalyseTimeout(true);
                        if(GlobalConfiguration.PRINT_METHODS){
                            log.warn("Cost {}s, trigger killer, {}", String.format("%5d",context.getSeconds()), method);
                        }
                    }else{
                        if(GlobalConfiguration.PRINT_METHODS){
                            log.warn("Cost {}s, {}", String.format("%5d",context.getSeconds()), method);
                        }
                    }
                }
                if(GlobalConfiguration.PRINT_METHODS) {
                    log.warn("================================end================================");
                }
            }
        }
    }

}