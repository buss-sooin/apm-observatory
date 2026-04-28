package com.apm.observatory.aipipeline.threshold.businesscycle.adapter;

import com.apm.observatory.aipipeline.threshold.businesscycle.model.BusinessCycle;
import com.apm.observatory.aipipeline.threshold.businesscycle.port.BusinessCyclePort;
import com.apm.observatory.aipipeline.threshold.businesscycle.repository.BusinessCycleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class BusinessCycleAdapter implements BusinessCyclePort {

    private final BusinessCycleRepository repository;

    @Override
    public Optional<BusinessCycle> findByAppName(String appName) {
        return repository.findByAppName(appName)
                .map(e -> new BusinessCycle(
                        e.getAppName(),
                        e.getCycleStart(),
                        e.getCycleEnd(),
                        e.getPeakStart(),
                        e.getPeakEnd()
                ));
    }

}