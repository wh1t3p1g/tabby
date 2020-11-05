package tabby.util;

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

    public static List<Path> getTargetDirectoryJarFiles(String target) throws IOException {
        List<Path> paths = new ArrayList<>();
        Path path = Paths.get(target).toAbsolutePath();
        if (!Files.exists(path)) {
            throw new IllegalArgumentException("Invalid jar path: " + path);
        }
        if(path.toFile().isFile()){
            paths.add(path);
        }else{
            Files.walkFileTree(path, new SimpleFileVisitor<Path>(){
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if(file.getFileName().endsWith(".jar")){
                        paths.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        return paths;
    }
}
