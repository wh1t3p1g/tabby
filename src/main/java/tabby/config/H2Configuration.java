package tabby.config;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tabby.util.FileUtils;

import javax.sql.DataSource;

import java.io.File;

import static tabby.config.GlobalConfiguration.*;


/**
 * @author wh1t3p1g
 * @since 2022/9/5
 */
@Configuration
public class H2Configuration {

    @Bean
    public DataSource getDataSource() {
        String filepath = String.join(File.separator,CACHE_DIRECTORY, CACHE_DB_FILENAME + ".mv.db");
        if(IS_CACHE_AUTO_REMOVE){
            FileUtils.delete(filepath);
        }

        DataSourceBuilder dataSourceBuilder = DataSourceBuilder.create();
        String url = String.format("jdbc:h2:file:%s;MODE=MySQL;LOCK_MODE=3;compress=true", filepath);
        dataSourceBuilder.url(url);
        dataSourceBuilder.driverClassName("org.h2.Driver");

        return dataSourceBuilder.build();
    }

}
