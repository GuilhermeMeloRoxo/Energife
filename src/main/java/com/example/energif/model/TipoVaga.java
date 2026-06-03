package com.example.energif.model;

public enum TipoVaga {
    RESERVADA("Reservada"),
    CADASTRO_RESERVA("Cadastro de Reserva"),
    AMPLA_CONCORRENCIA("Ampla Concorrência"),
    HABILITADO("Habilitado");
    
    private final String descricao;
    
    TipoVaga(String descricao) {
        this.descricao = descricao;
    }
    
    public String getDescricao() {
        return descricao;
    }

    public boolean isHabilitado() {
        return this == HABILITADO;
    }

    public boolean isReservado() {
        return this == RESERVADA || this == CADASTRO_RESERVA;
    }
    
    public static TipoVaga fromString(String text) {
        if (text != null) {
            if (text.equalsIgnoreCase("RESERVADO")) {
                return RESERVADA;
            }
            if (text.equalsIgnoreCase("CLASSIFICADO_MASCULINO")
                    || text.equalsIgnoreCase("CLASSIFICADO_FEMININO")) {
                return AMPLA_CONCORRENCIA;
            }
            if (text.equalsIgnoreCase("HABILITADO_MASCULINO")
                    || text.equalsIgnoreCase("HABILITADO_FEMININO")) {
                return HABILITADO;
            }
            for (TipoVaga tipo : TipoVaga.values()) {
                if (text.equalsIgnoreCase(tipo.name()) || 
                    text.equalsIgnoreCase(tipo.descricao)) {
                    return tipo;
                }
            }
        }
        return RESERVADA;
    }
}