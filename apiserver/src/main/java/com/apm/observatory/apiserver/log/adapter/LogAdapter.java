package com.apm.observatory.apiserver.log.adapter;

import com.apm.observatory.apiserver.log.repository.LogRepository;
import com.apm.observatory.apiserver.log.model.LogModel.LogEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

// 의도: 단순 조회 + Entity → model 변환만 있어 Port 없이 Adapter만 사용
// 도메인 판단 로직 없음 → 변환 경계만 담당
@Component
@RequiredArgsConstructor
public class LogAdapter {

    private final LogRepository logRepository;

    public List<LogEntry> streamLogs(String appName, Instant startTime,
                                     Instant endTime, String level) {
        // 의도: level 파라미터 있으면 필터, 없으면 전체 조회
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