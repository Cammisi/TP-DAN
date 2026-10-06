package com.tpnube.match_ingestion_service.domain;

// Tipos con reglas propias. Cualquier otro texto se acepta como evento genérico.
public enum EventType {
    GOAL,
    CARD,
    MATCH_END;

    public static EventType find(String type) {
        for (EventType known : values()) {
            if (known.name().equals(type)) {
                return known;
            }
        }
        return null;
    }
}
