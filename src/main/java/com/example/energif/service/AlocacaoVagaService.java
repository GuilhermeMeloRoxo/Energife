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
        Integer clas = turno.getNumeroVagasClassificado();
        Integer habi = turno.getNumeroVagasHabilitado();
        Integer cada = turno.getNumeroVagasCadastroReserva();
        
        int vagasReservadas = resv != null ? resv : 0;
        int vagasAmpla = ampl != null ? ampl : 0;
        int vagasClassificado = clas != null ? clas : 0;
        int vagasHabilitado = habi != null ? habi : 0;
        int vagasCadastroReserva = cada != null ? cada : 0;
        
        int totalVagas = vagasReservadas + vagasAmpla + vagasClassificado + vagasHabilitado + vagasCadastroReserva;

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

        // Reprocessa apenas os candidatos que não são pendentes nem eliminados.
        // Classificados/habilitados retornam ao estado base para nova alocação.
        List<Candidato> elegiveis = candidatosDoTurno.stream()
            .filter(c -> c.getSituacao() != SituacaoCandidato.PENDENTE
                && c.getSituacao() != SituacaoCandidato.ELIMINADO)
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
        Integer clas2 = turno.getNumeroVagasClassificado();
        Integer habi2 = turno.getNumeroVagasHabilitado();
        
        int vagasReservadas2 = resv2 != null ? resv2 : 0;
        int vagasAmpla2 = ampl2 != null ? ampl2 : 0;
        int vagasClassificado2 = clas2 != null ? clas2 : 0;
        int vagasHabilitado2 = habi2 != null ? habi2 : 0;

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

        // 3. Classificados (Resto das vagas classificadas)
        List<Candidato> restanteClassificado = new ArrayList<>();
        for (Candidato candidato : elegiveis) {
            if (restanteClassificado.size() >= vagasClassificado2) {
                break;
            }
            if (!classificadasReservadas.contains(candidato) && !classificadasAmpla.contains(candidato)) {
                restanteClassificado.add(candidato);
            }
        }

        // 4. Habilitados (Resto das vagas habilitadas)
        List<Candidato> restanteHabilitado = new ArrayList<>();
        for (Candidato candidato : elegiveis) {
            if (restanteHabilitado.size() >= vagasHabilitado2) {
                break;
            }
            if (!classificadasReservadas.contains(candidato) && 
                !classificadasAmpla.contains(candidato) &&
                !restanteClassificado.contains(candidato)) {
                restanteHabilitado.add(candidato);
            }
        }

        // 5. Aplica as regras de situação do candidato
        for (Candidato candidato : elegiveis) {
            if (classificadasReservadas.contains(candidato)) {
                candidato.setSituacao(SituacaoCandidato.CLASSIFICADO);
                candidato.setTipoVaga(TipoVaga.RESERVADA);
            } else if (classificadasAmpla.contains(candidato)) {
                candidato.setSituacao(SituacaoCandidato.CLASSIFICADO);
                candidato.setTipoVaga(TipoVaga.AMPLA_CONCORRENCIA);
            } else if (restanteClassificado.contains(candidato)) {
                candidato.setSituacao(SituacaoCandidato.CLASSIFICADO);
                candidato.setTipoVaga(TipoVaga.AMPLA_CONCORRENCIA);
            } else if (restanteHabilitado.contains(candidato)) {
                candidato.setSituacao(SituacaoCandidato.HABILITADO);
                if (candidato.getGenero() != null && Character.toUpperCase(candidato.getGenero()) == 'F') {
                    candidato.setTipoVaga(TipoVaga.HABILITADO_FEMININO);
                } else {
                    candidato.setTipoVaga(TipoVaga.HABILITADO_MASCULINO);
                }
            } else {
                // Candidatos não classificados permanecem PENDENTES (não devem ser marcados como habilitado)
                if (candidato.getSituacao() != SituacaoCandidato.PENDENTE) {
                    candidato.setSituacao(SituacaoCandidato.PENDENTE);
                }
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

        turno.setVagasReservadasOcupadas((int) vagasReservadasOcupadas);
        turno.setVagasAmplaOcupadas((int) vagasAmplaOcupadas);

        // Salva o turno com os contadores atualizados
        campusEditalTurnoRepository.save(turno);
    }
}