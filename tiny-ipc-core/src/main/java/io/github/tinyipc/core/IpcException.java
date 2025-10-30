package io.github.tinyipc.core;

public class IpcException extends RuntimeException {
    public IpcException(String message) { super(message); }
    public IpcException(String message, Throwable cause) { super(message, cause); }
}
