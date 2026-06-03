package com.example.energif.service;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.energif.model.Campus;
import com.example.energif.model.CampusEdital;
import com.example.energif.model.Candidato;
import com.example.energif.model.Edital;
import com.example.energif.model.TipoVaga;
import com.example.energif.model.SituacaoCandidato;
import com.example.energif.model.CampusEditalTurno;
import com.example.energif.repository.CampusEditalRepository;
import com.example.energif.repository.CampusRepository;
import com.example.energif.repository.CandidatoRepository;
import com.example.energif.repository.EditalRepository;
import com.example.energif.repository.CampusEditalTurnoRepository;

/**
 * Service for Candidate management.
 * 
 * NOTE: TipoVaga assignment is now handled by AlocacaoVagaService during allocation phase.
 * This service should no longer attempt to assign TipoVaga values.
 */
@Service
public class CandidatoService {

    private static final Logger logger = LoggerFactory.getLogger(CandidatoService.class);

    private final CandidatoRepository candidatoRepository;
    private final EditalRepository editalRepository;
    private final CampusEditalRepository campusEditalRepository;
    private final CampusRepository campusRepository;
    private final CampusEditalTurnoRepository campusEditalTurnoRepository;

    public CandidatoService(CandidatoRepository candidatoRepository, 
                          EditalRepository editalRepository, 
                          CampusEditalRepository campusEditalRepository,
                          CampusRepository campusRepository,
                          CampusEditalTurnoRepository campusEditalTurnoRepository) {
        this.candidatoRepository = candidatoRepository;
        this.editalRepository = editalRepository;
        this.campusEditalRepository = campusEditalRepository;
        this.campusRepository = campusRepository;
        this.campusEditalTurnoRepository = campusEditalTurnoRepository;
    }

    @Transactional
    public Candidato salvarComAtualizacao(Candidato candidato, Integer numeroVagasReservadas, Integer numeroVagasAmplaConcorrencia, Long editalId) {
        // Process edital if provided
        if (editalId != null) {
            Edital edital = editalRepository.findById(editalId).orElse(null);
            if (edital != null) {
                candidato.setEdital(edital);
            }
        }

        Candidato saved = candidatoRepository.save(candidato);

        // Update CampusEdital and create CampusEditalTurno with correct turno
        if (saved.getEdital() != null && saved.getCampus() != null) {
            CampusEdital ce = campusEditalRepository.findByCampusAndEdital(saved.getCampus(), saved.getEdital());
            if (ce == null) {
                ce = new CampusEdital();
                ce.setCampus(saved.getCampus());
                ce.setEdital(saved.getEdital());
            }
            if (numeroVagasReservadas != null) ce.setNumeroVagasReservadas(numeroVagasReservadas);
            if (numeroVagasAmplaConcorrencia != null) ce.setNumeroVagasAmplaConcorrencia(numeroVagasAmplaConcorrencia);
            campusEditalRepository.save(ce);
            
            // Create CampusEditalTurno with the correct turno of the candidate
            String turnoDoTurno = (saved.getTurno() != null && !saved.getTurno().isBlank()) ? saved.getTurno() : "UNICO";
            
            try {
                CampusEditalTurno existing = campusEditalTurnoRepository.findByCampusEditalAndTurno(ce, turnoDoTurno);
                if (existing == null) {
                    CampusEditalTurno cet = new CampusEditalTurno();
                    cet.setCampusEdital(ce);
                    cet.setTurno(turnoDoTurno);
                    cet.setNumeroVagasReservadas(ce.getNumeroVagasReservadas());
                    cet.setNumeroVagasAmplaConcorrencia(ce.getNumeroVagasAmplaConcorrencia());
                    logger.info("Creating CampusEditalTurno: Campus={}, Edital={}, Turno={}", 
                               saved.getCampus().getNome(), saved.getEdital().getDescricao(), turnoDoTurno);
                    campusEditalTurnoRepository.save(cet);
                } else {
                    logger.debug("CampusEditalTurno already exists: Campus={}, Edital={}, Turno={}", 
                               saved.getCampus().getNome(), saved.getEdital().getDescricao(), turnoDoTurno);
                }
            } catch (Exception ex) {
                logger.warn("Could not create/update CampusEditalTurno for turno '{}': {}", 
                           turnoDoTurno, ex.getMessage());
            }
        }

        // Update edital inscription counter
        if (editalId != null) {
            editalRepository.findById(editalId).ifPresent(edital -> {
                Integer current = edital.getNumeroInscritos();
                edital.setNumeroInscritos((current == null ? 0 : current) + 1);
                editalRepository.save(edital);
            });
        }

        return saved;
    }

    /**
     * Save candidate without extra parameters.
     * Note: TipoVaga will be assigned by AlocacaoVagaService later.
     */
    @Transactional
    public Candidato salvarComAtualizacao(Candidato candidato) {
        return candidatoRepository.save(candidato);
    }

    @Transactional
    public void habilitarCandidato(Long candidatoId, String motivo) {
        logger.info("Starting qualification of candidate ID: {}", candidatoId);
        
        Candidato candidato = candidatoRepository.findById(candidatoId)
            .orElseThrow(() -> new IllegalArgumentException("Candidate not found"));
        
        Campus campus = candidato.getCampus();
        if (campus == null) {
            throw new IllegalStateException("Candidate does not have campus defined");
        }
        
        // CHECK IF ALREADY CLASSIFIED
        if (candidato.getSituacao() == SituacaoCandidato.CLASSIFICADO) {
            logger.info("Candidate {} is already classified", candidatoId);
            return;
        }
        
        // TipoVaga is now assigned by AlocacaoVagaService, not here
        // We just update the situation to CLASSIFICADO
        candidato.setSituacao(SituacaoCandidato.CLASSIFICADO);
        candidato.setMotivoNaoClassificacao(null);
        
        candidatoRepository.save(candidato);
        
        logger.info("Candidate {} qualified successfully.", candidatoId);
    }

    @Transactional
    public Map<String, Object> habilitarCandidatoComFeedback(Long candidatoId, String motivo) {
        Map<String, Object> resultado = new HashMap<>();
        
        try {
            logger.info("=== STARTING QUALIFICATION OF CANDIDATE {} ===", candidatoId);
            
            Candidato candidato = candidatoRepository.findById(candidatoId)
                .orElseThrow(() -> {
                    logger.error("Candidate {} not found", candidatoId);
                    resultado.put("sucesso", false);
                    resultado.put("mensagem", "Candidate not found");
                    return new IllegalArgumentException("Candidate not found");
                });
            
            Campus campus = candidato.getCampus();
            if (campus == null) {
                logger.error("Candidate {} does not have campus defined", candidatoId);
                resultado.put("sucesso", false);
                resultado.put("mensagem", "Candidate does not have campus defined");
                return resultado;
            }
            
            logger.info("Candidate: {}, Campus: {}, Tipo Vaga: {}", 
                       candidato.getNome(), campus.getNome(), candidato.getTipoVaga());
            
            // CHECK IF ALREADY CLASSIFIED
            if (candidato.getSituacao() == SituacaoCandidato.CLASSIFICADO) {
                logger.info("Candidate {} is already classified", candidatoId);
                resultado.put("sucesso", true);
                resultado.put("mensagem", "Candidate was already classified");
                resultado.put("jaClassificado", true);
                return resultado;
            }
            
            // TipoVaga is assigned by AlocacaoVagaService, not here
            candidato.setSituacao(SituacaoCandidato.CLASSIFICADO);
            candidato.setMotivoNaoClassificacao(null);
            
            Candidato candidatoSalvo = candidatoRepository.save(candidato);
            logger.info("Candidate saved - Situation: {}", candidatoSalvo.getSituacao());
            
            resultado.put("sucesso", true);
            resultado.put("mensagem", "Candidate qualified successfully!");
            resultado.put("tipoVaga", candidato.getTipoVaga() != null ? candidato.getTipoVaga().toString() : "NOT ASSIGNED");
            resultado.put("campusNome", campus.getNome());
            
            logger.info("=== QUALIFICATION COMPLETED SUCCESSFULLY ===");
            
        } catch (Exception e) {
            logger.error("Error qualifying candidate {}: {}", candidatoId, e.getMessage(), e);
            resultado.put("sucesso", false);
            resultado.put("mensagem", "Internal error: " + e.getMessage());
        }
        
        return resultado;
    }
    
    @Transactional
    public void desabilitarCandidato(Long candidatoId, String motivo) {
        logger.info("=== STARTING DISQUALIFICATION OF CANDIDATE {} ===", candidatoId);
        
        Candidato candidato = candidatoRepository.findById(candidatoId)
            .orElseThrow(() -> new IllegalArgumentException("Candidate not found"));
        
        // Disqualify the candidate
        candidato.setSituacao(SituacaoCandidato.ELIMINADO);
        candidato.setMotivoNaoClassificacao(motivo);
        
        Candidato candidatoSalvo = candidatoRepository.save(candidato);
        logger.info("Candidate saved - Situation: {}", candidatoSalvo.getSituacao());
        
        logger.info("=== DISQUALIFICATION COMPLETED ===");
    }

    /**
     * Check if candidate can be classified.
     * Note: This is a simplified check; allocation is now handled by AlocacaoVagaService
     */
    public boolean podeClassificarCandidato(Long candidatoId) {
        try {
            Candidato candidato = candidatoRepository.findById(candidatoId)
                .orElseThrow(() -> new IllegalArgumentException("Candidate not found"));
            
            // Basic validation: candidate must have campus and edital
            return candidato.getCampus() != null && candidato.getEdital() != null;
            
        } catch (Exception e) {
            logger.error("Error checking if candidate {} can be classified: {}", candidatoId, e.getMessage());
            return false;
        }
    }

    /**
     * Mark a candidate as HABILITADO.
     * Note: This is for manual marking; AlocacaoVagaService handles automatic allocation.
     */
    @Transactional
    public Map<String, Object> marcarComoHabilitado(Long candidatoId) {
        Map<String, Object> resultado = new HashMap<>();
        
        try {
            Candidato candidato = candidatoRepository.findById(candidatoId)
                    .orElseThrow(() -> new IllegalArgumentException("Candidate not found"));

            Campus campus = candidato.getCampus();
            if (campus == null) {
                resultado.put("sucesso", false);
                resultado.put("mensagem", "Candidate does not have campus defined");
                return resultado;
            }

            // If already HABILITADO, nothing to do
            if (candidato.getSituacao() == SituacaoCandidato.HABILITADO) {
                resultado.put("sucesso", true);
                resultado.put("mensagem", "Candidate was already marked as qualified");
                return resultado;
            }

            // Mark candidate as HABILITADO
            candidato.setSituacao(SituacaoCandidato.HABILITADO);
            candidato.setTipoVaga(TipoVaga.HABILITADO);
            candidato.setMotivoNaoClassificacao(null);
            candidatoRepository.save(candidato);

            resultado.put("sucesso", true);
            resultado.put("mensagem", "Candidate marked as qualified successfully");
            logger.info("Candidate {} marked as HABILITADO successfully", candidatoId);
            return resultado;

        } catch (Exception ex) {
            logger.error("Error marking candidate {} as qualified: {}", candidatoId, ex.getMessage());
            resultado.put("sucesso", false);
            resultado.put("mensagem", "Error processing: " + ex.getMessage());
            return resultado;
        }
    }
}
