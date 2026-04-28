package com.apm.observatory.collectorserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class CollectorServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(CollectorServerApplication.class, args);
    }

}