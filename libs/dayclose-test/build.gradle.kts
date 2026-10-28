// dayclose-test: Testcontainers base classes and fixtures (§4.3)

dependencies {
    implementation(libs.spring.boot.starter.test)
    implementation(libs.bundles.testcontainers)
    implementation(libs.rest.assured)
    implementation(libs.archunit.junit5)
    implementation(libs.jqwik)
    implementation(libs.postgresql)
}
