package com.restaurant.controller;

import com.restaurant.dto.ModeloMatematicoRequest;
import com.restaurant.dto.ModeloMatematicoResponse;
import com.restaurant.services.io;
import com.restaurant.services.io.InvestigacionOperacionesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/investigacion-operaciones")
@CrossOrigin(origins = "*") 
public class InvestigacionOperacionesController {

    @Autowired
    private InvestigacionOperacionesService ioService;

    @PostMapping("/optimizar-mesas")
    public ResponseEntity<ModeloMatematicoResponse> optimizarAsignacion(@RequestBody ModeloMatematicoRequest request) {
        ModeloMatematicoResponse resultado = ioService.resolverAsignacionMesas(request);
        return ResponseEntity.ok(resultado);
    }
}
