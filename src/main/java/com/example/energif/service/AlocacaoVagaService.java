package com.example.energif.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.energif.model.CampusEditalTurno;
import com.example.energif.model.Candidato;
import com.example.energif.model.SituacaoCandidato;
import com.example.energif.model.TipoVaga;
import com.example.energif.repository.CampusEditalTurnoRepository;
import com.example.energif.repository.CandidatoRepository;

@Service
public class AlocacaoVagaService {

    @Autowired
    private CandidatoRepository candidatoRepository;

    @Autowired
    private CampusEditalTurnoRepository campusEditalTurnoRepository;

    /**
     * Aloca as vagas de TODOS os campus de uma única vez (Botão Global)
     * Agora trabalha com CampusEditalTurno para respeitar as vagas específicas de cada turno
     */
    public void alocarTodosCampus() {
        // Processar todos os turnos de todos os campus-edital
        List<CampusEditalTurno> todosTurnos = campusEditalTurnoRepository.findAll();
        for (CampusEditalTurno turno : todosTurnos) {
            processarAlocacaoVagasPorTurno(turno);
        }
    }

    /**
     * Processa a alocação para um turno específico
     * Considera as vagas definidas no CampusEditalTurno (que podem ser diferentes por turno)
     */
    public void processarAlocacaoVagasPorTurno(CampusEditalTurno turno) {
        if (turno == null || turno.getCampusEdital() == null) {
            return;
        }
        
        var campusEdital = turno.getCampusEdital();
        if (campusEdital.getCampus() == null || campusEdital.getEdital() == null) {
            return;
        }

        // Verificar se há vagas definidas para este turno
        Integer resv = turno.getNumeroVagasReservadas();
        Integer ampl = turno.getNumeroVagasAmplaConcorrencia();
        Integer cada = turno.getNumeroVagasCadastroReserva();
        
        int vagasReservadas = resv != null ? resv : 0;
        int vagasAmpla = ampl != null ? ampl : 0;
        int vagasCadastroReserva = cada != null ? cada : 0;

        int totalVagas = vagasReservadas + vagasAmpla + vagasCadastroReserva;

        if (totalVagas <= 0) {
            return;
        }

        // Busca todos os candidatos do Campus, Edital e Turno específico
        List<Candidato> candidatosDoTurno = candidatoRepository
                .findByCampusIdAndEditalIdOrderByDataInscricaoAscHoraInscricaoAsc(
                        campusEdital.getCampus().getId(),
                        campusEdital.getEdital().getId())
                .stream()
                .filter(c -> turno.getTurno().equals(c.getTurno()))
                .collect(Collectors.toList());

        if (candidatosDoTurno.isEmpty()) {
            return;
        }

        // Reprocessa apenas candidatos classificados/habilitados.
        // Candidatos pendentes e eliminados não participam desta etapa.
        List<Candidato> elegiveis = candidatosDoTurno.stream()
            .filter(c -> c.getSituacao() == SituacaoCandidato.CLASSIFICADO
                || c.getSituacao() == SituacaoCandidato.HABILITADO)
            .collect(Collectors.toList());

        if (elegiveis.isEmpty()) {
            return;
        }

        for (Candidato candidato : elegiveis) {
            candidato.setSituacao(SituacaoCandidato.CLASSIFICADO);
            candidato.setTipoVaga(null);
        }

        candidatoRepository.saveAll(candidatosDoTurno);

        // Usa as vagas definidas no CampusEditalTurno
        Integer resv2 = turno.getNumeroVagasReservadas();
        Integer ampl2 = turno.getNumeroVagasAmplaConcorrencia();
        
        int vagasReservadas2 = resv2 != null ? resv2 : 0;
        int vagasAmpla2 = ampl2 != null ? ampl2 : 0;

        // 1. Vagas Reservadas (Mulheres)
        List<Candidato> classificadasReservadas = new ArrayList<>();
        for (Candidato candidato : elegiveis) {
            if (classificadasReservadas.size() >= vagasReservadas2) {
                break;
            }
            if (candidato.getGenero() != null && Character.toUpperCase(candidato.getGenero()) == 'F') {
                classificadasReservadas.add(candidato);
            }
        }

        // 2. Ampla Concorrência (Homens e Mulheres não classificadas em vagas reservadas)
        List<Candidato> classificadasAmpla = new ArrayList<>();
        for (Candidato candidato : elegiveis) {
            if (classificadasAmpla.size() >= vagasAmpla2) {
                break;
            }
            if (!classificadasReservadas.contains(candidato)) {
                classificadasAmpla.add(candidato);
            }
        }

        // 3. Habilitados (todo o restante após reservadas e ampla)
        List<Candidato> restanteHabilitado = new ArrayList<>();
        for (Candidato candidato : elegiveis) {
            if (!classificadasReservadas.contains(candidato) && 
                !classificadasAmpla.contains(candidato)) {
                restanteHabilitado.add(candidato);
            }
        }

        // 4. Aplica as regras de situação do candidato
        for (Candidato candidato : elegiveis) {
            if (classificadasReservadas.contains(candidato)) {
                candidato.setSituacao(SituacaoCandidato.CLASSIFICADO);
                candidato.setTipoVaga(TipoVaga.RESERVADA);
            } else if (classificadasAmpla.contains(candidato)) {
                candidato.setSituacao(SituacaoCandidato.CLASSIFICADO);
                candidato.setTipoVaga(TipoVaga.AMPLA_CONCORRENCIA);
            } else if (restanteHabilitado.contains(candidato)) {
                candidato.setSituacao(SituacaoCandidato.HABILITADO);
                candidato.setTipoVaga(TipoVaga.HABILITADO);
            } else {
                candidato.setSituacao(SituacaoCandidato.CLASSIFICADO);
                candidato.setTipoVaga(null);
            }
        }

        // Salva todos de uma vez
        candidatoRepository.saveAll(candidatosDoTurno);

        // Atualiza os contadores de vagas ocupadas baseado nos candidatos alocados
        long vagasReservadasOcupadas = candidatosDoTurno.stream()
                .filter(c -> c.getSituacao() == SituacaoCandidato.CLASSIFICADO && c.getTipoVaga() == TipoVaga.RESERVADA)
                .count();
        
        long vagasAmplaOcupadas = candidatosDoTurno.stream()
                .filter(c -> c.getSituacao() == SituacaoCandidato.CLASSIFICADO && c.getTipoVaga() == TipoVaga.AMPLA_CONCORRENCIA)
                .count();
        long vagasHabilitadoMasculinoOcupadas = candidatosDoTurno.stream()
            .filter(c -> c.getSituacao() == SituacaoCandidato.HABILITADO
                && c.getTipoVaga() == TipoVaga.HABILITADO
                && c.getGenero() != null
                && Character.toUpperCase(c.getGenero()) != 'F')
            .count();
        long vagasHabilitadoFemininoOcupadas = candidatosDoTurno.stream()
            .filter(c -> c.getSituacao() == SituacaoCandidato.HABILITADO
                && c.getTipoVaga() == TipoVaga.HABILITADO
                && c.getGenero() != null
                && Character.toUpperCase(c.getGenero()) == 'F')
            .count();

        turno.setVagasReservadasOcupadas((int) vagasReservadasOcupadas);
        turno.setVagasAmplaOcupadas((int) vagasAmplaOcupadas);
        turno.setVagasHabilitadoMasculinoOcupadas((int) vagasHabilitadoMasculinoOcupadas);
        turno.setVagasHabilitadoFemininoOcupadas((int) vagasHabilitadoFemininoOcupadas);

        // Salva o turno com os contadores atualizados
        campusEditalTurnoRepository.save(turno);
    }
}