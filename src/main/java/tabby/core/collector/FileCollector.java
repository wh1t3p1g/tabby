package tabby.core.collector;

import org.springframework.stereotype.Service;
import tabby.config.GlobalConfiguration;
import tabby.core.data.FileLocation;
import tabby.util.FileUtils;
import tabby.util.JavaVersion;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author wh1t3p1g
 * @since 2022/12/18
 */
@Service
public class FileCollector {

    public Map<String, String> collect(String targetPath){
        Map<String, String> allTargets = new HashMap<>();
        Path path = Paths.get(targetPath).toAbsolutePath();
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Invalid target path: " + path);
        }
        FileLocation location = new FileLocation(path);
        Set<String> cps = location.resolve();
        for(String cp:cps){
            Path temp = Paths.get(cp);
            if(Files.isDirectory(temp)){
                allTargets.put(cp, cp);
            }else{
                String filename = temp.getFileName().toString();
                String fileMd5 = FileUtils.getFileMD5(cp);
                if(GlobalConfiguration.IS_WEB_MODE && GlobalConfiguration.rulesContainer.isInCommonJarList(filename)){
                    GlobalConfiguration.libraries.put(fileMd5, cp);
                }else{
                    allTargets.put(fileMd5, cp);
                }
            }
        }
        return allTargets;
    }



    public Map<String, String> collectJdkDependencies() throws IOException {
        Map<String, String> allJdkDependencies = new HashMap<>();

        String javaHome = System.getProperty("java.home");
        if(JavaVersion.isAtLeast(9)){ // jdk >= 9
            if(GlobalConfiguration.IS_WITH_ALL_JDK){
                allJdkDependencies.putAll(FileUtils.findAllJdkDependencies(javaHome+"/jmods/", false));
            }else{
                String path = javaHome+"/jmods/java.base.jmod";
                File file = new File(path);
                if(file.exists()){
                    allJdkDependencies.put(FileUtils.getFileMD5(file), "java.base");
                }
            }
        }else{ // jdk <= 8
            if(GlobalConfiguration.IS_WITH_ALL_JDK){
                allJdkDependencies.putAll(FileUtils.findAllJdkDependencies(javaHome+"/lib", true));
                allJdkDependencies.putAll(FileUtils.findAllJdkDependencies(javaHome+"/../lib", false));
            }else{
                String[] jre = new String[]{"lib/rt.jar","lib/jce.jar"};
                for(String cp:jre){
                    String path = String.join(File.separator, javaHome, cp);
                    File file = new File(path);
                    if(file.exists()){
                        allJdkDependencies.put(FileUtils.getFileMD5(file), path);
                    }
                }
            }
        }

        return allJdkDependencies;
    }

}
