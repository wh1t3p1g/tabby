package tabby.analysis.data;

import lombok.Data;
import org.apache.commons.lang3.RandomStringUtils;
import tabby.config.GlobalConfiguration;
import tabby.common.utils.FileUtils;
import tabby.plugin.JspCompilePlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author wh1t3p1g
 * @since 2022/12/18
 */
@Data
public class FileLocation {

    private Set<String> cps = new HashSet<>();
    private boolean isJar = false;
    private boolean isFatJar = false;
    private boolean isWar = false;
    private boolean isClass = false;
    private boolean isJsp = false;
    private boolean isDir = false;
    private Path path;

    public FileLocation(Path path) {
        this.path = path;
        isDir = Files.isDirectory(path);
        if(!isDir){
            String filepath = path.toString();
            if(filepath.endsWith(".jsp")){
                isJsp = true;
            }else if(filepath.endsWith(".class")){
                isClass = true;
            }else if(filepath.endsWith(".jar")){
                isFatJar = GlobalConfiguration.IS_CHECK_FAT_JAR
                            && FileUtils.isFatJar(filepath);
                if(!isFatJar){
                    isJar = true;
                }
            }else if(filepath.endsWith(".war")){
                isWar = true;
            }
        }
    }

    public String getFileName(){
        return path.getFileName().toString();
    }

    public Set<String> resolve(){
        Path targetPath = null;
        if(isWar || isFatJar){
            // unpack
            try{
                targetPath = FileUtils.unpack(path, getFileName());
            }catch (IOException e){
                e.printStackTrace();
            }
        } else if(isDir) {
            targetPath = path;
        }

        if(targetPath == null){
            cps.add(path.toString());
        }else{
            try{
                Map<String, Set<String>> targets = new HashMap<>();
                targets.put("jar", new HashSet<>());
                targets.put("war", new HashSet<>());
                targets.put("classes", new HashSet<>());
                targets.put("jsp", new HashSet<>());
                targets.put("jmods", new HashSet<>());
                FileUtils.findAllTargets(targetPath, targets);

                cps.addAll(targets.get("jmods")); // jdk >= 9 直接添加

                if(isDir){
                    // 对于目录类型，可能是最初的输入
                    // 需要对当前目录的jar和war做处理
                    Set<String> allUnpackedFiles = new HashSet<>();
                    allUnpackedFiles.addAll(targets.get("war"));
                    allUnpackedFiles.addAll(targets.get("jar"));
                    for(String unpacked:allUnpackedFiles){
                        FileLocation location = new FileLocation(Paths.get(unpacked));
                        cps.addAll(location.resolve());
                    }
                }else{ // 一般不太可能 war里面嵌套一个war，所以这里暂时不考虑war
                    cps.addAll(targets.get("jar"));
                }
                // 处理class和jsp
                Path tempPath = targetPath;
                if(isDir){
                    tempPath = FileUtils.registerTempDirectory(RandomStringUtils.randomAlphanumeric(3));
                }
                Set<String> remainedClasses = new HashSet<>();
                Set<String> allClasses = targets.get("classes");
                for(String cls:allClasses){
                    if(cls.contains("BOOT-INF/classes/") || cls.contains("WEB-INF/classes/")) continue;
                    remainedClasses.add(cls);
                }
                if(remainedClasses.size() > 0){
                    Path tmpClassesPath = tempPath.resolve("classes_"+RandomStringUtils.randomAlphanumeric(3));
                    FileUtils.copyAll(remainedClasses, tmpClassesPath, targetPath.toString());
                    cps.add(tmpClassesPath.toString());
                }
                // jsp
                if(targets.get("jsp").size() > 0){
                    Path tmpJspPath = tempPath.resolve("jsp_"+RandomStringUtils.randomAlphanumeric(3));
                    FileUtils.copyAll(targets.get("jsp"), tmpJspPath, targetPath.toString());
                    String allJarClasspath = String.join(File.pathSeparator, targets.get("jar"));
                    String systemClasspath = System.getProperty("java.class.path");
                    if(systemClasspath != null && !systemClasspath.isEmpty()){
                        allJarClasspath = systemClasspath + File.pathSeparator + allJarClasspath;
                    }
                    String output = JspCompilePlugin.parse(tmpJspPath.toString(), allJarClasspath) + "/org/apache/jsp/";
                    cps.add(output);
                }
                //   BOOT-INF/classes
                //   WEB-INF/classes
                Path classes = targetPath.resolve("BOOT-INF/classes");
                if(Files.exists(classes)){
                    cps.add(classes.toString());
                }
                classes = targetPath.resolve("WEB-INF/classes");
                if(Files.exists(classes)){
                    cps.add(classes.toString());
                }
            }catch (IOException e){
                e.printStackTrace();
            }
        }

        return cps;
    }

}
