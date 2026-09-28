package fr.miage.collectivit.storeservice.controller;

import fr.miage.collectivit.storeservice.config.OpenApiConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controller exposing store service status and health information with OpenAPI documentation. */
@RestController
@RequestMapping("/api/v1/stores")
@Tag(name = "Store Status", description = "Operations related to store service status")
public class StoreStatusController {

  @GetMapping("/status")
  @Operation(
      summary = "Check service status",
      description = "Returns the current running status of the store service",
      security = @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME))
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Service is active and operational")
      })
  public ResponseEntity<Map<String, String>> getStatus() {
    return ResponseEntity.ok(Map.of("service", "store-service", "status", "UP"));
  }
}
