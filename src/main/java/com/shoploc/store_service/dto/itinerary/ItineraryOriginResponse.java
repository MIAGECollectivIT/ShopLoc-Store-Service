package com.shoploc.store_service.dto.itinerary;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Point de départ de la tournée Click & Collect")
public record ItineraryOriginResponse(
    @Schema(description = "Libellé de l'adresse de départ", example = "Grand Place, 59000 Lille")
        String address,
    @Schema(description = "Latitude du point de départ", example = "50.62925") Double latitude,
    @Schema(description = "Longitude du point de départ", example = "3.057256") Double longitude,
    @Schema(description = "Indique si l'origine a été fournie explicitement par le client")
        boolean customOrigin) {}
