package demo;

import io.github.tinyipc.core.IpcClient;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class ClientMain {
    public static void main(String[] args) throws Exception {

        String defaultWorkerPath = Path.of("examples", "worker-sample", "build", "libs", "worker-sample-all.jar").toAbsolutePath().toString();

        // java -cp worker-sample-all.jar demo.WorkerMain 형태로 워커를 띄움
        try (IpcClient client = IpcClient.builder(Path.of("java"))
                .args(List.of("-cp", System.getProperty("worker.classpath", defaultWorkerPath), "demo.WorkerMain"))
                .maxConcurrent(8)
                .logger(System.out::println)
                .start()) {

            String pong = client.call("ping", Map.of(), String.class, Duration.ofSeconds(1));
            System.out.println("ping -> " + pong);

            Map sum = client.call("add", Map.of("a", 7, "b", 5), Map.class, Duration.ofSeconds(1));
            System.out.println("add -> " + sum);
        }
    }
}
