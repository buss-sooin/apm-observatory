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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * traceId로 모은 flat Span row를 조회 시점에 트리로 조립해 폭포수 응답을 만든다(SpanPort 구현).
 * 측정된 ROOT가 있으면 완전 trace로, 없으면 조립용 가짜 root를 세운 불완전 trace로 분기한다.
 */
@Component
@RequiredArgsConstructor
public class SpanAdapter implements SpanPort {

    private final SpanRepository spanRepository;

    @Override
    public Optional<WaterfallResponse> buildWaterfall(String traceId) {
        List<SpanEntity> spans = spanRepository.findByTraceIdOrderByStartTimeAsc(traceId);
        if (spans.isEmpty()) return Optional.empty();

        // spanId → 자식 목록 맵 (완전·불완전 조립 공용)
        Map<String, List<SpanEntity>> childrenMap = spans.stream()
                .filter(s -> s.getParentSpanId() != null && !s.getParentSpanId().isBlank())
                .collect(Collectors.groupingBy(SpanEntity::getParentSpanId));

        // parentSpanId가 없거나 비면 측정된 ROOT
        Optional<SpanEntity> rootOpt = spans.stream()
                .filter(s -> s.getParentSpanId() == null || s.getParentSpanId().isBlank())
                .findFirst();

        if (rootOpt.isPresent()) {
            return Optional.of(buildMeasured(traceId, rootOpt.get(), childrenMap));
        }
        return Optional.of(buildRootMissing(traceId, spans, childrenMap));
    }

    /**
     * 측정된 ROOT가 있는 완전 trace를 조립한다. offset은 root 시작을 기준으로 한 상대값이다.
     * 부모 → 자식 순서를 자연스럽게 보장하려고 BFS가 아닌 DFS로 순회하고, 자식은 start_time
     * 오름차순으로 정렬해 형제 Span이 시간순으로 나열되게 한다.
     */
    private WaterfallResponse buildMeasured(String traceId,
                                            SpanEntity root,
                                            Map<String, List<SpanEntity>> childrenMap) {
        long rootStartEpoch = root.getStartTime().toEpochMilli();

        List<WaterfallSpan> result = new ArrayList<>();
        dfs(root, childrenMap, rootStartEpoch, 0, result);

        return new WaterfallResponse(
                traceId,
                "MEASURED",
                Optional.ofNullable(root.getDurationMs()).orElse(0L),
                root.getStartTime(),
                result
        );
    }

    /**
     * ROOT가 없는 불완전 trace를 조립한다. 조립용 가짜 root를 트리 최상단에 세우고, 부모가 이
     * trace에 없는 고아 Span을 그 자식으로 묶는다. 고아의 parentSpanId는 원래 값을 그대로 둬서
     * 원래 어떤 ROOT를 부모로 기대했는지 드러나게 한다(가짜 root로 덮어쓰지 않는다).
     * root 시작 기준이 없으므로 totalDurationMs·startTime·offset은 초기값으로 둔다.
     */
    private WaterfallResponse buildRootMissing(String traceId,
                                               List<SpanEntity> spans,
                                               Map<String, List<SpanEntity>> childrenMap) {
        Set<String> idSet = spans.stream()
                .map(SpanEntity::getSpanId)
                .collect(Collectors.toSet());

        List<WaterfallSpan> result = new ArrayList<>();
        result.add(new WaterfallSpan(
                "synthetic-root-" + traceId, null, "ROOT",
                0L, 0L, 0,
                null, null, null, null, null, false
        ));

        // 고아 = parentSpanId가 이 trace 집합에 없는 Span. offset 기준이 없어 null 전달.
        spans.stream()
                .filter(s -> s.getParentSpanId() == null
                        || s.getParentSpanId().isBlank()
                        || !idSet.contains(s.getParentSpanId()))
                .sorted((a, b) -> {
                    if (a.getStartTime() == null) return 1;
                    if (b.getStartTime() == null) return -1;
                    return a.getStartTime().compareTo(b.getStartTime());
                })
                .forEach(orphan -> dfs(orphan, childrenMap, null, 1, result));

        return new WaterfallResponse(traceId, "MISSING", 0L, null, result);
    }

    /**
     * 노드를 WaterfallSpan으로 변환하고 자식을 start_time 순으로 재귀 방문한다.
     * startOffsetMs는 이 Span의 시작에서 root 시작을 뺀 밀리초로, 폭포수 차트의 가로축 시작
     * 위치를 정한다. offsetBaseEpoch가 null(ROOT 부재)이면 기준점이 없어 0으로 둔다.
     */
    private void dfs(SpanEntity node,
                     Map<String, List<SpanEntity>> childrenMap,
                     Long offsetBaseEpoch,
                     int depth,
                     List<WaterfallSpan> result) {

        long offsetMs = (offsetBaseEpoch != null && node.getStartTime() != null)
                ? node.getStartTime().toEpochMilli() - offsetBaseEpoch
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

        List<SpanEntity> children = childrenMap.getOrDefault(node.getSpanId(), List.of());
        children.stream()
                .sorted((a, b) -> {
                    if (a.getStartTime() == null) return 1;
                    if (b.getStartTime() == null) return -1;
                    return a.getStartTime().compareTo(b.getStartTime());
                })
                .forEach(child -> dfs(child, childrenMap, offsetBaseEpoch, depth + 1, result));
    }

}