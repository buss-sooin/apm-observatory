package com.apm.observatory.targetappmvc.controller;

import com.apm.observatory.targetappmvc.repository.TestRepository;
import com.apm.observatory.targetappmvc.entity.TestEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class TestController {

    private static final Logger log = LoggerFactory.getLogger(TestController.class);
    private final TestRepository testRepository;
    private final RestClient restClient;

    public TestController(TestRepository testRepository, RestClient restClient) {
        this.testRepository = testRepository;
        this.restClient = restClient;
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ok");
    }

    @GetMapping("/external")
    public String external() {
        return restClient.get()
                .uri("https://httpbin.org/get")
                .retrieve()
                .body(String.class);
    }

    @GetMapping("/hello")
    public String hello() {
        return "hello";
    }

    @GetMapping("/db")
    public List<TestEntity> db() {
        return testRepository.findAll();
    }

    // 의도: DB 조회 + 외부 API 호출을 한 요청 안에서 동시 수행
    // 하나의 traceId 아래 INTERNAL → DB + EXTERNAL 트리 구조 생성
    // → 폭포수 차트에서 Span 중첩 구조를 시각적으로 확인 가능
    @GetMapping("/combined")
    public Map<String, Object> combined() {
        log.info("[TestController] /combined 요청 수신");  // 추가
        Map<String, Object> result = new HashMap<>();
        List<TestEntity> dbResult = testRepository.findAll();
        result.put("db", dbResult);
        String externalResult = restClient.get()
                .uri("https://httpbin.org/get")
                .retrieve()
                .body(String.class);
        result.put("external", externalResult);
        log.info("[TestController] /combined 응답 완료");  // 추가
        return result;
    }

}