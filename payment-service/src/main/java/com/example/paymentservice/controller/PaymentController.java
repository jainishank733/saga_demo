package com.example.paymentservice.controller;

import com.example.paymentservice.dto.PaymentRequest;
import com.example.paymentservice.dto.PaymentResponse;
import com.example.paymentservice.entity.Payment;
import com.example.paymentservice.entity.PaymentStatus;
import com.example.paymentservice.exception.PaymentDeclinedException;
import com.example.paymentservice.exception.PaymentNotFoundException;
import com.example.paymentservice.repository.PaymentRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    /**
     * Simulated business rule so this demo can show both saga outcomes without
     * needing a real payment gateway: any charge above this amount is "declined".
     */
    private static final BigDecimal DECLINE_THRESHOLD = BigDecimal.valueOf(1000);

    private final PaymentRepository paymentRepository;

    public PaymentController(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> charge(@RequestBody PaymentRequest request) {
        if (request.getAmount().compareTo(DECLINE_THRESHOLD) > 0) {
            throw new PaymentDeclinedException(
                    "amount " + request.getAmount() + " exceeds simulated account limit of " + DECLINE_THRESHOLD);
        }

        Payment payment = new Payment(request.getOrderId(), request.getAmount(), PaymentStatus.SUCCESS);
        payment = paymentRepository.save(payment);

        return ResponseEntity.ok(toResponse(payment));
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<PaymentResponse> refund(@PathVariable Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new PaymentNotFoundException("no payment found for orderId " + orderId));

        payment.setStatus(PaymentStatus.REFUNDED);
        payment = paymentRepository.save(payment);

        return ResponseEntity.ok(toResponse(payment));
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getOrderId(), payment.getAmount(),
                payment.getStatus().name());
    }
}
