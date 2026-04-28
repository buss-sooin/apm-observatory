package com.apm.observatory.apiserver.span.repository;

import com.apm.observatory.apiserver.span.entity.SpanEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpanRepository extends JpaRepository<SpanEntity, String> {

    // 의도: traceId로 전체 Span 조회 → waterfall 트리 조립용
    // start_time 오름차순 → root부터 자식 순서로 정렬
    List<SpanEntity> findByTraceIdOrderByStartTimeAsc(String traceId);

}