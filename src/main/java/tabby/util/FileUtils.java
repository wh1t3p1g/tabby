package tabby.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * @author wh1t3P1g
 * @since 2020/10/22
 */
public class FileUtils {

    public static List<String> getTargetDirectoryJarFiles(String target) throws IOException {
        List<String> paths = new ArrayList<>();
        Path path = Paths.get(target).toAbsolutePath();
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Invalid jar path: " + path);
        }
        if(path.toFile().isFile()){
            paths.add(path.toAbsolutePath().toString());
        }else{
            Files.walkFileTree(path, new SimpleFileVisitor<Path>(){
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if(file.getFileName().toString().endsWith(".jar")){
                        paths.add(file.toAbsolutePath().toString());
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
}
