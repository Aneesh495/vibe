plugins {
    `java-library`
}

allprojects {
    group = "com.vibe"
    version = "2.0.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java-library")

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Xlint:-serial", "-parameters"))
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            showStandardStreams = false
        }
    }

    dependencies {
        val slf4jVersion = "2.0.16"
        val logbackVersion = "1.5.16"
        val junitVersion = "5.11.4"
        val assertjVersion = "3.27.0"

        "api"("org.slf4j:slf4j-api:$slf4jVersion")
        "testImplementation"("ch.qos.logback:logback-classic:$logbackVersion")
        "testImplementation"("org.junit.jupiter:junit-jupiter-api:$junitVersion")
        "testImplementation"("org.junit.jupiter:junit-jupiter-params:$junitVersion")
        "testRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
        "testImplementation"("org.assertj:assertj-core:$assertjVersion")
    }
}

project(":protocol") {
    dependencies {
        // Core protocol module has zero external dependencies beyond slf4j
    }
}

project(":transport-nio") {
    dependencies {
        "api"(project(":protocol"))
    }
}

project(":domain") {
    dependencies {
        "api"(project(":protocol"))
        "implementation"("org.mindrot:jbcrypt:0.4")
        "implementation"("com.fasterxml.jackson.core:jackson-databind:2.18.2")
        "implementation"("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.2")
    }
}

project(":storage-local") {
    dependencies {
        "api"(project(":protocol"))
        "api"(project(":domain"))
    }
}

project(":storage-ratis") {
    val ratisVersion = "3.1.2"
    dependencies {
        "api"(project(":protocol"))
        "api"(project(":domain"))
        "implementation"("org.apache.ratis:ratis-server:$ratisVersion")
        "implementation"("org.apache.ratis:ratis-grpc:$ratisVersion")
        "implementation"("org.apache.ratis:ratis-common:$ratisVersion")
        "implementation"("org.apache.ratis:ratis-metrics-default:$ratisVersion")
    }
}

project(":delivery") {
    dependencies {
        "api"(project(":protocol"))
        "api"(project(":domain"))
        "api"(project(":transport-nio"))
    }
}

project(":server") {
    dependencies {
        "api"(project(":protocol"))
        "api"(project(":transport-nio"))
        "api"(project(":domain"))
        "api"(project(":storage-local"))
        "api"(project(":storage-ratis"))
        "api"(project(":delivery"))
        "implementation"("ch.qos.logback:logback-classic:1.5.16")
        // Netty used solely for the browser WebSocket bridge if needed
        "implementation"("io.netty:netty-codec-http:4.1.115.Final")
        "implementation"("io.netty:netty-handler:4.1.115.Final")
        "implementation"("io.netty:netty-transport:4.1.115.Final")
    }
}

project(":sdk-java") {
    dependencies {
        "api"(project(":protocol"))
        "api"(project(":transport-nio"))
        "api"(project(":domain"))
        "implementation"("org.xerial:sqlite-jdbc:3.47.1.0")
        "testImplementation"(project(":server"))
    }
}

project(":desktop") {
    dependencies {
        "implementation"(project(":sdk-java"))
        "implementation"(project(":domain"))
        "implementation"(project(":protocol"))
        "implementation"("ch.qos.logback:logback-classic:1.5.16")
    }
}

project(":benchmarks") {
    dependencies {
        "implementation"(project(":transport-nio"))
        "implementation"(project(":protocol"))
        "implementation"(project(":storage-local"))
        "implementation"(project(":storage-ratis"))
        "implementation"(project(":domain"))
        "implementation"(project(":delivery"))
        "implementation"("org.openjdk.jmh:jmh-core:1.37")
        "annotationProcessor"("org.openjdk.jmh:jmh-generator-annprocess:1.37")
    }

    tasks.register<JavaExec>("runLoadClient") {
        group = "benchmark"
        description = "Runs the standalone VibeLoadClient"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("com.vibe.benchmarks.VibeLoadClient")
    }

    tasks.register<JavaExec>("runBenchmarks") {
        group = "benchmark"
        description = "Runs the JMH microbenchmarks"
        classpath = sourceSets["main"].runtimeClasspath
        mainClass.set("com.vibe.benchmarks.BenchmarkMain")
    }
}
