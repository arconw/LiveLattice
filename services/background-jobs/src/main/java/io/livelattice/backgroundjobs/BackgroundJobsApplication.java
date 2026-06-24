package io.livelattice.backgroundjobs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BackgroundJobsApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackgroundJobsApplication.class, args);
    }
}
