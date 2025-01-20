package tabby.plugin.jsp;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.jasper.JspC;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * @author wh1t3p1g
 * @since 2021/12/30
 */
@Slf4j
public class JspCompilePlugin {

    public static String parse(String uriRoot, String classpath) {
        String output = String.join(File.separator, uriRoot, "jsp_classes_" + RandomStringUtils.randomAlphanumeric(5));
        try {
            JspC jspc = new JspC();
            jspc.setCompile(true);
            jspc.setClassDebugInfo(false);
            jspc.setFailOnError(false);
//            jspc.setBlockExternal(false);
            jspc.setOutputDir(output);
            jspc.setClassPath(classpath);
            jspc.setUriroot(uriRoot);
            jspc.execute();
            return output;
        } catch (Exception e) {
//            log.error(e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("JSP parse error!");
        } finally {
            clean(output);
        }
    }

    public static void clean(String target) {
        File file = new File(target);
        if (file.exists() && file.isDirectory()) {
            try {
                Files.walkFileTree(file.toPath(), new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                        String file = path.toAbsolutePath().toString();
                        if (file.endsWith(".java")) {
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
