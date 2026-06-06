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

@Component
@RequiredArgsConstructor
public class SpanAdapter implements SpanPort {

    private final SpanRepository spanRepository;

    @Override
    public Optional<WaterfallResponse> buildWaterfall(String traceId) {
        List<SpanEntity> spans = spanRepository.findByTraceIdOrderByStartTimeAsc(traceId);
        if (spans.isEmpty()) return Optional.empty();

        // 의도: spanId → 자식 목록 맵 구성 → DFS 트리 순회용 (완전/불완전 공용)
        Map<String, List<SpanEntity>> childrenMap = spans.stream()
                .filter(s -> s.getParentSpanId() != null && !s.getParentSpanId().isBlank())
                .collect(Collectors.groupingBy(SpanEntity::getParentSpanId));

        // 의도: parent_span_id 없거나 비어있는 Span이 측정된 root
        Optional<SpanEntity> rootOpt = spans.stream()
                .filter(s -> s.getParentSpanId() == null || s.getParentSpanId().isBlank())
                .findFirst();

        if (rootOpt.isPresent()) {
            return Optional.of(buildMeasured(traceId, rootOpt.get(), childrenMap));
        }
        return Optional.of(buildRootMissing(traceId, spans, childrenMap));
    }

    // 측정된 ROOT가 있는 완전 trace. offset은 root 시작 기준 상대값으로 계산한다.
    private WaterfallResponse buildMeasured(String traceId,
                                            SpanEntity root,
                                            Map<String, List<SpanEntity>> childrenMap) {
        long rootStartEpoch = root.getStartTime().toEpochMilli();

        // 의도: DFS 재귀 순회로 트리 구조를 유지하면서 flat 리스트 생성
        // BFS 대신 DFS 선택 이유: 부모 → 자식 순서를 자연스럽게 보장
        // 자식들을 start_time 오름차순 정렬 후 방문 → 형제 Span들이 시간순으로 표현
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

    // ROOT가 없는 불완전 trace. 조립용 가짜 root를 트리 최상단에 세우고, 부모가 이 trace에
    // 없는 고아 span들을 그 자식으로 묶는다. 고아의 parentSpanId는 원래 값을 그대로 둬서
    // "원래 어떤 ROOT를 부모로 기대했는지"가 드러나게 한다(가짜 root로 덮어쓰지 않는다).
    // root 시작 기준이 없으므로 totalDurationMs·startTime·offset은 초기값으로 둔다.
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

        // 고아: parentSpanId가 이 trace의 span 집합에 없는 span. offset 기준이 없어 null 전달.
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

    private void dfs(SpanEntity node,
                     Map<String, List<SpanEntity>> childrenMap,
                     Long offsetBaseEpoch,
                     int depth,
                     List<WaterfallSpan> result) {

        // 의도: startOffsetMs = 이 Span의 시작 - root의 시작 (밀리초)
        // 폭포수 차트에서 가로축 시작 위치를 결정하는 핵심 값
        // offsetBaseEpoch가 null(ROOT 부재)이면 기준점이 없어 0으로 둔다.
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

        // 자식 Span들을 start_time 순으로 재정렬 후 재귀
        // 의도: 형제 Span들이 시간순으로 표현되도록 정렬 후 DFS 진행
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