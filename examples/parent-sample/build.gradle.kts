plugins { application }

dependencies {
    implementation(project(":tiny-ipc-core"))
}

application {
    mainClass.set("demo.ClientMain")
    applicationDefaultJvmArgs = listOf(
        "-Dworker.classpath=${project.rootProject.projectDir}/examples/worker-sample/build/libs/worker-sample-all.jar"
    )
}
