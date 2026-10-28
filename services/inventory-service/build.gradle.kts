dependencies {
    implementation(project(":libs:dayclose-events"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jdbc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.amqp)
    implementation(libs.postgresql)
    implementation(libs.bundles.flyway)
    implementation(libs.bundles.jjwt)
    implementation(libs.resilience4j.spring.boot3)
}
