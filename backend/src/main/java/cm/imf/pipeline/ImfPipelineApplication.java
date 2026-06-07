package cm.imf.pipeline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableAspectJAutoProxy
public class ImfPipelineApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImfPipelineApplication.class, args);
    }
}
