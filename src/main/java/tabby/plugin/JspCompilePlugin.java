package tabby.plugin;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.jasper.JspC;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * @author wh1t3p1g
 * @since 2021/12/30
 */
@Slf4j
public class JspCompilePlugin {

    public static String parse(String uriRoot, String classpath){
        try{
            String output = String.join(File.separator, uriRoot, "jsp_classes_"+ RandomStringUtils.randomAlphanumeric(5));
            JspC jspc = new JspC();
            jspc.setCompile(true);
            jspc.setClassDebugInfo(false);
            jspc.setFailOnError(false);
            jspc.setOutputDir(output);
            jspc.setClassPath(classpath);
            jspc.setUriroot(uriRoot);
            jspc.execute();
            clean(output);
            return output;
        }catch (Exception e){
            log.error(e.getMessage());
        }
        return null;
    }

    public static String parse(String uriRoot){
        log.debug("Parse jsp files...");
        File file = new File(uriRoot);
        if(file.exists() && file.isDirectory()){
            String classpath = getClasspath(file);
            return parse(uriRoot, classpath);
        }else if(file.exists() && file.isFile()){
            if(uriRoot.endsWith(".jsp") || uriRoot.endsWith(".jspx")){
                return parse(file.getParentFile().toString(), null);
            }
        }
        return null;
    }

    public static String getClasspath(File baseRoot){
        List<String> jarFiles = new ArrayList<>();
        // find all jars as dependencies
        // normal at WEB-INF/lib BOOT-INF/lib
        String systemClasspath = System.getProperty("java.class.path");
        try {
            Files.walkFileTree(baseRoot.toPath(), new SimpleFileVisitor<Path>(){
                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                    String file = path.toAbsolutePath().toString();
                    if(file.endsWith(".jar")){
                        jarFiles.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            if(jarFiles.size() > 0){
                if(systemClasspath != null && !systemClasspath.isEmpty()){
                    return systemClasspath + File.pathSeparator +String.join(File.pathSeparator, jarFiles);
                }else if(systemClasspath != null){
                    return String.join(File.pathSeparator, jarFiles);
                }
            }
        } catch (IOException e) {
            //
        }
        return systemClasspath;
    }

    public static void clean(String target){
        File file = new File(target);
        if(file.exists() && file.isDirectory()){
            try {
                Files.walkFileTree(file.toPath(), new SimpleFileVisitor<Path>(){
                    @Override
                    public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                        String file = path.toAbsolutePath().toString();
                        if(file.endsWith(".java")){
                            Files.deleteIfExists(Paths.get(file));
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                //
            }
        }
    }
}
