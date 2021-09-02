package tabby.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import soot.CompilationDeathException;
import soot.Main;
import soot.Scene;
import soot.options.Options;
import tabby.config.GlobalConfiguration;
import tabby.config.SootConfiguration;
import tabby.core.container.DataContainer;
import tabby.core.container.RulesContainer;
import tabby.core.scanner.CallGraphScanner;
import tabby.core.scanner.ClassInfoScanner;
import tabby.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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

    public void run(String target, boolean isJDKProcess,
                    boolean withAllJDK, boolean isSaveOnly,
                    boolean excludeJDK, boolean isJDKOnly, boolean checkFatJar) throws IOException {
        if(isSaveOnly){
            save();
        }else{
            Map<String, String> dependencies = getJdkDependencies(withAllJDK);
            log.info("Get {} JDK dependencies", dependencies.size());
            Map<String, String> classpaths = excludeJDK?
                    new HashMap<>():new HashMap<>(dependencies);
            Map<String, String> targets = new HashMap<>();
            if(isJDKOnly){
                targets.putAll(dependencies);
            }else{
                Map<String, String> files = FileUtils.getTargetDirectoryJarFiles(target, checkFatJar);
                classpaths.putAll(files);
                targets.putAll(files);
                if(isJDKProcess){
                    targets.putAll(dependencies);
                }
            }
            runSootAnalysis(targets, new ArrayList<>(classpaths.values()) );
        }
    }

    public void runSootAnalysis(Map<String, String> targets, List<String> classpaths){
        try{
            SootConfiguration.initSootOption();
            long start = System.nanoTime();
            addBasicClasses();
            // set class paths
            Scene.v().setSootClassPath(String.join(File.pathSeparator, new HashSet<>(classpaths)));
            // get target filepath
            List<String> realTargets = getTargets(targets);
            if(realTargets.isEmpty()){
                log.info("Nothing to analysis!");
                return;
            }
            Main.v().autoSetOptions();

            // 类信息抽取
            classInfoScanner.run(realTargets);
            // 函数调用分析
            callGraphScanner.run();
            rulesContainer.saveStatus();
            log.info("Cost {} seconds"
                    , TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start));
//            if (!Options.v().oaat()) {
//                PackManager.v().writeOutput();
//            }
        }catch (CompilationDeathException e){
            if (e.getStatus() != CompilationDeathException.COMPILATION_SUCCEEDED) {
                throw e;
            }
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
            Scene.v().addBasicClass(cls ,HIERARCHY);
        }
    }

    public void save(){
        log.info("Start to save cache.");
        long start = System.nanoTime();

        dataContainer.save2CSV();
        dataContainer.save2Neo4j();
        clean();
        log.info("Cost {} seconds"
                , TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start));
    }

    /**
     * 这里提取的是jdk8版本下的jre信息
     * 仅测试于MacOS
     * @return jre
     */
    public Map<String, String> getJdkDependencies(boolean all){
        String javaHome = System.getProperty("java.home");

        String[] jre;
        if(all){
            jre = new String[]{"../lib/dt.jar","../lib/sa-jdi.jar","../lib/tools.jar","../lib/jconsole.jar","lib/resources.jar","lib/rt.jar","lib/jsse.jar","lib/jce.jar","lib/charsets.jar","lib/ext/cldrdata.jar","lib/ext/dnsns.jar","lib/ext/jaccess.jar","lib/ext/localedata.jar","lib/ext/nashorn.jar","lib/ext/sunec.jar","lib/ext/sunjce_provider.jar","lib/ext/sunpkcs11.jar","lib/ext/zipfs.jar","lib/management-agent.jar"};
        }else{// 对于正常分析其他的jar文件，不需要全量jdk依赖的分析，暂时添加这几个
            jre = new String[]{"lib/rt.jar","lib/jce.jar","lib/ext/nashorn.jar"};
        }
        Map<String, String> exists = new HashMap<>();
        for(String cp:jre){
            String path = String.join(File.separator, javaHome, cp);
            File file = new File(path);
            if(file.exists()){
                exists.put(file.getName(), path);
            }
        }
        log.info("Load " +exists.size()+" jre jars.");
        return exists;
    }

    public void clean(){
        try {
            File cacheDir = new File(GlobalConfiguration.CACHE_PATH);
            File[] files = cacheDir.listFiles();
            if(files != null){
                for(File file: files){
                    if(file.getName().endsWith(".csv")){
                        Files.deleteIfExists(file.toPath());
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}