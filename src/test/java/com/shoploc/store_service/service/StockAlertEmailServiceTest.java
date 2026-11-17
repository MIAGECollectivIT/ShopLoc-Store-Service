package com.shoploc.store_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class StockAlertEmailServiceTest {

  @Mock private ObjectProvider<JavaMailSender> mailSenderProvider;
  @Mock private JavaMailSender javaMailSender;

  private StockAlertEmailService stockAlertEmailService;

  @BeforeEach
  void setUp() {
    stockAlertEmailService = new StockAlertEmailService(mailSenderProvider, "contact@shop-loc.fr");
  }

  @Test
  @DisplayName("sendStockAlertAsync should send email for critical stock threshold")
  void shouldSendAlertForCriticalStock() throws Exception {
    when(mailSenderProvider.getIfAvailable()).thenReturn(javaMailSender);
    MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
    when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

    stockAlertEmailService.sendStockAlertAsync(
        "merchant@test.fr", "Boulangerie Paul", "Baguette Tradition", "BAG-001", 3, 5);

    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(javaMailSender).send(captor.capture());

    MimeMessage sentMessage = captor.getValue();
    assertThat(sentMessage.getAllRecipients()).hasSize(1);
    assertThat(sentMessage.getAllRecipients()[0].toString()).isEqualTo("merchant@test.fr");
    assertThat(sentMessage.getSubject()).contains("Seuil critique de stock atteint");
  }

  @Test
  @DisplayName("sendStockAlertAsync should send email for rupture de stock (currentStock = 0)")
  void shouldSendAlertForOutOfStock() throws Exception {
    when(mailSenderProvider.getIfAvailable()).thenReturn(javaMailSender);
    MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
    when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

    stockAlertEmailService.sendStockAlertAsync(
        "merchant@test.fr", "Boulangerie Paul", "Baguette Tradition", "BAG-001", 0, 5);

    ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
    verify(javaMailSender).send(captor.capture());

    MimeMessage sentMessage = captor.getValue();
    assertThat(sentMessage.getSubject()).contains("RUPTURE DE STOCK");
  }

  @Test
  @DisplayName("sendStockAlertAsync should skip dispatch when merchantEmail is blank")
  void shouldSkipWhenMerchantEmailBlank() {
    stockAlertEmailService.sendStockAlertAsync(
        "   ", "Boulangerie Paul", "Baguette", "REF-1", 2, 5);

    verify(javaMailSender, never()).send(any(MimeMessage.class));
  }

  @Test
  @DisplayName("sendStockAlertAsync should skip dispatch when JavaMailSender is unavailable")
  void shouldSkipWhenMailSenderUnavailable() {
    when(mailSenderProvider.getIfAvailable()).thenReturn(null);

    stockAlertEmailService.sendStockAlertAsync(
        "merchant@test.fr", "Boulangerie Paul", "Baguette", "REF-1", 2, 5);

    verify(javaMailSender, never()).send(any(MimeMessage.class));
  }
}
