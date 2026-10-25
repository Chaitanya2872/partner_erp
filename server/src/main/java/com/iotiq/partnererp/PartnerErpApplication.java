package com.iotiq.partnererp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PartnerErpApplication {

    public static void main(String[] args) {
        SpringApplication.run(PartnerErpApplication.class, args);
    }
}
