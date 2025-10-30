package io.github.tinyipc.core;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Message {
    public int v;           // protocol version
    public String id;       // correlation id
    public String method;   // request only
    public Object params;   // request only
    public Object result;   // response only
    public ErrorObj error;  // response only

    public static final class ErrorObj {
        public String code;
        public String message;
        public Object data;
    }
}
