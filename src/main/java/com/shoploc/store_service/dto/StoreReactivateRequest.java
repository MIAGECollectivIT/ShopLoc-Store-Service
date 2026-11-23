package com.shoploc.store_service.dto;

import com.shoploc.store_service.validation.ValidRibOrIban;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

@Schema(description = "Requête de réactivation d'un commerce partenaire révoqué (SLE-263)")
public record StoreReactivateRequest(
    @Schema(
            description = "Nouveau RIB ou IBAN conventionnel valide du commerce partenaire",
            example = "FR7630006000011234567890189")
        @NotBlank(message = "Le nouveau RIB est obligatoire pour réactiver le commerce")
        @ValidRibOrIban
        String rib,
    @Schema(
            description =
                "Identifiant UUID optionnel du commerçant réaffecté lors de la réactivation",
            example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
        UUID ownerUid) {

  public StoreReactivateRequest(String rib) {
    this(rib, null);
  }
}
