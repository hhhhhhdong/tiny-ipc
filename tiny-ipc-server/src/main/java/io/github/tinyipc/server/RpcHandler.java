package io.github.tinyipc.server;

import com.fasterxml.jackson.databind.JsonNode;

public interface RpcHandler {
    Object handle(String method, JsonNode params) throws Exception;
}
