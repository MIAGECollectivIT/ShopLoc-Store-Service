package com.shoploc.store_service.service;

import com.shoploc.store_service.dto.itinerary.ItineraryOriginResponse;
import com.shoploc.store_service.dto.itinerary.ItineraryStepResponse;
import com.shoploc.store_service.dto.itinerary.TransportMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Standardized builder for Google Maps Directions API URLs (SLE-31, SLE-298).
 * Enforces:
 * 1. Compact latitude,longitude format (ex: 50.635600,3.061700).
 * 2. UTF-8 fallback with '+' for spaces on textual addresses.
 * 3. Exact 0 waypoints parameter when there is only 1 destination store.
 * 4. Deduplication of consecutive identical locations.
 * 5. Strict ceiling of 9 waypoints max for multi-destination routes.
 */
@Component
public class GoogleMapsUrlBuilder {

  public static final int MAX_GOOGLE_MAPS_WAYPOINTS = 9;
  public static final String GOOGLE_MAPS_BASE_URL = "https://www.google.com/maps/dir/?api=1";

  public record UrlBuildResult(String url, boolean waypointsTruncated) {}

  public UrlBuildResult buildDirectionsUrl(
      ItineraryOriginResponse origin,
      List<ItineraryStepResponse> orderedSteps,
      TransportMode transportMode) {

    if (orderedSteps == null || orderedSteps.isEmpty()) {
      return new UrlBuildResult(GOOGLE_MAPS_BASE_URL, false);
    }

    StringBuilder url = new StringBuilder(GOOGLE_MAPS_BASE_URL);

    // 1. Origin parameter
    String originLocation = formatOrigin(origin, orderedSteps.get(0));
    if (originLocation != null && !originLocation.isBlank()) {
      url.append("&origin=").append(originLocation);
    }

    // 2. Single store case (N = 1): no waypoints parameter
    if (orderedSteps.size() == 1) {
      String destinationLocation = formatStepLocation(orderedSteps.get(0));
      url.append("&destination=").append(destinationLocation);
      appendTravelMode(url, transportMode);
      return new UrlBuildResult(url.toString(), false);
    }

    // 3. Multi-store case (N > 1):
    // The last step is the destination
    ItineraryStepResponse lastStep = orderedSteps.get(orderedSteps.size() - 1);
    String destinationLocation = formatStepLocation(lastStep);
    url.append("&destination=").append(destinationLocation);

    // Intermediate steps become waypoints (from index 0 up to size - 2, or index 1 if custom origin was omitted)
    List<String> rawWaypoints = new ArrayList<>();
    int startIndex = (origin != null && origin.customOrigin()) ? 0 : 1;
    for (int i = startIndex; i < orderedSteps.size() - 1; i++) {
      rawWaypoints.add(formatStepLocation(orderedSteps.get(i)));
    }

    // Deduplicate consecutive identical waypoints (colocalized stores at same address/coords)
    List<String> distinctConsecutiveWaypoints = new ArrayList<>();
    String previousLocation = originLocation;
    for (String waypoint : rawWaypoints) {
      if (!waypoint.equalsIgnoreCase(previousLocation) && !waypoint.equalsIgnoreCase(destinationLocation)) {
        distinctConsecutiveWaypoints.add(waypoint);
        previousLocation = waypoint;
      }
    }

    boolean waypointsTruncated = false;
    if (distinctConsecutiveWaypoints.size() > MAX_GOOGLE_MAPS_WAYPOINTS) {
      distinctConsecutiveWaypoints = distinctConsecutiveWaypoints.subList(0, MAX_GOOGLE_MAPS_WAYPOINTS);
      waypointsTruncated = true;
    }

    if (!distinctConsecutiveWaypoints.isEmpty()) {
      url.append("&waypoints=");
      for (int i = 0; i < distinctConsecutiveWaypoints.size(); i++) {
        if (i > 0) {
          url.append("%7C"); // URL-encoded pipe '|'
        }
        url.append(distinctConsecutiveWaypoints.get(i));
      }
    }

    appendTravelMode(url, transportMode);
    return new UrlBuildResult(url.toString(), waypointsTruncated);
  }

  private String formatOrigin(ItineraryOriginResponse origin, ItineraryStepResponse firstStep) {
    if (origin != null) {
      if (origin.latitude() != null && origin.longitude() != null) {
        return formatCoordinates(origin.latitude(), origin.longitude());
      }
      if (origin.address() != null && !origin.address().isBlank()) {
        return encodeAddressFallback(origin.address());
      }
    }
    // If no custom origin provided, first step is used as origin
    return formatStepLocation(firstStep);
  }

  public String formatStepLocation(ItineraryStepResponse step) {
    if (step.latitude() != null && step.longitude() != null) {
      return formatCoordinates(step.latitude(), step.longitude());
    }
    return encodeAddressFallback(step.address());
  }

  public String formatCoordinates(double lat, double lng) {
    return String.format(Locale.ROOT, "%.6f,%.6f", lat, lng);
  }

  public String encodeAddressFallback(String address) {
    if (address == null || address.isBlank()) {
      return "";
    }
    // Clean text, encode URL, substitute spaces with '+'
    String cleaned = address.trim().replaceAll("\\s+", " ");
    return URLEncoder.encode(cleaned, StandardCharsets.UTF_8).replace("%20", "+");
  }

  private void appendTravelMode(StringBuilder url, TransportMode transportMode) {
    if (transportMode != null) {
      url.append("&travelmode=").append(transportMode.getGoogleMapsMode());
    } else {
      url.append("&travelmode=driving");
    }
  }
}
