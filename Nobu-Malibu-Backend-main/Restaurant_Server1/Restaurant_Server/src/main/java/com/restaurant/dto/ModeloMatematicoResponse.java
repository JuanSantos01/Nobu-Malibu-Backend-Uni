package com.restaurant.dto;

import lombok.Getter;
import lombok.Setter;

public class ModeloMatematicoResponse {
    // Getters y Setters
    @Setter
    @Getter
    private String funcionObjetivoExplicacion;
    @Setter
    @Getter
    private String restriccionesAplicadas;
    @Setter
    @Getter
    private int optimoMesas2P;
    @Setter
    @Getter
    private int optimoMesas4P;
    private double zMaxIngresoEsperado;

    public double getzMaxIngresoEsperado() { return zMaxIngresoEsperado; }
    public void setzMaxIngresoEsperado(double zMaxIngresoEsperado) { this.zMaxIngresoEsperado = zMaxIngresoEsperado; }
}
