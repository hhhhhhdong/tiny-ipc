# tiny-ipc (Java 17+)
Lightweight JSON-RPC over stdin/stdout for parent-child Java processes.

## Features (v0.1)
- NDJSON framing (one line per message)
- Minimal client API (`IpcClient`) with concurrency control
- Server helper (`IpcServer`, `RpcHandler`)
- Jackson-based (object mapping)

## Modules
- `tiny-ipc-core`: client, protocol, utilities
- `tiny-ipc-server`: server loop helper
- `examples:worker-sample`: demo worker process
- `examples:parent-sample`: demo parent caller

## Quickstart
```bash
# build all
./gradlew clean build

# run worker (fat jar 권장)
# 예: ./gradlew :examples:worker-sample:installDist && ./examples/worker-sample/build/install/worker-sample/bin/worker-sample

# run parent
./gradlew :examples:parent-sample:run \
  -Dworker.classpath=examples/worker-sample/build/libs/worker-sample-all.jar