package com.restaurant.services.io;

import com.restaurant.dto.ModeloMatematicoRequest;
import com.restaurant.dto.ModeloMatematicoResponse;
import org.springframework.stereotype.Service;

@Service
public class InvestigacionOperacionesServiceImpl implements InvestigacionOperacionesService {

    @Override
    public ModeloMatematicoResponse resolverAsignacionMesas(ModeloMatematicoRequest request) {
        // Coeficientes de la Función Objetivo (Ingreso promedio por tipo de mesa)
        double c1 = 55.0;  // Z1: Beneficio promedio asignado a mesas de 2 comensales
        double c2 = 120.0; // Z2: Beneficio promedio asignado a mesas de 4 comensales

        int x1Optimo = 0;
        int x2Optimo = 0;
        double zMaximo = 0.0;

        // Bucle iterativo sobre el espacio de soluciones acotado por las restricciones de inventario
        for (int x1 = 0; x1 <= request.getMesasDisponibles2P(); x1++) {
            for (int x2 = 0; x2 <= request.getMesasDisponibles4P(); x2++) {

                // Restricción de Capacidad de Comensales Simultáneos (Cocina y Salón)
                int capacidadUtilizada = (x1 * 2) + (x2 * 4);

                if (capacidadUtilizada <= request.getCapacidadTotalPersonas()) {
                    // Evaluar Función Objetivo: Z = c1*x1 + c2*x2
                    double ingresoActual = (x1 * c1) + (x2 * c2);

                    if (ingresoActual > zMaximo) {
                        zMaximo = ingresoActual;
                        x1Optimo = x1;
                        x2Optimo = x2;
                    }
                }
            }
        }

        // Construir la respuesta con la formalización matemática completa para el profesor
        ModeloMatematicoResponse response = new ModeloMatematicoResponse();
        response.setFuncionObjetivoExplicacion("Maximizar Z = 55*x1 + 120*x2");
        response.setRestriccionesAplicadas(
                "Restricciones del modelo: " +
                        "x1 <= " + request.getMesasDisponibles2P() + " [Disponibilidad 2P]; " +
                        "x2 <= " + request.getMesasDisponibles4P() + " [Disponibilidad 4P]; " +
                        "2*x1 + 4*x2 <= " + request.getCapacidadTotalPersonas() + " [Capacidad Máxima de Comensales]"
        );
        response.setOptimoMesas2P(x1Optimo);
        response.setOptimoMesas4P(x2Optimo);
        response.setzMaxIngresoEsperado(zMaximo);

        return response;
    }
}