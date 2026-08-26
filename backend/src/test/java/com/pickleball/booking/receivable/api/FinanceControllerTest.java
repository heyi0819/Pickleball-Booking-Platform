package com.pickleball.booking.receivable.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pickleball.booking.receivable.application.ReceivableApplicationService;
import com.pickleball.booking.receivable.application.RefundApplicationService;
import com.pickleball.booking.receivable.domain.PaymentMethod;
import com.pickleball.booking.shared.api.ApiExceptionHandler;
import com.pickleball.booking.shared.api.RequestIdFilter;
import com.pickleball.booking.shared.application.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FinanceControllerTest {
    private ReceivableApplicationService receivables;
    private RefundApplicationService refunds;
    private MockMvc mvc;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        receivables = mock(ReceivableApplicationService.class);
        refunds = mock(RefundApplicationService.class);
        mvc = MockMvcBuilders.standaloneSetup(new FinanceController(receivables, refunds))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
        actorId = UUID.randomUUID();
    }

    @Test
    void recordsPaymentUsingStringMoneyContract() throws Exception {
        UUID receivableId = UUID.randomUUID();
        UUID payerId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Instant paidAt = Instant.parse("2026-08-20T04:00:00Z");
        when(receivables.recordPayment(any(), eq(receivableId), any(), eq("payment-key"), eq("req-pay")))
                .thenReturn(new ReceivableApplicationService.PaymentResult(
                        paymentId, receivableId, new BigDecimal("1000.00"), PaymentMethod.CASH,
                        "PARTIALLY_PAID", new BigDecimal("1000.00"), new BigDecimal("800.00")));

        mvc.perform(post("/api/v1/receivables/{id}/payments", receivableId)
                        .principal(authentication())
                        .requestAttr(RequestIdFilter.ATTRIBUTE, "req-pay")
                        .header("Idempotency-Key", "payment-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount":"1000.00",
                                  "method":"CASH",
                                  "paidAt":"2026-08-20T04:00:00Z",
                                  "payerUserId":"%s",
                                  "note":"現場收款"
                                }
                                """.formatted(payerId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.data.amount").value("1000.00"))
                .andExpect(jsonPath("$.data.paymentStatus").value("PARTIALLY_PAID"))
                .andExpect(jsonPath("$.data.paidTotal").value("1000.00"))
                .andExpect(jsonPath("$.data.outstandingAmount").value("800.00"))
                .andExpect(jsonPath("$.meta.requestId").value("req-pay"));

        ArgumentCaptor<ReceivableApplicationService.RecordPaymentCommand> command =
                ArgumentCaptor.forClass(ReceivableApplicationService.RecordPaymentCommand.class);
        verify(receivables).recordPayment(any(), eq(receivableId), command.capture(), eq("payment-key"), eq("req-pay"));
        assertThat(command.getValue().amount()).isEqualByComparingTo("1000.00");
        assertThat(command.getValue().paidAt()).isEqualTo(paidAt);
        assertThat(command.getValue().payerUserId()).isEqualTo(payerId);
    }

    @Test
    void requestsRefundUsingCanonicalSinglePaymentContract() throws Exception {
        UUID receivableId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        when(refunds.requestRefund(any(), eq(receivableId), any(), eq("refund-key"), eq("req-refund")))
                .thenReturn(new RefundApplicationService.RefundResult(
                        refundId, paymentId, new BigDecimal("600.00"), "PENDING_APPROVAL",
                        null, null, null, null, null));

        mvc.perform(post("/api/v1/receivables/{id}/refunds", receivableId)
                        .principal(authentication())
                        .requestAttr(RequestIdFilter.ATTRIBUTE, "req-refund")
                        .header("Idempotency-Key", "refund-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentId":"%s",
                                  "amount":"600.00",
                                  "reason":"學員退班"
                                }
                                """.formatted(paymentId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.refundId").value(refundId.toString()))
                .andExpect(jsonPath("$.data.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.data.amount").value("600.00"))
                .andExpect(jsonPath("$.data.currency").value("TWD"));

        ArgumentCaptor<RefundApplicationService.RequestRefundCommand> command =
                ArgumentCaptor.forClass(RefundApplicationService.RequestRefundCommand.class);
        verify(refunds).requestRefund(any(), eq(receivableId), command.capture(), eq("refund-key"), eq("req-refund"));
        assertThat(command.getValue().paymentId()).isEqualTo(paymentId);
        assertThat(command.getValue().amount()).isEqualByComparingTo("600.00");
        assertThat(command.getValue().reason()).isEqualTo("學員退班");
    }

    @Test
    void reviewsAndExecutesRefundAsSeparateCommands() throws Exception {
        UUID refundId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Instant approvedAt = Instant.parse("2026-08-20T05:00:00Z");
        Instant refundedAt = Instant.parse("2026-08-20T05:30:00Z");
        when(refunds.reviewRefund(any(), eq(refundId), any(), eq("review-key"), eq("req-review")))
                .thenReturn(new RefundApplicationService.RefundResult(
                        refundId, paymentId, new BigDecimal("600.00"), "APPROVED",
                        actorId, approvedAt, null, null, null));
        when(refunds.executeRefund(any(), eq(refundId), any(), eq("execute-key"), eq("req-execute")))
                .thenReturn(new RefundApplicationService.RefundResult(
                        refundId, paymentId, new BigDecimal("600.00"), "COMPLETED",
                        actorId, approvedAt, actorId, refundedAt, PaymentMethod.BANK_TRANSFER));

        mvc.perform(post("/api/v1/refunds/{id}/review", refundId)
                        .principal(authentication())
                        .requestAttr(RequestIdFilter.ATTRIBUTE, "req-review")
                        .header("Idempotency-Key", "review-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVE","reason":"確認可退"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.approvedBy").value(actorId.toString()))
                .andExpect(jsonPath("$.data.approvedAt").value("2026-08-20T05:00:00Z"));

        mvc.perform(post("/api/v1/refunds/{id}/execution", refundId)
                        .principal(authentication())
                        .requestAttr(RequestIdFilter.ATTRIBUTE, "req-execute")
                        .header("Idempotency-Key", "execute-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "method":"BANK_TRANSFER",
                                  "refundedAt":"2026-08-20T05:30:00Z",
                                  "reference":"人工匯款末五碼 12345"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.processedBy").value(actorId.toString()))
                .andExpect(jsonPath("$.data.refundedAt").value("2026-08-20T05:30:00Z"));
    }

    @Test
    void financeBusinessErrorsUseCanonicalHttpStatuses() throws Exception {
        UUID receivableId = UUID.randomUUID();
        when(receivables.recordPayment(any(), eq(receivableId), any(), eq("missing-key"), eq("req-404")))
                .thenThrow(new BusinessException("RECEIVABLE_NOT_FOUND", "Receivable was not found"));

        mvc.perform(post("/api/v1/receivables/{id}/payments", receivableId)
                        .principal(authentication())
                        .requestAttr(RequestIdFilter.ATTRIBUTE, "req-404")
                        .header("Idempotency-Key", "missing-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPaymentBody(UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RECEIVABLE_NOT_FOUND"))
                .andExpect(jsonPath("$.error.traceId").value("req-404"));

        UUID refundReceivableId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        when(refunds.requestRefund(any(), eq(refundReceivableId), any(), eq("exceeds-key"), eq("req-422")))
                .thenThrow(new BusinessException("REFUND_EXCEEDS_REFUNDABLE", "Refund exceeds refundable amount"));

        mvc.perform(post("/api/v1/receivables/{id}/refunds", refundReceivableId)
                        .principal(authentication())
                        .requestAttr(RequestIdFilter.ATTRIBUTE, "req-422")
                        .header("Idempotency-Key", "exceeds-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"paymentId":"%s","amount":"600.00","reason":"refund"}
                                """.formatted(paymentId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("REFUND_EXCEEDS_REFUNDABLE"));
    }

    @Test
    void validationAndLegacyRefundShapeReturnBadRequest() throws Exception {
        UUID receivableId = UUID.randomUUID();
        mvc.perform(post("/api/v1/receivables/{id}/payments", receivableId)
                        .principal(authentication())
                        .requestAttr(RequestIdFilter.ATTRIBUTE, "req-invalid")
                        .header("Idempotency-Key", "invalid-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount":"not-money",
                                  "method":"CASH",
                                  "paidAt":"2026-08-20T04:00:00Z",
                                  "payerUserId":"%s"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));

        mvc.perform(post("/api/v1/receivables/{id}/refunds", receivableId)
                        .principal(authentication())
                        .requestAttr(RequestIdFilter.ATTRIBUTE, "req-legacy")
                        .header("Idempotency-Key", "legacy-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "amount":"600.00",
                                  "reason":"legacy",
                                  "paymentAllocations":[{"paymentId":"%s","amount":"600.00"}]
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void missingIdempotencyKeyReturnsBadRequest() throws Exception {
        mvc.perform(post("/api/v1/receivables/{id}/payments", UUID.randomUUID())
                        .principal(authentication())
                        .requestAttr(RequestIdFilter.ATTRIBUTE, "req-no-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPaymentBody(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    private UsernamePasswordAuthenticationToken authentication() {
        return new UsernamePasswordAuthenticationToken(actorId.toString(), "n/a");
    }

    private static String validPaymentBody(UUID payerId) {
        return """
                {
                  "amount":"100.00",
                  "method":"CASH",
                  "paidAt":"2026-08-20T04:00:00Z",
                  "payerUserId":"%s"
                }
                """.formatted(payerId);
    }
}
