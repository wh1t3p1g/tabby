package tabby.config;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tabby.util.FileUtils;

import javax.sql.DataSource;

import static tabby.config.GlobalConfiguration.IS_CACHE_AUTO_REMOVE;
import static tabby.config.GlobalConfiguration.REAL_CACHE_PATH;


/**
 * @author wh1t3p1g
 * @since 2022/9/5
 */
@Configuration
public class H2Configuration {

    @Bean
    public DataSource getDataSource() {

        if(IS_CACHE_AUTO_REMOVE){
            FileUtils.delete(REAL_CACHE_PATH+".mv.db");
        }

        DataSourceBuilder dataSourceBuilder = DataSourceBuilder.create();
        String url = String.format("jdbc:h2:file:%s;MODE=MySQL;LOCK_MODE=3;compress=true", REAL_CACHE_PATH);
        dataSourceBuilder.url(url);
        dataSourceBuilder.driverClassName("org.h2.Driver");

        return dataSourceBuilder.build();
    }

}
