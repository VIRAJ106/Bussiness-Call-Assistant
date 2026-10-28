dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.data.jdbc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.amqp)
    implementation(libs.postgresql)
    implementation(libs.bundles.flyway)
    implementation(libs.bundles.jjwt)
    
    // Twilio SDK for SMS
    implementation("com.twilio.sdk:twilio:9.14.1")
}
