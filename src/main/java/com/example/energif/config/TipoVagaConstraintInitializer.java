package com.example.energif.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TipoVagaConstraintInitializer {

    private final JdbcTemplate jdbcTemplate;

    public TipoVagaConstraintInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void atualizarConstraintTipoVaga() {
        jdbcTemplate.execute("ALTER TABLE candidato DROP CONSTRAINT IF EXISTS candidato_tipo_vaga_check");
        jdbcTemplate.execute(
                "ALTER TABLE candidato ADD CONSTRAINT candidato_tipo_vaga_check CHECK (tipo_vaga IS NULL OR tipo_vaga IN ('RESERVADA', 'AMPLA_CONCORRENCIA', 'CADASTRO_RESERVA', 'HABILITADO'))");
    }
}