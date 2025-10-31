package demo;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.tinyipc.server.IpcServer;
import io.github.tinyipc.server.RpcHandler;

public class WorkerMain {
    public static void main(String[] args) {
        RpcHandler handler = (method, params) -> {
            switch (method) {
                case "ping":
                    return "pong";
                case "add":
                    return java.util.Map.of("sum", params.get("a").asInt() + params.get("b").asInt());
                case "__shutdown__":
                    System.exit(0);
                    return null;
                default:
                    throw new IllegalArgumentException("E_NO_SUCH_METHOD: " + method);
            }
        };
        IpcServer.serve(handler, System.in, System.out);
    }
}
