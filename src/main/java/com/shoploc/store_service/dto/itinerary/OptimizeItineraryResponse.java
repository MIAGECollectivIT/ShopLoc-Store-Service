package com.shoploc.store_service.dto.itinerary;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Résultat complet de l'itinéraire optimisé Click & Collect multi-boutiques")
public record OptimizeItineraryResponse(
    @Schema(description = "Point de départ de la tournée") ItineraryOriginResponse origin,
    @Schema(description = "Dernière boutique de la tournée") ItineraryStepResponse destination,
    @Schema(description = "Liste chronologique et ordonnée des étapes de retrait")
        List<ItineraryStepResponse> orderedSteps,
    @Schema(
            description =
                "Distance totale parcourue en kilomètres (ajustée du facteur de sinuosité urbaine 1.35)",
            example = "4.8")
        double totalDistanceKm,
    @Schema(
            description =
                "Durée totale estimée en minutes (trajets + temps forfaitaire de retrait par étape)",
            example = "24")
        int estimatedDurationMinutes,
    @Schema(description = "Mode de transport utilisé pour le calcul") TransportMode transportMode,
    @Schema(
            description = "Lien de redirection Google Maps pré-rempli et prêt à l'emploi",
            example =
                "https://www.google.com/maps/dir/?api=1&origin=50.62925,3.057256&destination=50.6356,3.0617&travelmode=driving")
        String googleMapsUrl,
    @Schema(
            description =
                "Indique si la liste des waypoints a été plafonnée à 9 étapes pour Google Maps")
        boolean waypointsTruncated,
    @Schema(description = "Liste d'avertissements éventuels (ex: commerces révoqués ou fermés)")
        List<String> warnings) {}
