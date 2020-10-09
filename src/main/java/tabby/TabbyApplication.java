package tabby;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class TabbyApplication {

    public static void main(String[] args) {
        SpringApplication.run(TabbyApplication.class, args);
    }

}
