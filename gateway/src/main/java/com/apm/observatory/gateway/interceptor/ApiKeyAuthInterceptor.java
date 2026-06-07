package com.apm.observatory.gateway.interceptor;

import com.apm.observatory.gateway.config.GatewayConfig;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

/**
 * 모든 RPC 진입 전에 API Key를 검증하는 gRPC 서버 인터셉터.
 * 인증에 실패하면 {@link Status#UNAUTHENTICATED}로 즉시 거부한다.
 *
 * <p>gRPC 인터셉터는 Netty 전송 계층 위에서 동작한다. 프레임워크가 메타데이터를 파싱한 뒤
 * RPC 단위로 가로채므로, {@code MonitoringServiceImpl}까지 요청이 도달하기 전에 차단한다.
 */
public class ApiKeyAuthInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> API_KEY_METADATA =
            Metadata.Key.of(GatewayConfig.API_KEY_HEADER,
                    Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String apiKey = headers.get(API_KEY_METADATA);

        if (apiKey == null || !apiKey.equals(GatewayConfig.API_KEY)) {
            call.close(
                    Status.UNAUTHENTICATED.withDescription("유효하지 않은 API Key"),
                    new Metadata()
            );
            // close() 이후 콜백을 받지 않도록 빈 리스너를 반환한다
            return new ServerCall.Listener<>() {};
        }

        return next.startCall(call, headers);
    }

}
