package com.shoploc.store_service.service;

import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class StockAlertEmailService {

  private static final Logger log = LoggerFactory.getLogger(StockAlertEmailService.class);

  private final ObjectProvider<JavaMailSender> mailSenderProvider;
  private final String fromEmail;

  public StockAlertEmailService(
      ObjectProvider<JavaMailSender> mailSenderProvider,
      @Value("${spring.mail.from:contact@shop-loc.fr}") String fromEmail) {
    this.mailSenderProvider = mailSenderProvider;
    this.fromEmail = fromEmail;
  }

  @Async
  public void sendStockAlertAsync(
      String merchantEmail,
      String storeName,
      String productName,
      String productReference,
      int currentStock,
      int alertThreshold) {
    if (merchantEmail == null || merchantEmail.isBlank()) {
      log.warn(
          "Cannot dispatch stock alert: merchant email is missing for store '{}', product '{}'",
          storeName,
          productName);
      return;
    }

    JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
    if (mailSender == null) {
      log.warn("JavaMailSender is not available. Skipping stock alert email dispatch.");
      return;
    }

    boolean isOutOfStock = currentStock == 0;
    String subject =
        isOutOfStock
            ? String.format("[ShopLoc Alerte] RUPTURE DE STOCK - %s (%s)", productName, storeName)
            : String.format(
                "[ShopLoc Alerte] Seuil critique de stock atteint - %s (%s)",
                productName, storeName);

    try {
      MimeMessage message = mailSender.createMimeMessage();
      MimeMessageHelper helper =
          new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

      helper.setFrom(fromEmail);
      helper.setTo(merchantEmail.trim());
      helper.setSubject(subject);

      String html =
          buildHtmlTemplate(
              storeName, productName, productReference, currentStock, alertThreshold, isOutOfStock);
      String text =
          buildTextContent(
              storeName, productName, productReference, currentStock, alertThreshold, isOutOfStock);

      helper.setText(text, html);

      mailSender.send(message);
      log.info(
          "Stock alert email successfully dispatched to {} for product '{}' (stock={}) in store '{}'",
          merchantEmail,
          productName,
          currentStock,
          storeName);
    } catch (Exception e) {
      log.error(
          "Failed to send stock alert email to {} for product '{}': {}",
          merchantEmail,
          productName,
          e.getMessage());
    }
  }

  private String buildHtmlTemplate(
      String storeName,
      String productName,
      String productReference,
      int currentStock,
      int alertThreshold,
      boolean isOutOfStock) {
    String alertHeaderColor = isOutOfStock ? "#dc2626" : "#f59e0b";
    String statusTitle = isOutOfStock ? "Rupture de Stock" : "Seuil Critique Atteint";
    String statusDescription =
        isOutOfStock
            ? "Ce produit n'a plus aucune unité en stock et a été basculé en statut indisponible sur votre vitrine Click & Collect."
            : "Le stock de ce produit a franchi le seuil d'alerte configuré. Pensez à réapprovisionner.";

    return """
        <!DOCTYPE html>
        <html lang="fr">
        <head>
          <meta charset="UTF-8">
          <style>
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background-color: #f8fafc; color: #1e293b; margin: 0; padding: 24px; }
            .card { max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 12px; border: 1px solid #e2e8f0; overflow: hidden; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05); }
            .header { background: %s; padding: 24px; color: #ffffff; text-align: center; }
            .header h1 { margin: 0; font-size: 20px; font-weight: 700; }
            .header p { margin: 6px 0 0; opacity: 0.95; font-size: 13px; }
            .content { padding: 24px; }
            .alert-box { background: %s; border-radius: 8px; padding: 14px; margin: 16px 0; border: 1px solid %s; }
            .details-box { background: #f8fafc; border-radius: 8px; border: 1px solid #e2e8f0; padding: 16px; margin: 20px 0; }
            .detail-row { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px dashed #cbd5e1; font-size: 13px; }
            .detail-row:last-child { border-bottom: none; }
            .detail-label { color: #64748b; font-weight: 500; }
            .detail-value { font-weight: 600; color: #0f172a; text-align: right; }
            .badge { display: inline-block; padding: 4px 10px; border-radius: 9999px; font-size: 12px; font-weight: 700; background: %s; color: #ffffff; }
            .footer { padding: 16px 24px; background: #f8fafc; border-top: 1px solid #e2e8f0; text-align: center; font-size: 11px; color: #94a3b8; }
          </style>
        </head>
        <body>
          <div class="card">
            <div class="header">
              <h1>⚠️ Alerte Stock Commerçant — ShopLoc</h1>
              <p>%s</p>
            </div>
            <div class="content">
              <p>Bonjour,</p>
              <div class="alert-box">
                <span class="badge">%s</span>
                <p style="margin: 8px 0 0; font-size: 13px; line-height: 1.5;">%s</p>
              </div>

              <div class="details-box">
                <div class="detail-row">
                  <span class="detail-label">Établissement</span>
                  <span class="detail-value">%s</span>
                </div>
                <div class="detail-row">
                  <span class="detail-label">Produit</span>
                  <span class="detail-value">%s</span>
                </div>
                <div class="detail-row">
                  <span class="detail-label">Référence article</span>
                  <span class="detail-value" style="font-family: monospace;">%s</span>
                </div>
                <div class="detail-row">
                  <span class="detail-label">Stock restant</span>
                  <span class="detail-value" style="color: %s; font-size: 15px;"><strong>%d unité(s)</strong></span>
                </div>
                <div class="detail-row">
                  <span class="detail-label">Seuil d'alerte paramétré</span>
                  <span class="detail-value">%d unité(s)</span>
                </div>
              </div>

              <p style="font-size: 13px; color: #475569; line-height: 1.5;">
                Pour réapprovisionner ce produit, connectez-vous à votre espace commerçant ShopLoc et mettez à jour votre stock.
              </p>
            </div>
            <div class="footer">
              Cet email automatique est envoyé par la plateforme ShopLoc aux commerçants partenaires.
            </div>
          </div>
        </body>
        </html>
        """
        .formatted(
            alertHeaderColor,
            isOutOfStock ? "#fef2f2" : "#fffbeb",
            isOutOfStock ? "#fecaca" : "#fde68a",
            alertHeaderColor,
            storeName,
            statusTitle,
            statusDescription,
            storeName,
            productName,
            productReference,
            alertHeaderColor,
            currentStock,
            alertThreshold);
  }

  private String buildTextContent(
      String storeName,
      String productName,
      String productReference,
      int currentStock,
      int alertThreshold,
      boolean isOutOfStock) {
    return """
        [Alerte Stock ShopLoc]
        Établissement : %s
        Statut : %s

        Produit : %s (Réf : %s)
        Stock restant : %d unité(s)
        Seuil d'alerte : %d unité(s)

        Merci de vous connecter à votre espace commerçant pour ajuster votre stock.
        """
        .formatted(
            storeName,
            isOutOfStock ? "RUPTURE DE STOCK" : "SEUIL CRITIQUE ATTEINT",
            productName,
            productReference,
            currentStock,
            alertThreshold);
  }
}
