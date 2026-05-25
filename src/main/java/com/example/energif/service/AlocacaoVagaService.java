package com.example.energif.service;

import com.example.energif.model.Candidato;
import com.example.energif.model.SituacaoCandidato;
import com.example.energif.model.TipoVaga;
import com.example.energif.model.Vaga;
import com.example.energif.repository.CandidatoRepository;
import com.example.energif.repository.VagaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AlocacaoVagaService {

    @Autowired
    private CandidatoRepository candidatoRepository;

    @Autowired
    private VagaRepository vagaRepository;

    /**
     * Aloca as vagas de TODOS os campus de uma única vez (Botão Global)
     */
    public void alocarTodosCampus() {
        List<Vaga> vagas = vagaRepository.findAll();
        for (Vaga vaga : vagas) {
            processarAlocacaoVagas(vaga);
        }
    }

    /**
     * Processa a alocação separando os candidatos pelos seus respectivos turnos
     */
    public void processarAlocacaoVagas(Vaga vaga) {
        if (vaga == null || vaga.getQuantidade() == null || vaga.getQuantidade() <= 0) {
            return;
        }
        if (vaga.getCampus() == null || vaga.getEdital() == null) {
            return;
        }

        // Busca todos os candidatos do Campus e Edital
        List<Candidato> todosCandidatos = candidatoRepository.findByCampusIdAndEditalIdOrderByDataInscricaoAscHoraInscricaoAsc(
                vaga.getCampus().getId(),
                vaga.getEdital().getId()
        );

        if (todosCandidatos.isEmpty()) {
            return;
        }

        // CORREÇÃO: Agrupa os candidatos pelo turno deles (Ex: "Manhã", "Tarde", "Noite")
        // Certifique-se de que o candidato possui o método getTurno() ou correspondente
        Map<String, List<Candidato>> candidatosPorTurno = todosCandidatos.stream()
                .filter(c -> c.getTurno() != null)
                .collect(Collectors.groupingBy(Candidato::getTurno));

        // Processa as regras de negócio de vagas de forma isolada para cada turno encontrado
        for (Map.Entry<String, List<Candidato>> entry : candidatosPorTurno.entrySet()) {
            List<Candidato> candidatosDoTurno = entry.getValue();

            int quantidade = vaga.getQuantidade(); 
            int vagasReservadas = Math.max(1, (int) Math.ceil(quantidade * 0.2));

            List<Candidato> elegiveis = candidatosDoTurno.stream()
                    .filter(c -> c.getSituacao() != SituacaoCandidato.ELIMINADO)
                    .collect(Collectors.toList());

            // 1. Vagas Reservadas (Mulheres)
            List<Candidato> classificadasReservadas = new ArrayList<>();
            for (Candidato candidato : elegiveis) {
                if (classificadasReservadas.size() >= vagasReservadas) {
                    break;
                }
                if (candidato.getGenero() != null && Character.toUpperCase(candidato.getGenero()) == 'F') {
                    classificadasReservadas.add(candidato);
                }
            }

            // 2. Ampla Concorrência (Homens)
            int vagasAmplaConcorrencia = Math.max(0, quantidade - vagasReservadas);
            List<Candidato> classificadasAmpla = new ArrayList<>();
            for (Candidato candidato : elegiveis) {
                if (classificadasAmpla.size() >= vagasAmplaConcorrencia) {
                    break;
                }
                if (candidato.getGenero() != null && Character.toUpperCase(candidato.getGenero()) == 'M') {
                    classificadasAmpla.add(candidato);
                }
            }

            // 3. Aplica as regras de situação do candidato
            for (Candidato candidato : elegiveis) {
                if (classificadasReservadas.contains(candidato)) {
                    candidato.setSituacao(SituacaoCandidato.CLASSIFICADO);
                    candidato.setTipoVaga(TipoVaga.RESERVADO);
                } else if (classificadasAmpla.contains(candidato)) {
                    candidato.setSituacao(SituacaoCandidato.CLASSIFICADO);
                    candidato.setTipoVaga(TipoVaga.AMPLA_CONCORRENCIA);
                } else {
                    candidato.setSituacao(SituacaoCandidato.HABILITADO);
                    if (candidato.getGenero() != null && Character.toUpperCase(candidato.getGenero()) == 'F') {
                        candidato.setTipoVaga(TipoVaga.HABILITADO_FEMININO);
                    } else {
                        candidato.setTipoVaga(TipoVaga.HABILITADO_MASCULINO);
                    }
                }
            }
        }

        // Salva todos de uma vez
        candidatoRepository.saveAll(todosCandidatos);
    }
}