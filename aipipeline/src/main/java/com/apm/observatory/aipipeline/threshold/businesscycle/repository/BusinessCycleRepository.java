package com.apm.observatory.aipipeline.threshold.businesscycle.repository;

import com.apm.observatory.aipipeline.threshold.businesscycle.entity.BusinessCycleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BusinessCycleRepository extends JpaRepository<BusinessCycleEntity, String> {
    Optional<BusinessCycleEntity> findByAppName(String appName);
    void deleteByAppName(String appName);
}