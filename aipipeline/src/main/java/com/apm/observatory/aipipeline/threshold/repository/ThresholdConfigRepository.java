package com.apm.observatory.aipipeline.threshold.repository;

import com.apm.observatory.aipipeline.threshold.entity.ThresholdConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ThresholdConfigRepository extends JpaRepository<ThresholdConfigEntity, String> {

    List<ThresholdConfigEntity> findAll();
    Optional<ThresholdConfigEntity> findByAppName(String appName);

}