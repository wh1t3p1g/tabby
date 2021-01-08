package tabby.util;

import tabby.config.GlobalConfiguration;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;

/**
 * @author wh1t3P1g
 * @since 2020/10/22
 */
public class FileUtils {

    public static Map<String, String> getTargetDirectoryJarFiles(String target) throws IOException {
        Map<String, String> paths = new HashMap<>();
        Path path = Paths.get(target).toAbsolutePath();
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Invalid jar path: " + path);
        }

        if(path.toFile().isFile()){
            paths.put(path.toFile().getName(), path.toAbsolutePath().toString());
        }else{
            Files.walkFileTree(path, new SimpleFileVisitor<Path>(){
                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                    if(path.getFileName().toString().endsWith(".jar")){
                        paths.put(path.toFile().getName(), path.toAbsolutePath().toString());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        return paths;
    }

    public static boolean fileExists(String path){
        File file = new File(path);
        return file.exists();
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

}
