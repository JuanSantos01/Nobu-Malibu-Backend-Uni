package com.restaurant.dto;



public class ModeloMatematicoRequest {
    private int mesasDisponibles2P;
    private int mesasDisponibles4P;
    private int capacidadTotalPersonas;

    // Getters y Setters
    public int getMesasDisponibles2P() { return mesasDisponibles2P; }
    public void setMesasDisponibles2P(int mesasDisponibles2P) { this.mesasDisponibles2P = mesasDisponibles2P; }

    public int getMesasDisponibles4P() { return mesasDisponibles4P; }
    public void setMesasDisponibles4P(int mesasDisponibles4P) { this.mesasDisponibles4P = mesasDisponibles4P; }

    public int getCapacidadTotalPersonas() { return capacidadTotalPersonas; }
    public void setCapacidadTotalPersonas(int capacidadTotalPersonas) { this.capacidadTotalPersonas = capacidadTotalPersonas; }
}
