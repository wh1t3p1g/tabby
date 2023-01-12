package tabby.util;

import com.google.common.hash.Hashing;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import tabby.config.GlobalConfiguration;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;

/**
 * @author wh1t3P1g
 * @since 2020/10/22
 */
@Slf4j
public class FileUtils {

    public static Map<String, String> findAllJdkDependencies(String target, boolean isNeedRecursion) throws IOException {
        Map<String, String> paths = new HashMap<>();
        Path path = Paths.get(target).toRealPath();
        String realPath = path.toString();
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Invalid jar path: " + path);
        }

        Files.walkFileTree(path, new SimpleFileVisitor<Path>(){
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                String file = path.toAbsolutePath().toString();

                if(file.endsWith("local_policy.jar") || file.endsWith("US_export_policy.jar")){
                    return FileVisitResult.CONTINUE;
                }

                if(file.endsWith(".jar") || file.endsWith(".jmod")){
                    String fileMd5 = FileUtils.getFileMD5(file);
                    paths.put(fileMd5, file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if(isNeedRecursion || realPath.equals(dir.toAbsolutePath().toString())){
                    return super.preVisitDirectory(dir, attrs);
                }else{
                    return FileVisitResult.SKIP_SUBTREE;
                }
            }
        });

        return paths;
    }

    public static boolean isFatJar(String filepath){
        Path path = Paths.get(filepath);
        if(Files.exists(path)){
            try(JarFile jarFile = new JarFile(path.toFile())){
                return jarFile.getEntry("WEB-INF") != null
                        || jarFile.getEntry("BOOT-INF") != null
                        || jarFile.getEntry("lib") != null;
            }catch (Exception ignore){}
        }
        return false;
    }

    public static void findAllTargets(Path path, Map<String, Set<String>> map) throws IOException {
        if(path == null) return;
        Files.walkFileTree(path, new SimpleFileVisitor<Path>(){
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                String file = path.toAbsolutePath().toString();
                if(file.endsWith(".jar")){
                    map.get("jar").add(file);
                } else if(file.endsWith(".class")){
                    map.get("classes").add(file);
                }else if(file.endsWith(".jmod")){
                    map.get("jmods").add(file);
                } else if(file.endsWith(".war")){
                    map.get("war").add(file);
                } else if(file.endsWith(".jsp") || file.endsWith(".jspx") || file.endsWith(".tld")){
                    map.get("jsp").add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static Path unpack(Path path, String filename) throws IOException {
        if(Files.exists(path)){
            Path output = registerTempDirectory(filename + RandomStringUtils.randomAlphanumeric(3));
            extract(path, output);
            return output;
        }
        return null;
    }

    /**
     * 提取当前fatJar到临时目录
     * @param jarPath
     * @param tmpDir
     * @throws IOException
     */
    public static void extract(Path jarPath, Path tmpDir) throws IOException {
        try (JarInputStream jarInputStream = new JarInputStream(Files.newInputStream(jarPath))) {
            JarEntry jarEntry;
            while ((jarEntry = jarInputStream.getNextJarEntry()) != null) {
                Path fullPath = tmpDir.resolve(jarEntry.getName());
                if (!jarEntry.isDirectory()
                        && (
                        jarEntry.getName().endsWith(".class")
                                || jarEntry.getName().endsWith(".jar")
                                || jarEntry.getName().endsWith(".jsp")
                                || jarEntry.getName().endsWith(".jspx")
                                || jarEntry.getName().endsWith(".tld")
                                || jarEntry.getName().endsWith(".jmod")
                )) {
                    Path dirName = fullPath.getParent();
                    if (dirName == null) {
                        throw new IllegalStateException("Parent of item is outside temp directory.");
                    }
                    if (!Files.exists(dirName)) {
                        Files.createDirectories(dirName);
                    }

                    Files.copy(jarInputStream, fullPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    public static void copy(String source, Path target) throws IOException {
        Path dirPath = target.getParent();
        if(!Files.exists(dirPath)){
            Files.createDirectories(dirPath);
        }
        Files.copy(Paths.get(source), target, StandardCopyOption.REPLACE_EXISTING);
    }

    public static void copyAll(Set<String> sources, Path target, String basePath) throws IOException {
        if(Files.notExists(target)){
            Files.createDirectories(target);
        }
        int len = basePath.length();
        for(String source:sources){
            if(source.startsWith(basePath)){
                String sub = source.substring(len);
                if(sub.startsWith("/")){
                    sub = sub.substring(1);
                }
                Path path = target.resolve(sub);
                copy(source, path);
            }
        }
    }

    public static boolean fileExists(String path){
        if(path == null) return false;

        File file = new File(path);
        return file.exists();
    }

    public static void delete(String filepath){
        File file = new File(filepath);
        if (file.exists()) {
            file.delete();
        }
    }

    /**
     * 获取json格式的文件，并解析出具体的对象
     * 如果文件不存在，则返回null
     * @param path json文件路径
     * @param type 需要还原的对象类型
     * @return 还原后的对象，或null
     */
    public static Object getJsonContent(String path, Class<?> type){
        File file = new File(path);
        if(!file.exists()) return null;
        FileReader reader = null;
        try {
            reader = new FileReader(file);
            return GlobalConfiguration.GSON.fromJson(reader, type);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void putJsonContent(String path, Object data){
        File file = new File(path);
        FileWriter writer = null;
        try {
            writer = new FileWriter(file);
            writer.write(GlobalConfiguration.GSON.toJson(data));
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void putRawContent(String path, Collection<String> data){
        File file = new File(path);
        try (FileWriter writer = new FileWriter(file)){
            for(String d:data){
                writer.write(d+"\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 注册新的临时目录
     * @param directory temp directory
     * @return temp directory path
     * @throws IOException
     */
    public static Path registerTempDirectory(String directory) throws IOException {

        final Path tmpDir = Files.createTempDirectory("tabby_"+directory);
        // Delete the temp directory at shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                deleteDirectory(tmpDir);
            } catch (IOException e) {
                log.error("Error cleaning up temp directory " + tmpDir.toString(), e);
            }
        }));

        return tmpDir;
    }

    /**
     * Recursively delete the directory root and all its contents
     * @param root Root directory to be deleted
     */
    public static void deleteDirectory(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void createDirectory(String path){
        File file = new File(path);
        if(file.mkdirs()){
            log.info("Create directory {} success!", path);
        }
    }

    public static void createDirectory(Path path){
        File file = path.toFile();
        if(!file.exists() && file.mkdirs()){
            log.info("Create directory {} success!", path);
        }
    }

    public static String getWinPath(String path){
        if(JavaVersion.isWin()){
            path = "/"+path.replace("\\", "/");
        }
        return path;
    }

    public static String getFileMD5(String filepath){
        return getFileMD5(new File(filepath));
    }

    public static String getFileMD5(File file){
        try {
            return com.google.common.io.Files.hash(file, Hashing.md5()).toString();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }
    }
}
