package io.github.tinyipc.core;

public class IpcException extends RuntimeException {
    private final ErrorCode code; // nullable 허용(호환성)

    public IpcException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }
    public IpcException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public IpcException(String message) {
        super(message);
        this.code = null; // 또는 ErrorCode.INTERNAL
    }
    public IpcException(String message, Throwable cause) {
        super(message, cause);
        this.code = null; // 또는 ErrorCode.INTERNAL
    }

    public ErrorCode code() { return code; }
    public boolean is(ErrorCode c) { return code == c; }

    public static IpcException processDied(String msg, Throwable cause) {
        return new IpcException(ErrorCode.PROCESS_DIED, msg, cause);
    }
    public static IpcException deadlineExceeded(String msg, Throwable cause) {
        return new IpcException(ErrorCode.DEADLINE_EXCEEDED, msg, cause);
    }
    public static IpcException writeFailed(Throwable cause) {
        return new IpcException(ErrorCode.WRITE_FAILED, "Write failed", cause);
    }
    public static IpcException protocol(String msg, Throwable cause) {
        return new IpcException(ErrorCode.PROTOCOL, msg, cause);
    }
    public static IpcException badParams(String msg) {
        return new IpcException(ErrorCode.BAD_PARAMS, msg);
    }
    public static IpcException noSuchMethod(String msg) {
        return new IpcException(ErrorCode.NO_SUCH_METHOD, msg);
    }
    public static IpcException internal(String msg, Throwable cause) {
        return new IpcException(ErrorCode.INTERNAL, msg, cause);
    }
}

