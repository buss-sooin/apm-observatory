package com.apm.observatory.apiserver.span.adapter;

import com.apm.observatory.apiserver.span.entity.SpanEntity;
import com.apm.observatory.apiserver.span.repository.SpanRepository;
import com.apm.observatory.apiserver.span.model.SpanModel.WaterfallResponse;
import com.apm.observatory.apiserver.span.model.SpanModel.WaterfallSpan;
import com.apm.observatory.apiserver.span.port.SpanPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SpanAdapter implements SpanPort {

    private final SpanRepository spanRepository;

    @Override
    public Optional<WaterfallResponse> buildWaterfall(String traceId) {
        List<SpanEntity> spans = spanRepository.findByTraceIdOrderByStartTimeAsc(traceId);
        if (spans.isEmpty()) return Optional.empty();

        // 의도: parent_span_id 없거나 비어있는 Span이 root
        Optional<SpanEntity> rootOpt = spans.stream()
                .filter(s -> s.getParentSpanId() == null || s.getParentSpanId().isBlank())
                .findFirst();

        if (rootOpt.isEmpty()) return Optional.empty();

        SpanEntity root = rootOpt.get();
        long rootStartEpoch = root.getStartTime().toEpochMilli();

        // 의도: spanId → 자식 목록 맵 구성 → DFS 트리 순회용
        Map<String, List<SpanEntity>> childrenMap = spans.stream()
                .filter(s -> s.getParentSpanId() != null && !s.getParentSpanId().isBlank())
                .collect(Collectors.groupingBy(SpanEntity::getParentSpanId));

        // 의도: DFS 재귀 순회로 트리 구조를 유지하면서 flat 리스트 생성
        // BFS 대신 DFS 선택 이유: 부모 → 자식 순서를 자연스럽게 보장
        // 자식들을 start_time 오름차순 정렬 후 방문 → 형제 Span들이 시간순으로 표현
        List<WaterfallSpan> result = new ArrayList<>();
        dfs(root, childrenMap, rootStartEpoch, 0, result);

        return Optional.of(new WaterfallResponse(
                traceId,
                Optional.ofNullable(root.getDurationMs()).orElse(0L),
                root.getStartTime(),
                result
        ));
    }

    private void dfs(SpanEntity node,
                     Map<String, List<SpanEntity>> childrenMap,
                     long rootStartEpoch,
                     int depth,
                     List<WaterfallSpan> result) {

        // 의도: startOffsetMs = 이 Span의 시작 - root의 시작 (밀리초)
        // 폭포수 차트에서 가로축 시작 위치를 결정하는 핵심 값
        long offsetMs = node.getStartTime() != null
                ? node.getStartTime().toEpochMilli() - rootStartEpoch
                : 0L;

        result.add(new WaterfallSpan(
                node.getSpanId(),
                node.getParentSpanId(),
                node.getSpanType(),
                offsetMs,
                Optional.ofNullable(node.getDurationMs()).orElse(0L),
                depth,
                node.getHttpMethod(),
                node.getHttpUrl(),
                node.getHttpStatus(),
                node.getSqlQuery(),
                node.getExternalHost(),
                node.isError()
        ));

        // 자식 Span들을 start_time 순으로 재정렬 후 재귀
        // 의도: 형제 Span들이 시간순으로 표현되도록 정렬 후 DFS 진행
        List<SpanEntity> children = childrenMap.getOrDefault(node.getSpanId(), List.of());
        children.stream()
                .sorted((a, b) -> {
                    if (a.getStartTime() == null) return 1;
                    if (b.getStartTime() == null) return -1;
                    return a.getStartTime().compareTo(b.getStartTime());
                })
                .forEach(child -> dfs(child, childrenMap, rootStartEpoch, depth + 1, result));
    }

}