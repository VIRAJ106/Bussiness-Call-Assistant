rootProject.name = "dayclose"

// Shared libraries
include("libs:dayclose-events")
include("libs:dayclose-dto")
include("libs:dayclose-web-starter")
include("libs:dayclose-test")

// Services (ADR-0001)
include("services:api-gateway")
include("services:auth-service")
include("services:inventory-service")
include("services:commitment-service")
include("services:knowledge-service")
include("services:notification-service")
include("services:voice-agent-service")
