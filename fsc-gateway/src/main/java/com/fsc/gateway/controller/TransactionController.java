package com.fsc.gateway.controller;

import com.fsc.gateway.service.TransactionRoutingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionRoutingService routingService;

    public TransactionController(TransactionRoutingService routingService) {
        this.routingService = routingService;
    }

    @PostMapping("/realtime")
    public CompletableFuture<ResponseEntity<String>> processRealtime(@RequestBody String payload) {
        // Validação de Rate Limit via Redis virá aqui através de Filter ou AOP.
        return routingService.routeRealtime(payload)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/batch")
    public ResponseEntity<String> processBatch(@RequestBody String payload) {
        routingService.routeBatch(payload);
        return ResponseEntity.accepted().body("Transação recebida para processamento em lote.");
    }
}
