package com.apm.observatory.aipipeline.ai.repository;

import com.apm.observatory.aipipeline.ai.entity.AiAnalysisResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface AiAnalysisResultRepository extends JpaRepository<AiAnalysisResultEntity, String> {

    // app_name 기반 최근 결과 조회 (API 서버에서도 이 Repository를 공유하거나 동일하게 선언)
    List<AiAnalysisResultEntity> findByAppNameAndTimestampBetween(
            String appName,
            Instant start,
            Instant end
    );

}