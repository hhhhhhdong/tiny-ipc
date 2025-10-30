plugins {
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    implementation(project(":tiny-ipc-server"))
}

application {
    mainClass.set("demo.WorkerMain")
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveFileName.set("worker-sample-all.jar")
}
