package com.apm.observatory.apiserver.config.repository;

import com.apm.observatory.apiserver.config.entity.BusinessCycleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface BusinessCycleRepository extends JpaRepository<BusinessCycleEntity, String> {
    Optional<BusinessCycleEntity> findByAppName(String appName);

    @Transactional
    void deleteByAppName(String appName);
}