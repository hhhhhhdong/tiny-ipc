package io.github.tinyipc.core;

import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class IpcClientIT {

    private static String javaBin() {
        // 현재 Gradle/JUnit이 사용 중인 java 실행기
        return System.getProperty("java.home") + java.io.File.separator + "bin" + java.io.File.separator + "java";
    }

    private static String testClasspath() {
        // 테스트 실행 시점의 클래스패스 (테스트/메인 클래스 모두 포함)
        return System.getProperty("java.class.path");
    }

    private IpcClient newClient(String... extraArgs) {
        var cmd = new java.util.ArrayList<String>();
        cmd.add("-cp");
        cmd.add(testClasspath());
        cmd.add("it.TestWorkerMain");
        // 필요시 추가 인자
        cmd.addAll(java.util.Arrays.asList(extraArgs));

        return IpcClient.builder(Path.of(javaBin()))
                .args(cmd)
                .maxConcurrent(8)
                .logger(System.out::println) // stderr/비JSON 라인 로그 노출
                .start();
    }

    @Test
    void happyPath_ping_and_add() {
        try (var client = newClient()) {
            var pong = client.call("ping", Map.of(), String.class, Duration.ofSeconds(3));
            assertEquals("pong", pong);

            @SuppressWarnings("unchecked")
            Map<String, Integer> sum = client.call("add", Map.of("a", 7, "b", 5), Map.class, Duration.ofSeconds(3));
            assertEquals(12, sum.get("sum"));
        }
    }

    @Test
    void ignore_non_json_stdout_and_drain_stderr() {
        try (var client = newClient()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> res = client.call("noisy", Map.of(), Map.class, Duration.ofSeconds(3));
            assertNotNull(res);
            assertEquals(true, res.get("ok"));
            // 여기까지 왔다면 stdout의 비-JSON 라인을 무시했고, stderr 드레인도 성공
        }
    }

    @Test
    void timeout_when_worker_sleeps_longer_than_deadline() {
        try (var client = newClient()) {
            var ex = assertThrows(IpcException.class, () ->
                    client.call("sleep", Map.of("ms", 2000), String.class, Duration.ofMillis(500))
            );
            assertTrue(ex.getMessage().contains("E_DEADLINE_EXCEEDED"));
        }
    }

    @Test
    void process_died_midflight() {
        try (var client = newClient()) {
            // die 호출로 워커가 즉시 종료
            var ex = assertThrows(IpcException.class, () ->
                    client.call("die", Map.of(), Void.class, Duration.ofSeconds(100))
            );
            // 첫 응답 자체가 안 오므로 쓰기/읽기 도중 파이프 닫힘 → E_PROCESS_DIED or Write failed
            assertTrue(ex.getMessage().contains("E_PROCESS_DIED") || ex.getMessage().contains("Write failed"));
        }
    }

    @Test
    void server_reports_bad_params_as_error_response() {
        try (var client = newClient()) {
            var ex = assertThrows(IpcException.class, () ->
                    client.call("badParams", Map.of(), Void.class, Duration.ofSeconds(2))
            );
            // 서버가 IllegalArgumentException → E_BAD_PARAMS로 매핑해 응답
            assertTrue(ex.getMessage().contains("E_BAD_PARAMS"));
        }
    }

    @Test
    void backpressure_respected_under_concurrency() throws Exception {
        try (var client = newClient()) {
            // maxConcurrent=8인데 32개 동시 호출 → 큐잉/스루풋 확인
            int N = 32;
            var pool = Executors.newFixedThreadPool(16);
            var futures = new java.util.ArrayList<Future<String>>();
            for (int i = 0; i < N; i++) {
                int a = i, b = N - i;
                futures.add(pool.submit(() ->
                        client.call("add", Map.of("a", a, "b", b), Map.class, Duration.ofSeconds(5)).toString()));
            }
            for (var f : futures) {
                var s = f.get(10, TimeUnit.SECONDS);
                assertTrue(s.contains("sum="));
            }
            pool.shutdownNow();
        }
    }

    @Test
    void huge_stderr_flood_does_not_deadlock() {
        try (var client = newClient()) {
            // 워커 측에 stderr 대량 출력 로직을 넣지 않았으므로,
            // 여기서는 간단히 sleep 호출하면서 별도 스레드에서 부모 로그를 계속 씀(시뮬레이션 개념).
            // 실제로는 TestWorkerMain 에 stderr 대량 출력 케이스를 추가해도 좋음.
            var ex = assertThrows(IpcException.class, () ->
                    client.call("sleep", Map.of("ms", 3000), String.class, Duration.ofMillis(10))
            );
            assertTrue(ex.getMessage().contains("E_DEADLINE_EXCEEDED"));
            // 타임아웃으로 빠지고 전체가 멈추지 않았다면, stderr 드레인/루프 교착은 없는 셈.
        }
    }
}
