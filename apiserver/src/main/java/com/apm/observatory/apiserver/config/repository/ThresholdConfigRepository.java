package com.apm.observatory.apiserver.config.repository;

import com.apm.observatory.apiserver.config.entity.ThresholdConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ThresholdConfigRepository extends JpaRepository<ThresholdConfigEntity, String> {

    Optional<ThresholdConfigEntity> findByAppName(String appName);

}