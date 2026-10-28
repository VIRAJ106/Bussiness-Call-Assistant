dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jdbc)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.bundles.jjwt)
    implementation(libs.postgresql)
    implementation(libs.bundles.flyway)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
