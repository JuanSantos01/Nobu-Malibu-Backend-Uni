package com.restaurant.configs;

import java.util.Map;

/** Mesas físicas del restaurante (10 en total) y límites de duración de una reserva. */
public final class TableCapacity {

    private TableCapacity() {}

    /** Los nombres deben coincidir con los `tableType` que envía la app. */
    public static final Map<String, Integer> BY_TYPE = Map.of(
            "Standard Table", 3,
            "Premium Table", 1,
            "Outdoor Table", 2,
            "High-top Table", 1,
            "Chef's Table", 1,
            "Family-Style Table", 2
    );

    public static final int DEFAULT_DURATION_MINUTES = 90;
    public static final int MIN_DURATION_MINUTES = 30;
    public static final int MAX_DURATION_MINUTES = 240;
}
