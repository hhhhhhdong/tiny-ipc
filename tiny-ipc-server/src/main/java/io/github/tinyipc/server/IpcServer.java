package io.github.tinyipc.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.tinyipc.core.Message;
import io.github.tinyipc.core.Messages;
import io.github.tinyipc.core.IpcException;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class IpcServer {
    private static final ObjectMapper OM = Messages.mapper();

    public static void serve(RpcHandler handler, InputStream in, OutputStream out) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                Message req;
                try {
                    req = Messages.parseLine(line);
                } catch (Exception e) {
                    continue; // ignore broken lines
                }

                Message resp = new Message();
                resp.v = 1;
                resp.id = req.id;

                try {
                    JsonNode params = req.params == null ? OM.nullNode() : OM.valueToTree(req.params);
                    Object result = handler.handle(req.method, params);
                    resp.result = result;
                } catch (Throwable t) {
                    Message.ErrorObj err = new Message.ErrorObj();
                    err.code = (t instanceof IllegalArgumentException) ? "E_BAD_PARAMS" : "E_INTERNAL";
                    err.message = t.getMessage();
                    resp.error = err;
                }

                writer.write(Messages.toJsonLine(resp));
                writer.flush();
            }
        } catch (IOException e) {
            throw new IpcException("Server loop failed", e);
        }
    }

    private IpcServer() {
    }
}
