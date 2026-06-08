package com.apm.observatory.apiserver.log.adapter;

import com.apm.observatory.apiserver.log.repository.LogRepository;
import com.apm.observatory.apiserver.log.model.LogModel.LogEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * 로그 조회와 Entity → model 변환만 담당한다. 도메인 판단이 없는 단순 변환 경계라 Port 없이
 * Adapter만 둔다.
 */
@Component
@RequiredArgsConstructor
public class LogAdapter {

    private final LogRepository logRepository;

    /** level이 있으면 해당 레벨만, 없으면 전체를 조회한다. level은 대문자로 맞춰 비교한다. */
    public List<LogEntry> streamLogs(String appName, Instant startTime,
                                     Instant endTime, String level) {
        List<?> entities = (level != null && !level.isBlank())
                ? logRepository.findStreamByLevel(appName, startTime, endTime, level.toUpperCase())
                : logRepository.findStream(appName, startTime, endTime);

        return entities.stream()
                .map(e -> {
                    var log = (com.apm.observatory.apiserver.log.entity.LogEntity) e;
                    return new LogEntry(
                            log.getId().getTimestamp(),
                            log.getId().getAppName(),
                            log.getId().getThreadName(),
                            log.getLevel(),
                            log.getMessage(),
                            log.getTraceId(),
                            log.getStackTrace(),
                            log.isError()
                    );
                })
                .toList();
    }

}