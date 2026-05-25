package com.example.energif.model;

public enum TipoVaga {
    CLASSIFICADO_MASCULINO("Classificado - Masculino"),
    CLASSIFICADO_FEMININO("Classificado - Feminino"),
    HABILITADO_MASCULINO("Habilitado - Masculino"),
    HABILITADO_FEMININO("Habilitado - Feminino"),
    RESERVADO("Reservado"),
    // Legacy name: some rows in the database still store RESERVADA
    RESERVADA("Reservada"),
    CADASTRO_RESERVA("Cadastro de Reserva"),
    // Legacy/older DB value for open competition
    AMPLA_CONCORRENCIA("Ampla Concorrência");
    
    private final String descricao;
    
    TipoVaga(String descricao) {
        this.descricao = descricao;
    }
    
    public String getDescricao() {
        return descricao;
    }

    public boolean isClassificado() {
        return this == CLASSIFICADO_MASCULINO || this == CLASSIFICADO_FEMININO || this == AMPLA_CONCORRENCIA;
    }

    public boolean isHabilitado() {
        return this == HABILITADO_MASCULINO || this == HABILITADO_FEMININO;
    }

    public boolean isReservado() {
        return this == RESERVADO || this == RESERVADA || this == CADASTRO_RESERVA;
    }

    public boolean isMasculino() {
        return this == CLASSIFICADO_MASCULINO || this == HABILITADO_MASCULINO;
    }

    public boolean isFeminino() {
        return this == CLASSIFICADO_FEMININO || this == HABILITADO_FEMININO;
    }
    
    public static TipoVaga fromString(String text) {
        if (text != null) {
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