package com.shoploc.store_service.controller;

import com.shoploc.store_service.dto.itinerary.OptimizeItineraryRequest;
import com.shoploc.store_service.dto.itinerary.OptimizeItineraryResponse;
import com.shoploc.store_service.service.ItineraryOptimizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/v1/stores/itinerary", "/api/v1/stores/itinerary"})
@Tag(
    name = "Itinerary",
    description =
        "Endpoints de calcul d'itinéraire optimisé Click & Collect multi-boutiques (SLE-31, SLE-32)")
public class ItineraryController {

  private final ItineraryOptimizationService itineraryOptimizationService;

  @Autowired
  public ItineraryController(ItineraryOptimizationService itineraryOptimizationService) {
    this.itineraryOptimizationService = itineraryOptimizationService;
  }

  @PostMapping("/optimize")
  @Operation(
      summary = "Calculer l'itinéraire optimisé de retrait",
      description =
          "Prend une liste d'identifiants de boutiques partenaires, optimise l'ordre de passage "
              + "selon la distance géographique et les heures limites de fermeture (SLE-32), et génère l'URL Google Maps standardisée.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Itinéraire optimisé calculé avec succès"),
    @ApiResponse(
        responseCode = "400",
        description = "Données d'entrée invalides ou liste de boutiques vide")
  })
  public ResponseEntity<OptimizeItineraryResponse> optimizeItinerary(
      @Valid @RequestBody OptimizeItineraryRequest request) {
    OptimizeItineraryResponse response = itineraryOptimizationService.optimizeItinerary(request);
    return ResponseEntity.ok(response);
  }
}
