package io.github.tinyipc.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class Messages {
    private static final ObjectMapper OM = new ObjectMapper();

    public static String toJsonLine(Message m) {
        try {
            return OM.writeValueAsString(m) + "\n"; // NDJSON (one line per message)
        } catch (JsonProcessingException e) {
            throw new IpcException("Failed to serialize message", e);
        }
    }

    public static Message parseLine(String line) {
        try {
            return OM.readValue(line, Message.class);
        } catch (Exception e) {
            throw new IpcException("Failed to parse message line: " + line, e);
        }
    }

    public static ObjectMapper mapper() {
        return OM;
    }
}
