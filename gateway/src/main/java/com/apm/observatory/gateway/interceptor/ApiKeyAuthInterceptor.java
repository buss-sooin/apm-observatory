package com.apm.observatory.gateway.interceptor;

import com.apm.observatory.gateway.config.GatewayConfig;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

// gRPC 서버 인터셉터 — API Key 인증 담당
// 모든 RPC 호출 진입 전에 실행
// 인증 실패 시 UNAUTHENTICATED로 즉시 거부
//
// 인터셉터 선택 이유:
//   ChannelPipeline Handler 대신 gRPC 인터셉터를 쓰는 이유는
//   gRPC 프레임워크 레벨에서 인증을 처리하기 때문에
//   MonitoringServiceImpl까지 요청이 도달하기 전에 차단 가능
//
// 더 나아간다면 인증 방식 자체를 고도화해야 할 것 같음
//   테넌트별 API Key를 DB에서 조회 + 캐싱
//   만료 시간, 요청 횟수 제한(Rate Limiting) 추가
public class ApiKeyAuthInterceptor implements ServerInterceptor {

    // API Key 메타데이터 키 정의
    // ASCII_STRING_MARSHALLER: 문자열 직렬화 방식
    private static final Metadata.Key<String> API_KEY_METADATA =
            Metadata.Key.of(GatewayConfig.API_KEY_HEADER,
                    Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String apiKey = headers.get(API_KEY_METADATA);

        // API Key 검증
        if (apiKey == null || !apiKey.equals(GatewayConfig.API_KEY)) {
            // 인증 실패 → UNAUTHENTICATED 즉시 반환
            // 인증 실패가 반복된다면 이상 접근으로 판단하고 차단하는 방식도 필요할 것 같음
            call.close(
                    Status.UNAUTHENTICATED.withDescription("유효하지 않은 API Key"),
                    new Metadata()
            );
            // 빈 리스너 반환 — 이후 요청 처리 없음
            return new ServerCall.Listener<>() {};
        }

        // 인증 성공 → 다음 인터셉터 또는 서비스로 전달
        return next.startCall(call, headers);
    }

}