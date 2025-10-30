package io.github.tinyipc.core;

import java.util.UUID;

final class Util {
    static String newId() { return UUID.randomUUID().toString(); }
    private Util() {}
}
