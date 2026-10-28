// dayclose-web-starter: Auto-config for problem+json, correlation-id, tenant context, idempotency filter (§4.4)

dependencies {
    implementation(project(":libs:dayclose-dto"))
    implementation(libs.spring.boot.starter.web)
    implementation(libs.jackson.databind)
    implementation(libs.jackson.datatype.jsr310)
    compileOnly(libs.spring.boot.starter.security)
}
