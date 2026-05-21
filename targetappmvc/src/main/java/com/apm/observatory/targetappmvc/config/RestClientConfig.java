package com.apm.observatory.targetappmvc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 외부 HTTP 호출용 RestClient 빈 설정.
 *
 * <p>타임아웃 값은 {@link TargetAppConfig}에서 가져온다. 기본값인 무한 대기는
 * 외부 응답 지연이 타깃 앱 요청 스레드를 무제한 점유시켜 후속 요청 처리에 영향을 준다.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TargetAppConfig.REST_CLIENT_CONNECT_TIMEOUT);
        factory.setReadTimeout(TargetAppConfig.REST_CLIENT_READ_TIMEOUT);
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

}
