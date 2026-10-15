package com.assignment.deepfine.controller;

import com.assignment.deepfine.dto.InboundRequest;
import com.assignment.deepfine.dto.OutboundRequest;
import com.assignment.deepfine.dto.ProductStock;
import com.assignment.deepfine.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    // 상품 입고
    @PostMapping("/inbounds")
    public ProductStock inbound(@Valid @RequestBody InboundRequest request) {
        return service.inbound(request.id(), request.name(), request.amount());
    }

    // 상품 출고
    @PostMapping("/outbounds")
    public ProductStock outbound(@Valid @RequestBody OutboundRequest request) {
        return service.outbound(request.id(), request.amount());
    }
}
