package com.apm.observatory.apiserver.config.repository;

import com.apm.observatory.apiserver.config.entity.BusinessCycleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface BusinessCycleRepository extends JpaRepository<BusinessCycleEntity, String> {
    Optional<BusinessCycleEntity> findByAppName(String appName);

    /** 파생 삭제 쿼리라 쓰기 트랜잭션이 필요해 {@code @Transactional}을 둔다. */
    @Transactional
    void deleteByAppName(String appName);
}