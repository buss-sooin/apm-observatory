package com.apm.observatory.aipipeline;

import com.apm.observatory.aipipeline.config.AiPipelineConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/** AI 분석 파이프라인 애플리케이션 진입점. 스케줄링과 설정 프로퍼티를 활성화한다. */
@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(AiPipelineConfig.class)
public class AiPipelineApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiPipelineApplication.class, args);
    }

}