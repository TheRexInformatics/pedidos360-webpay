package com.pedidos360.pedidos360_webpay.controller;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/v1/webpay")
public class WebpayController {

    private static final String WEBPAY_URL = "https://webpay3gint.transbank.cl/rswebpaytransaction/api/webpay/v1.2/transactions";
    private static final String COMMERCE_CODE = "597055555532";
    private static final String API_KEY = "579B532A7440BB0C9079DED94D31EA1615BACEB56610332264630D42D0A36B1C";

    private final RestTemplate restTemplate = new RestTemplate();

    private HttpHeaders getTransbankHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Tbk-Api-Key-Id", COMMERCE_CODE);
        headers.set("Tbk-Api-Key-Secret", API_KEY);
        return headers;
    }

    @PostMapping("/crear")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Object> crearTransaccion(@RequestBody Map<String, Object> payload) {
        try {
            String rawOrder = String.valueOf(payload.getOrDefault("boleta", "ORD-" + System.currentTimeMillis()));
            String buyOrder = rawOrder.length() > 26 ? rawOrder.substring(0, 26) : rawOrder;

            String rawSession = String.valueOf(payload.getOrDefault("sessionId", "SES-" + System.currentTimeMillis()));
            String sessionId = rawSession.length() > 61 ? rawSession.substring(0, 61) : rawSession;

            long amount = Math.round(Double.parseDouble(String.valueOf(payload.get("monto"))));
            String returnUrl = String.valueOf(payload.get("returnUrl"));

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("buy_order", buyOrder);
            requestBody.put("session_id", sessionId);
            requestBody.put("amount", amount);
            requestBody.put("return_url", returnUrl);

            var entity = new HttpEntity<>(requestBody, getTransbankHeaders());
            var response = restTemplate.exchange(WEBPAY_URL, HttpMethod.POST, entity, HashMap.class);

            Map<String, Object> body = response.getBody();
            Map<String, Object> result = new HashMap<>();
            result.put("token", body != null ? body.get("token") : null);
            result.put("url", body != null ? body.get("url") : null);

            return ResponseEntity.status(200).body(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Error al iniciar transacción Webpay");
            error.put("detalle", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/confirmar")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Object> confirmarTransaccion(@RequestBody Map<String, String> payload) {
        try {
            String tokenWs = payload.get("token_ws");
            if (tokenWs == null || tokenWs.isBlank()) {
                Map<String, Object> error = new HashMap<>();
                error.put("error", "El parámetro token_ws es obligatorio");
                return ResponseEntity.badRequest().body(error);
            }

            var entity = new HttpEntity<Void>(getTransbankHeaders());
            var response = restTemplate.exchange(WEBPAY_URL + "/" + tokenWs, HttpMethod.PUT, entity, HashMap.class);

            Map<String, Object> body = response.getBody();
            String status = body != null ? String.valueOf(body.getOrDefault("status", "")) : "";
            Object codeObj = body != null ? body.get("response_code") : null;
            int responseCode = (codeObj instanceof Number) ? ((Number) codeObj).intValue() : -1;
            boolean aprobado = "AUTHORIZED".equals(status) && responseCode == 0;

            Map<String, Object> result = new HashMap<>();
            result.put("aprobado", aprobado);
            result.put("estado", status);
            result.put("ordenCompra", body != null ? body.getOrDefault("buy_order", "") : "");
            result.put("monto", body != null ? body.getOrDefault("amount", 0) : 0);
            result.put("codigoAutorizacion", body != null ? body.getOrDefault("authorization_code", "") : "");
            result.put("fechaTransaccion", body != null ? body.getOrDefault("transaction_date", "") : "");

            return ResponseEntity.status(200).body(result);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Error al confirmar transacción Webpay");
            error.put("detalle", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}