package com.apm.observatory.apiserver.span.repository;

import com.apm.observatory.apiserver.span.entity.SpanEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpanRepository extends JpaRepository<SpanEntity, String> {

    /**
     * traceId로 전체 Span을 start_time 오름차순으로 조회한다. waterfall 트리 조립의
     * 입력이며, 오름차순이라 root가 자식보다 앞선다.
     */
    List<SpanEntity> findByTraceIdOrderByStartTimeAsc(String traceId);

}