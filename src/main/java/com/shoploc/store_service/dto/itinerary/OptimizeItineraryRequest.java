package com.shoploc.store_service.dto.itinerary;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Requête d'optimisation d'itinéraire Click & Collect multi-boutiques")
public record OptimizeItineraryRequest(
    @Schema(
            description = "Liste des identifiants uniques (UUID) des boutiques à visiter",
            example = "[\"11111111-1111-1111-1111-111111111111\", \"22222222-2222-2222-2222-222222222222\"]")
        @NotEmpty(message = "La liste des boutiques partenaires ne peut pas être vide")
        List<UUID> storeUids,
    @Schema(description = "Latitude du point de départ du client", example = "50.62925")
        Double originLatitude,
    @Schema(description = "Longitude du point de départ du client", example = "3.057256")
        Double originLongitude,
    @Schema(
            description = "Adresse textuelle facultative du point de départ",
            example = "Grand Place, 59000 Lille")
        String originAddress,
    @Schema(description = "Mode de transport choisi", defaultValue = "DRIVING")
        TransportMode transportMode,
    @Schema(
            description = "Heure de départ souhaitée (ISO Instant). Si omise, l'heure courante est utilisée.")
        Instant departureTime) {

  public OptimizeItineraryRequest {
    if (transportMode == null) {
      transportMode = TransportMode.DRIVING;
    }
    if (departureTime == null) {
      departureTime = Instant.now();
    }
  }
}
