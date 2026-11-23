package com.shoploc.store_service.dto.itinerary;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Mode de transport utilisé pour le calcul de l'itinéraire Click & Collect")
public enum TransportMode {
  @Schema(description = "Déplacement en voiture")
  DRIVING("driving", 30.0, 5),

  @Schema(description = "Déplacement à pied")
  WALKING("walking", 4.5, 3),

  @Schema(description = "Déplacement à vélo")
  BICYCLE("bicycling", 15.0, 3);

  private final String googleMapsMode;
  private final double averageSpeedKmH;
  private final int stopHandlingMinutes;

  TransportMode(String googleMapsMode, double averageSpeedKmH, int stopHandlingMinutes) {
    this.googleMapsMode = googleMapsMode;
    this.averageSpeedKmH = averageSpeedKmH;
    this.stopHandlingMinutes = stopHandlingMinutes;
  }

  public String getGoogleMapsMode() {
    return googleMapsMode;
  }

  public double getAverageSpeedKmH() {
    return averageSpeedKmH;
  }

  public int getStopHandlingMinutes() {
    return stopHandlingMinutes;
  }
}
