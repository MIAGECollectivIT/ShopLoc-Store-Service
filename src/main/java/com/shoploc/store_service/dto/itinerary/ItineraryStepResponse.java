package com.shoploc.store_service.dto.itinerary;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Étape ordonnée d'une tournée de retrait Click & Collect")
public record ItineraryStepResponse(
    @Schema(description = "Numéro d'ordre de l'étape", example = "1") int stepNumber,
    @Schema(
            description = "Identifiant unique de la boutique",
            example = "11111111-1111-1111-1111-111111111111")
        UUID storeUid,
    @Schema(description = "Nom de la boutique", example = "Burger King Lille") String storeName,
    @Schema(description = "Adresse postale", example = "10 Rue Nationale, 59000 Lille")
        String address,
    @Schema(description = "Latitude GPS", example = "50.6356") Double latitude,
    @Schema(description = "Longitude GPS", example = "3.0617") Double longitude,
    @Schema(
            description = "Distance parcourue depuis l'étape précédente en kilomètres (ajustée sinuosité)",
            example = "1.2")
        double distanceFromPreviousKm,
    @Schema(description = "Temps de trajet estimé depuis l'étape précédente en minutes", example = "4")
        int travelTimeMinutes,
    @Schema(description = "Heure d'arrivée estimée à la boutique (fuseau Europe/Paris)")
        Instant estimatedArrivalTime,
    @Schema(description = "Indique si la boutique est ouverte lors de l'arrivée estimée")
        boolean isOpenAtArrival,
    @Schema(description = "Indique si la boutique est fermée toute la journée à la date de passage")
        boolean isClosedAllDay,
    @Schema(
            description =
                "Indique si cette boutique est à la même adresse que l'étape précédente (retrait groupé)")
        boolean isSameLocationAsPrevious,
    @Schema(description = "Plage horaire d'ouverture du jour", example = "09:00 - 19:00")
        String scheduleInfo,
    @Schema(
            description = "Alerte éventuelle si fermeture imminente ou boutique fermée",
            example = "Attention : fermeture dans 25 minutes")
        String warningAlert) {}
