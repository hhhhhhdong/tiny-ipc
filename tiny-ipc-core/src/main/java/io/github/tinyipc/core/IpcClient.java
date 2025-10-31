package io.github.tinyipc.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Minimal client that launches a worker process and exchanges NDJSON messages over stdin/stdout.
 */
public final class IpcClient implements AutoCloseable {
    private final Process process;
    private final BufferedWriter writer;
    private final BufferedReader reader;
    private final ExecutorService readLoop = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "tiny-ipc-read");
        t.setDaemon(true);
        return t;
    });
    private final ExecutorService writeLoop = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "tiny-ipc-write");
        t.setDaemon(true);
        return t;
    });

    private final ConcurrentMap<String, CompletableFuture<Message>> pending = new ConcurrentHashMap<>();
    private final Semaphore inFlight;
    private final Consumer<String> stdLogger;

    private volatile boolean closed = false;

    public static Builder builder(Path command) {
        return new Builder(command);
    }

    private IpcClient(Process process, int maxConcurrent, Consumer<String> stdLogger) {
        this.process = process;
        this.writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        this.reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        this.inFlight = new Semaphore(Math.max(1, maxConcurrent));
        this.stdLogger = stdLogger != null ? stdLogger : s -> {
        };
        startReadLoop();
    }

    private void startReadLoop() {
        readLoop.submit(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    Message m = Messages.parseLine(line);
                    if (m.id != null) {
                        CompletableFuture<Message> f = pending.remove(m.id);
                        if (f != null) {
                            f.complete(m);
                        }
                    }
                }
                // EOF: process likely died or closed stream
                failAll(new IpcException("E_PROCESS_DIED: worker stream closed"));
            } catch (Throwable t) {
                failAll(new IpcException("E_PROTOCOL: read loop error", t));
            }
        });
    }

    private void failAll(Throwable t) {
        pending.values().forEach(f -> f.completeExceptionally(t));
        pending.clear();
    }

    public <T> T call(String method, Object params, Class<T> resultType, Duration timeout) {
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(resultType, "resultType");
        Objects.requireNonNull(timeout, "timeout");
        if (closed) throw new IpcException("Client is closed");

        try {
            inFlight.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IpcException("Interrupted acquiring inFlight permit", e);
        }

        String id = Util.newId();
        Message req = new Message();
        req.v = 1;
        req.id = id;
        req.method = method;
        req.params = params;

        CompletableFuture<Message> future = new CompletableFuture<>();
        pending.put(id, future);

        writeLoop.submit(() -> {
            try {
                writer.write(Messages.toJsonLine(req));
                writer.flush();
            } catch (IOException e) {
                CompletableFuture<Message> f = pending.remove(id);
                if (f != null) f.completeExceptionally(new IpcException("Write failed", e));
            }
        });

        try {
            Message resp = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (resp.error != null) {
                throw new IpcException(resp.error.code + ": " + resp.error.message);
            }
            if (resultType == Void.class || resp.result == null) return null;
            return Messages.mapper().convertValue(resp.result, resultType);
        } catch (TimeoutException te) {
            pending.remove(id);
            throw new IpcException("E_DEADLINE_EXCEEDED: call timed out", te);
        } catch (ExecutionException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IpcException("Call failed", e);
        } finally {
            inFlight.release();
        }
    }

    @Override
    public void close() {
        closed = true;
        try {
            writer.write("{\"v\":1,\"id\":\"" + Util.newId() + "\",\"method\":\"__shutdown__\"}\n");
            writer.flush();
        } catch (Exception ignore) {
        }
        try {
            writer.close();
        } catch (Exception ignore) {
        }
        try {
            reader.close();
        } catch (Exception ignore) {
        }
        try {
            process.waitFor(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        process.destroy();
        readLoop.shutdownNow();
        writeLoop.shutdownNow();
    }

    // --------------------- Builder ---------------------
    public static final class Builder {
        private final Path command;
        private List<String> args = List.of();
        private Map<String, String> env = Map.of();
        private File workingDir;
        private int maxConcurrent = 16;
        private Consumer<String> stdLogger;

        private Builder(Path command) {
            this.command = command;
        }

        public Builder args(List<String> args) {
            this.args = args;
            return this;
        }

        public Builder env(Map<String, String> env) {
            this.env = env;
            return this;
        }

        public Builder workingDir(File dir) {
            this.workingDir = dir;
            return this;
        }

        public Builder maxConcurrent(int n) {
            this.maxConcurrent = n;
            return this;
        }

        public Builder logger(Consumer<String> l) {
            this.stdLogger = l;
            return this;
        }

        public IpcClient start() {
            try {
                List<String> cmd = new ArrayList<>();
                cmd.add(command.toString());
                cmd.addAll(args);
                ProcessBuilder pb = new ProcessBuilder(cmd);
                if (workingDir != null) pb.directory(workingDir);
                if (env != null && !env.isEmpty()) pb.environment().putAll(env);
                pb.redirectErrorStream(true); // merge stderr into stdout
                Process p = pb.start();
                return new IpcClient(p, maxConcurrent, stdLogger);
            } catch (IOException e) {
                throw new IpcException("Failed to start process", e);
            }
        }
    }
}
