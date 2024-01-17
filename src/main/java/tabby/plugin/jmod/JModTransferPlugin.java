package tabby.plugin.jmod;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import tabby.common.utils.FileUtils;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * @author wh1t3p1g
 * @project tabby
 * @since 2024/1/16
 */
@Component
public class JModTransferPlugin {

    @Async("tabby-collector")
    public CompletableFuture<Boolean> transfer(String source, String output){

        try{
            if(source.endsWith(".jar")){
                FileUtils.copy(source, output);
            }else if(source.endsWith(".jmod")){
                ZipFile moduleFile = new ZipFile(source);
                ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(output));
                for(Enumeration enums = moduleFile.entries(); enums.hasMoreElements();){
                    ZipEntry entry = (ZipEntry)enums.nextElement();
                    String name = entry.getName();
                    if((name.startsWith("classes") && !name.contains("module-info"))
                            || name.startsWith("resources") || name.startsWith("lib")){
                        zipOutputStream.putNextEntry(new ZipEntry(name.substring(name.indexOf('/') + 1)));
                        InputStream in = moduleFile.getInputStream(entry);
                        while(in.available() > 0)
                            zipOutputStream.write(in.read());
                        zipOutputStream.flush();
                    }
                }
                zipOutputStream.close();
            }
        } catch(Exception e) {
            e.printStackTrace();
        }

        return CompletableFuture.completedFuture(true);
    }


}
