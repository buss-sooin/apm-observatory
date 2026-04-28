package com.apm.observatory.targetappmvc.repository;

import com.apm.observatory.targetappmvc.entity.TestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TestRepository extends JpaRepository<TestEntity, Long> {

}