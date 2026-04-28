package com.apm.observatory.apiserver.ai.repository;

import com.apm.observatory.apiserver.ai.entity.AiAnalysisResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiAnalysisResultRepository extends JpaRepository<AiAnalysisResultEntity, String> {
    List<AiAnalysisResultEntity> findByAppNameOrderByTimestampDesc(String appName);
}