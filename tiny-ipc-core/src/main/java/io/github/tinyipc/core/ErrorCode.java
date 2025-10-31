package io.github.tinyipc.core;

public enum ErrorCode {
    PROCESS_DIED("E_PROCESS_DIED"),
    DEADLINE_EXCEEDED("E_DEADLINE_EXCEEDED"),
    WRITE_FAILED("Write failed"),
    PROTOCOL("E_PROTOCOL"),
    BAD_PARAMS("E_BAD_PARAMS"),
    NO_SUCH_METHOD("E_NO_SUCH_METHOD"),
    INTERNAL("E_INTERNAL");

    private final String value;

    ErrorCode(String v) {
        this.value = v;
    }

    public String value() {
        return value;
    }

    public static ErrorCode from(String code) {
        if (code == null) return INTERNAL;
        for (ErrorCode c : values()) {
            if (c.value.equalsIgnoreCase(code)) return c;
        }
        return INTERNAL;
    }

    @Override
    public String toString() {
        return value;
    }
}

