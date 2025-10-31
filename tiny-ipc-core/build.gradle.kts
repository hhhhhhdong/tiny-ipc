dependencies {
    api("com.fasterxml.jackson.core:jackson-databind:${property("jacksonVersion")}")
    api("org.slf4j:slf4j-api:${property("slf4jVersion")}")

    testImplementation("org.junit.jupiter:junit-jupiter:${property("junitVersion")}")
    testImplementation(project(":tiny-ipc-server"))
}