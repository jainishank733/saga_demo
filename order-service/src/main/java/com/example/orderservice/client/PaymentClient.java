package com.example.orderservice.client;

import com.example.orderservice.dto.PaymentRequest;
import com.example.orderservice.dto.PaymentResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

@Component
public class PaymentClient {

    private final RestTemplate restTemplate;
    private final String paymentServiceUrl;

    public PaymentClient(RestTemplate restTemplate,
                          @Value("${services.payment.url}") String paymentServiceUrl) {
        this.restTemplate = restTemplate;
        this.paymentServiceUrl = paymentServiceUrl;
    }

    /**
     * Saga step: charge payment for an order.
     * Throws org.springframework.web.client.RestClientException (e.g. HttpClientErrorException
     * for a 402 Payment Required) if the payment is declined.
     */
    public PaymentResponse charge(Long orderId, BigDecimal amount) {
        PaymentRequest request = new PaymentRequest(orderId, amount);
        return restTemplate.postForObject(
                paymentServiceUrl + "/api/payments",
                request,
                PaymentResponse.class);
    }

    /**
     * Compensating transaction: refund a previously captured payment.
     */
    public void refund(Long orderId) {
        restTemplate.delete(paymentServiceUrl + "/api/payments/" + orderId);
    }
}
