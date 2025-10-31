package io.github.tinyipc.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.tinyipc.core.IpcException;
import io.github.tinyipc.core.Message;
import io.github.tinyipc.core.Messages;
import io.github.tinyipc.core.ErrorCode;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * Minimal NDJSON IPC server loop.
 * - stdin에서 한 줄(JSON)씩 읽어 RpcHandler로 디스패치하고
 * - result 또는 error(JSON) 한 줄로 stdout에 응답합니다.
 *
 * Error 처리:
 * - IllegalArgumentException -> E_BAD_PARAMS (단, 메시지가 E_NO_SUCH_METHOD*로 시작하면 E_NO_SUCH_METHOD)
 * - 그 외 -> E_INTERNAL
 * - 응답 Message.error.message 는 null/빈문자열을 방지하기 위해 safeMessage()로 보정
 */
public final class IpcServer {
    private static final ObjectMapper OM = Messages.mapper();

    private IpcServer() {}

    public static void serve(RpcHandler handler, InputStream in, OutputStream out) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                final Message req;
                try {
                    req = Messages.parseLine(line);
                } catch (Throwable parseFail) {
                    // 손상된 라인은 무시하고 다음으로 진행 (프로토콜 내구성)
                    continue;
                }

                final Message resp = new Message();
                resp.v = 1;
                resp.id = req.id;

                try {
                    final JsonNode params = (req.params == null) ? OM.nullNode() : OM.valueToTree(req.params);
                    final Object result = handler.handle(req.method, params);
                    resp.result = result;
                } catch (Throwable t) {
                    final Message.ErrorObj err = new Message.ErrorObj();
                    final ErrorCode ec = classifyThrowable(t);
                    err.code = ec.value();               // 표준화된 문자열 코드
                    err.message = safeMessage(t);        // null/빈 문자열 방지
                    resp.error = err;
                }

                writer.write(Messages.toJsonLine(resp));
                writer.flush();
            }
        } catch (IOException e) {
            // 서버 루프 자체의 I/O 실패는 상위 프로세스가 인지할 수 있도록 예외로 알림
            throw new IpcException("Server loop failed", e);
        }
    }

    // ---- Helpers ------------------------------------------------------------

    private static ErrorCode classifyThrowable(Throwable t) {
        final Throwable root = rootCause(t);

        // 잘못된 파라미터 계열
        if (root instanceof IllegalArgumentException) {
            final String msg = String.valueOf(root.getMessage());
            if (msg != null && msg.startsWith("E_NO_SUCH_METHOD")) {
                return ErrorCode.NO_SUCH_METHOD;
            }
            return ErrorCode.BAD_PARAMS;
        }

        // 인터럽트는 인터럽트 플래그를 복원하고 내부 오류로 응답
        if (root instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return ErrorCode.INTERNAL;
        }

        // TODO: 필요 시 도메인별 예외를 세분화해 매핑 추가
        return ErrorCode.INTERNAL;
    }

    private static Throwable rootCause(Throwable t) {
        Throwable x = t;
        // InvocationTargetException 등 래핑을 벗겨 실제 원인 기준으로 판단
        while (x.getCause() != null
                && (x instanceof java.lang.reflect.InvocationTargetException
                || x.getClass().getName().startsWith("java.util.concurrent"))) {
            x = x.getCause();
        }
        return x;
    }

    private static String safeMessage(Throwable t) {
        final String m = String.valueOf(t.getMessage());
        return (m == null || m.isBlank()) ? t.getClass().getSimpleName() : m;
    }
}