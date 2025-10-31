package it;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.tinyipc.server.IpcServer;
import io.github.tinyipc.server.RpcHandler;

import java.util.Map;

/**
 * 테스트 전용 워커 엔트리포인트.
 * 다양한 시나리오(정상/타임아웃/비JSON/프로세스종료/에러)를 method 이름으로 분기.
 */
public final class TestWorkerMain {
    public static void main(String[] args) {
        // stdout/err 노이즈 주입 (프로토콜 오염/교착 방지 검증)
        System.err.println("stderr: boot noise");
        System.out.println("NOT_JSON_BOOTLINE"); // stdout 비-JSON 라인 (클라에서 무시해야 함)

        RpcHandler h = (method, params) -> {
            switch (method) {
                case "ping":
                    return "pong";
                case "add":
                    return Map.of("sum", params.get("a").asInt() + params.get("b").asInt());
                case "sleep":
                    // 타임아웃 유도: params.ms 만큼 슬립
                    try { Thread.sleep(params.get("ms").asLong()); } catch (InterruptedException ignored) {}
                    return "slept";
                case "noisy":
                    // 실행 중에도 stdout/stderr로 쓰지만 JSON 응답도 정상적으로 보냄
                    System.out.println("NOT_JSON_RUNTIME"); // 클라가 무시해야 함
                    System.err.println("stderr: runtime noise");
                    return Map.of("ok", true);
                case "die":
                    // 요청을 받은 즉시 프로세스 종료 → 클라가 E_PROCESS_DIED 처리해야 함
                    System.err.println("stderr: exiting now");
                    System.exit(1);
                    return null; // 도달 불가
                case "badParams":
                    // 서버가 에러 응답을 돌려주도록 예외 던짐
                    throw new IllegalArgumentException("missing/invalid params");
                default:
                    throw new IllegalArgumentException("E_NO_SUCH_METHOD: " + method);
            }
        };

        IpcServer.serve(h, System.in, System.out);
    }
}
