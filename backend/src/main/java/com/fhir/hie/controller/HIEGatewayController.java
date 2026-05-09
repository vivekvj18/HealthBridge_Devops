package com.fhir.hie.controller;

import com.fhir.hie.dto.ExchangeRequestDTO;
import com.fhir.hie.dto.ExchangeResponseDTO;
import com.fhir.hie.service.HIEGatewayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/hie")
public class HIEGatewayController {

    @Autowired
    private HIEGatewayService hieGatewayService;

    @PostMapping("/exchange")
    public ExchangeResponseDTO requestExchange(@RequestBody ExchangeRequestDTO request) {
        return hieGatewayService.orchestrateExchange(request);
    }

    @PostMapping("/consent-only")
    public ExchangeResponseDTO initiateConsentOnly(@RequestBody ExchangeRequestDTO request) {
        return hieGatewayService.initiateConsentOnly(request);
    }

    @PostMapping("/pull-only")
    public ExchangeResponseDTO pullClinicalData(@RequestBody ExchangeRequestDTO request) {
        return hieGatewayService.pullClinicalData(request);
    }

    @GetMapping("/exchange/status/{consentId}")
    public ExchangeResponseDTO pollStatus(@PathVariable Long consentId) {
        return hieGatewayService.checkExchangeStatus(consentId);
    }
}
