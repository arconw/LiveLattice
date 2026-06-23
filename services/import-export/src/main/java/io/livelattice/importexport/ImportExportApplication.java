package io.livelattice.importexport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ImportExportApplication {

    public static void main(String[] args) {
        SpringApplication.run(ImportExportApplication.class, args);
    }
}
