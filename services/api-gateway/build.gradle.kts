dependencies {
    implementation(platform(libs.spring.cloud.dependencies))
    implementation(libs.spring.cloud.starter.gateway)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.bundles.jjwt)
}
