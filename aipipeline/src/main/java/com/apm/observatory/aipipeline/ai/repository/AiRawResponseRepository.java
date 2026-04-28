package com.apm.observatory.aipipeline.ai.repository;

import com.apm.observatory.aipipeline.ai.entity.AiRawResponseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRawResponseRepository extends JpaRepository<AiRawResponseEntity, String> {
}