package com.shoploc.store_service.dto;

import jakarta.validation.constraints.Min;

public class StockUpdateRequest {

  /**
   * Delta d'ajustement relatif (ex: -1, -5 pour décrémenter lors d'un achat, +10 pour réapprovisionnement).
   */
  private Integer quantityDelta;

  /**
   * Nouvelle quantité absolue (ex: 50). Doit être positive ou nulle.
   */
  @Min(value = 0, message = "La nouvelle quantité doit être supérieure ou égale à 0")
  private Integer newQuantity;

  public StockUpdateRequest() {}

  public StockUpdateRequest(Integer quantityDelta, Integer newQuantity) {
    this.quantityDelta = quantityDelta;
    this.newQuantity = newQuantity;
  }

  public static StockUpdateRequest ofDelta(int delta) {
    return new StockUpdateRequest(delta, null);
  }

  public static StockUpdateRequest ofAbsolute(int absoluteQuantity) {
    return new StockUpdateRequest(null, absoluteQuantity);
  }

  public Integer getQuantityDelta() {
    return quantityDelta;
  }

  public void setQuantityDelta(Integer quantityDelta) {
    this.quantityDelta = quantityDelta;
  }

  public Integer getNewQuantity() {
    return newQuantity;
  }

  public void setNewQuantity(Integer newQuantity) {
    this.newQuantity = newQuantity;
  }
}
