package com.apm.observatory.aipipeline.ai.repository;

import com.apm.observatory.aipipeline.ai.entity.AiAnalysisResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface AiAnalysisResultRepository extends JpaRepository<AiAnalysisResultEntity, String> {

    List<AiAnalysisResultEntity> findByAppNameAndTimestampBetween(
            String appName,
            Instant start,
            Instant end
    );

}