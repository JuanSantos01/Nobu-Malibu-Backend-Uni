package com.restaurant.services.io;

import com.restaurant.dto.ModeloMatematicoRequest;
import com.restaurant.dto.ModeloMatematicoResponse;

public interface InvestigacionOperacionesService {
    ModeloMatematicoResponse resolverAsignacionMesas(ModeloMatematicoRequest request);
}