package com.shoploc.store_service.dto;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.springframework.data.domain.Page;

@Schema(description = "Réponse paginée générique pour les listes d'entités")
public record PageResponse<T>(
    @ArraySchema(schema = @Schema(description = "Éléments de la page")) List<T> content,
    @Schema(description = "Index de la page courante (0-based)", example = "0") int page,
    @Schema(description = "Taille demandée de la page", example = "20") int size,
    @Schema(description = "Nombre total d'éléments", example = "42") long totalElements,
    @Schema(description = "Nombre total de pages", example = "3") int totalPages,
    @Schema(description = "Indique si c'est la première page", example = "true") boolean first,
    @Schema(description = "Indique si c'est la dernière page", example = "false") boolean last) {

  public static <T> PageResponse<T> fromPage(Page<T> page) {
    return new PageResponse<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.isFirst(),
        page.isLast());
  }

  public static <T> PageResponse<T> of(
      List<T> content, int page, int size, long totalElements, int totalPages) {
    return new PageResponse<>(
        content,
        page,
        size,
        totalElements,
        totalPages,
        page == 0,
        page >= totalPages - 1 || totalPages == 0);
  }
}
