package com.apm.observatory.aipipeline;

import com.apm.observatory.aipipeline.config.AiPipelineConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(AiPipelineConfig.class)
public class AiPipelineApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiPipelineApplication.class, args);
    }

}