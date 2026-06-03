package com.example.energif.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.example.energif.model.Campus;
import com.example.energif.model.Candidato;
import com.example.energif.model.Edital;
import com.example.energif.model.SituacaoCandidato;
import com.example.energif.model.TipoVaga;
import com.example.energif.repository.CampusRepository;
import com.example.energif.repository.CandidatoRepository;
import com.example.energif.util.Filtro;

import jakarta.servlet.http.HttpServletRequest;

@Controller
@RequestMapping("/candidatos")
public class CandidatoController {

    private static final Logger logger = LoggerFactory.getLogger(CandidatoController.class);

    private final CandidatoRepository candidatoRepository;
    private final CampusRepository campusRepository;
    private final com.example.energif.repository.CampusEditalRepository campusEditalRepository;
    private final com.example.energif.repository.CampusEditalTurnoRepository campusEditalTurnoRepository;
    private final Filtro filtro;
    private final com.example.energif.service.CandidatoService candidatoService;
    private final com.example.energif.repository.MotivoRepository motivoRepository;
    private final com.example.energif.service.RelatorioService relatorioService;

    public CandidatoController(CandidatoRepository candidatoRepository, CampusRepository campusRepository,
            com.example.energif.repository.CampusEditalRepository campusEditalRepository,
            com.example.energif.repository.CampusEditalTurnoRepository campusEditalTurnoRepository,
            Filtro filtro,
            com.example.energif.service.CandidatoService candidatoService,
            com.example.energif.repository.MotivoRepository motivoRepository,
            com.example.energif.service.RelatorioService relatorioService) {
        this.candidatoRepository = candidatoRepository;
        this.campusRepository = campusRepository;
        this.campusEditalRepository = campusEditalRepository;
        this.campusEditalTurnoRepository = campusEditalTurnoRepository;
        this.filtro = filtro;
        this.candidatoService = candidatoService;
        this.motivoRepository = motivoRepository;
        this.relatorioService = relatorioService;
    }

    // Return list of duplicate CPFs and their counts as JSON
    @GetMapping("/repetidos")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> candidatosRepetidos() {
        try {
            List<Object[]> rows = candidatoRepository.findDuplicateCpfs();
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object[] r : rows) {
                String cpf = r[0] != null ? r[0].toString() : null;
                long total = 0;
                if (r[1] instanceof Number)
                    total = ((Number) r[1]).longValue();
                else if (r[1] != null)
                    total = Long.parseLong(r[1].toString());
                out.add(Map.of("cpf", cpf, "total", total));
            }
            return ResponseEntity.ok(out);
        } catch (Exception ex) {
            logger.error("Erro ao listar candidatos repetidos", ex);
            return ResponseEntity.status(500).body(List.of(Map.of("error", "Erro ao listar")));
        }
    }

    @GetMapping("/novo")
    public String novoForm(Model model) {
        model.addAttribute("candidato", new Candidato());
        // provide list of campuses for selection in the form
        model.addAttribute("campuses", campusRepository.findAll());
        return "cadastro-candidato";
    }

    @GetMapping
    public String index() {
        // redirect bare /candidatos GET requests to the form to avoid
        // NoResourceFoundException
        return "redirect:/candidatos/novo";
    }

    @GetMapping("/list")
    public String listAll(
            @RequestParam(name = "order", required = false, defaultValue = "oldest") String order,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "campusId", required = false) Long campusId,
            @RequestParam(name = "genero", required = false) String genero,
            @RequestParam(name = "idade", required = false) String idade,
            @RequestParam(name = "situacao", required = false) String situacao,
            @RequestParam(name = "turno", required = false) String turno, // Parâmetro recebido
            @RequestParam(name = "page", required = false, defaultValue = "0") int page,
            @RequestParam(name = "size", required = false, defaultValue = "20") int size,
            Model model) {

        // normalize incoming filter params: treat empty strings as null
        String generoNorm = (genero != null && !genero.isBlank()) ? genero.trim().toUpperCase() : null;
        Character generoChar = (generoNorm != null && !generoNorm.isEmpty()) ? generoNorm.charAt(0) : null;
        String idadeNorm = (idade != null && !idade.isBlank()) ? idade.trim() : null;

        // normalize situacao param: expected values são os nomes do enum SituacaoCandidato
        String situacaoFilter = (situacao != null && !situacao.isBlank()) ? situacao.trim() : null;

        // NOVO: Normalize o parâmetro turno (String)
        String turnoNorm = (turno != null && !turno.isBlank()) ? turno.trim() : null;

        // MUDANÇA: Inclua 'turnoNorm' na verificação 'usingFilters'
        boolean usingFilters = (q != null && !q.isBlank()) || campusId != null || generoChar != null
                || (idadeNorm != null && !idadeNorm.isBlank()) || situacaoFilter != null || turnoNorm != null;

        // Debug logging: show incoming params and whether filters will be applied
        logger.info(
                "Candidatos list request - order={}, q={}, campusId={}, genero={}, idade={}, situacao={}, turno={}, page={}, size={} → usingFilters={}",
                order, q, campusId, generoChar, idadeNorm, situacaoFilter, turnoNorm, page, size, usingFilters);

        org.springframework.data.domain.Sort sort;
        if (usingFilters) {
            // native query expects DB column names for ORDER BY
            if ("oldest".equalsIgnoreCase(order)) {
                sort = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC,
                        "data_inscricao", "hora_inscricao");
            } else {
                sort = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,
                        "data_inscricao", "hora_inscricao");
            }
        } else {
            // JPA property names for non-native repository methods
            if ("oldest".equalsIgnoreCase(order)) {
                sort = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC,
                        "dataInscricao", "horaInscricao");
            } else {
                sort = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,
                        "dataInscricao", "horaInscricao");
            }
        }

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size,
                sort);
        // defensive: ensure page is not negative (prevents IllegalArgumentException
        // when PageRequest.of is called)
        if (page < 0) {
            logger.warn("Received negative page index ({}). Clamping to 0.", page);
            page = 0;
            pageable = org.springframework.data.domain.PageRequest.of(page, size, sort);
        }
        org.springframework.data.domain.Page<com.example.energif.model.Candidato> pageResult;
        // Prepare a lowercase trimmed q for queries that compare with lower(...)
        String qParam = (q != null && !q.isBlank()) ? q.trim().toLowerCase() : null;

        if (usingFilters) {
            // MUDANÇA: Passando o novo parâmetro 'turnoNorm' e situacaoFilter em vez de
            // habilitadoFilter
            pageResult = candidatoRepository.searchCombined(qParam, campusId, generoChar, idadeNorm, situacaoFilter,
                    turnoNorm, pageable);
        } else {
            pageResult = candidatoRepository.findAll(pageable);
        }

        model.addAttribute("selectedOrder", order);
        model.addAttribute("q", q);
        model.addAttribute("selectedCampus", campusId);
        model.addAttribute("selectedGenero", generoNorm);
        model.addAttribute("selectedIdade", idadeNorm);
        model.addAttribute("selectedSituacao", situacaoFilter);
        model.addAttribute("selectedTurno", turnoNorm); // MUDANÇA: Adicionando o valor normalizado ao modelo
        model.addAttribute("campuses", campusRepository.findAll());
        model.addAttribute("motivos", motivoRepository.findAll());
        // provide list of distinct turnos for report/filter
        try {
            model.addAttribute("turnos", candidatoRepository.findDistinctTurno());
        } catch (Exception ex) {
            model.addAttribute("turnos", java.util.List.of());
        }
        // Estatísticas dinâmicas por situação (respeitando os filtros aplicados na listagem)
        long totalPendentes = 0L;
        long totalClassificados = 0L;
        long totalHabilitados = 0L;
        long totalEliminados = 0L;
        long totalCadastroReserva = 0L;

        try {
            List<Object[]> situacaoCounts = candidatoRepository.countSituacaoByFilters(
                    qParam,
                    campusId,
                    generoChar,
                    idadeNorm,
                    situacaoFilter,
                    turnoNorm);

            for (Object[] row : situacaoCounts) {
                if (row == null || row.length < 2 || row[0] == null || row[1] == null) {
                    continue;
                }

                String situacaoNome = String.valueOf(row[0]).trim().toUpperCase();
                long total = (row[1] instanceof Number) ? ((Number) row[1]).longValue()
                        : Long.parseLong(String.valueOf(row[1]));

                switch (situacaoNome) {
                    case "PENDENTE" -> totalPendentes = total;
                    case "CLASSIFICADO" -> totalClassificados = total;
                    case "HABILITADO" -> totalHabilitados = total;
                    case "ELIMINADO" -> totalEliminados = total;
                    case "CADASTRO_RESERVA" -> totalCadastroReserva = total;
                    default -> {
                    }
                }
            }
        } catch (Exception ex) {
            logger.warn("Falha ao calcular estatísticas filtradas. Mantendo contadores zerados.", ex);
        }

        model.addAttribute("totalPendentes", totalPendentes);
        model.addAttribute("totalClassificados", totalClassificados);
        model.addAttribute("totalHabilitados", totalHabilitados);
        model.addAttribute("totalEliminados", totalEliminados);
        model.addAttribute("totalCadastroReserva", totalCadastroReserva);

        model.addAttribute("candidatosPage", pageResult);
        model.addAttribute("candidatos", pageResult.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        return "list";
    }

    @PostMapping
    public String criar(@ModelAttribute Candidato candidato,
            @RequestParam(required = false) String campusId,
            Model model) {
        logger.info("Recebendo candidato para salvar: {}", candidato);
        try {
            // Resolver campus baseado no ID ou nome
            if (campusId != null && !campusId.isBlank()) {
                Campus campus = resolveCampus(campusId);
                if (campus != null) {
                    candidato.setCampus(campus);
                    logger.info("Campus associado: {}", campus.getNome());
                }
            }

            Candidato saved = candidatoService.salvarComAtualizacao(candidato);
            logger.info("Candidato salvo: id={}, Campus: {}, Tipo Vaga: {}",
                    saved.getId(),
                    saved.getCampus() != null ? saved.getCampus().getNome() : "Nenhum",
                    saved.getTipoVaga());

            return "redirect:/candidatos/novo?success";
        } catch (Exception e) {
            logger.error("Erro ao salvar candidato", e);
            return "redirect:/candidatos/novo?error";
        }
    }

    @PostMapping("/import")
    public String importXlsx(@RequestPart("file") MultipartFile file,
            @RequestParam(name = "editalDescricao", required = false) String editalDescricao,
            Model model) {
        if (file == null || file.isEmpty()) {
            return "redirect:/candidatos/list?order=oldest&error=empty";
        }
        try {
            // save to temp file for processing by the importer
            java.nio.file.Path tmp = java.nio.file.Files.createTempFile("upload-", ".xlsx");
            try (java.io.InputStream in = file.getInputStream()) {
                java.nio.file.Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            logger.info("Uploaded file saved to {}", tmp);
            // invoke Filtro importer to parse and import rows
            try {
                int imported = filtro.importarXlsx(tmp.toFile(), editalDescricao);
                return "redirect:/candidatos/list?order=oldest&import=ok&count=" + imported;
            } catch (Exception ex) {
                logger.error("Erro ao importar xlsx", ex);
                return "redirect:/candidatos/list?order=oldest&import=error";
            }
        } catch (Exception e) {
            logger.error("Erro ao receber arquivo de importacao", e);
            return "redirect:/candidatos/list?order=oldest&import=error";
        }
    }

    @PostMapping("/{id}/deletar")
    @ResponseBody
    public ResponseEntity<?> deletarCandidato(@PathVariable("id") Long id) {
        logger.info("Exclusão de candidato iniciada: ID={}", id);
        try {
            Candidato candidato = candidatoRepository.findById(id).orElse(null);
            if (candidato == null) {
                return ResponseEntity.ok(Map.of("sucesso", false, "mensagem", "Candidato não encontrado"));
            }

            String nomeCandidato = candidato.getNome();
            String cpfCandidato = candidato.getCpf();

            candidatoRepository.deleteById(id);
            
            logger.info("Candidato deletado com sucesso: ID={}, Nome={}, CPF={}", id, nomeCandidato, cpfCandidato);
            return ResponseEntity.ok(Map.of("sucesso", true, "mensagem", "Candidato '" + nomeCandidato + "' foi deletado com sucesso"));
        } catch (Exception ex) {
            logger.error("Erro ao deletar candidato {}", id, ex);
            return ResponseEntity.ok(Map.of("sucesso", false, "mensagem", "Erro ao deletar candidato: " + ex.getMessage()));
        }
    }

    @PostMapping("/delete-all")
    public String deleteAll() {
        logger.info("Exclusão em massa iniciada: candidatos, campus_edital, campus");
        try {
            // Remove campus-edital links first to avoid FK constraint
            campusEditalRepository.deleteAll();
        } catch (Exception ex) {
            logger.warn("Falha ao apagar campus_edital antes de apagar campus: {}", ex.getMessage());
        }
        // delete candidates and campuses
        candidatoRepository.deleteAll();
        try {
            campusRepository.deleteAll();
        } catch (Exception ex) {
            logger.error("Erro ao apagar campi: {}", ex.getMessage());
        }
        return "redirect:/candidatos/list?deleted=all";
    }

    // No CandidatoController - método habilitarCandidato com logs detalhados:
    // No CandidatoController - atualize o método habilitarCandidato:
    @PostMapping("/{id}/habilitar")
    @ResponseBody
    public ResponseEntity<?> habilitarCandidato(@PathVariable("id") Long id,
            @RequestParam(name = "situacao", required = false) String situacao,
            @RequestParam(name = "motivo", required = false) String motivo,
            HttpServletRequest request) {

        logger.info("=== SOLICITAÇÃO DE HABILITAÇÃO ===");
        logger.info("Candidato ID: {}, Situação: {}, Motivo: {}", id, situacao, motivo);

        try {
            Map<String, Object> resultado;

            // normalize incoming situacao - esperamos valores em UPPERCASE (CLASSIFICADO, HABILITADO, etc)
            String situacaoNormalized = situacao != null ? situacao.trim().toUpperCase() : null;

            if ("CLASSIFICADO".equals(situacaoNormalized)) {
                logger.info("Tentando classificar candidato {}", id);
                Candidato cand = candidatoRepository.findById(id).orElse(null);
                if (cand == null)
                    return ResponseEntity.ok(Map.of("sucesso", false, "mensagem", "Candidato não encontrado"));
                
                // VERIFICAÇÃO: Se já foi classificado, retornar erro
                if (cand.getSituacao() == SituacaoCandidato.CLASSIFICADO) {
                    logger.warn("Tentativa de reclassificar candidato {} que já está classificado", id);
                    return ResponseEntity.ok(Map.of(
                            "sucesso", false,
                            "mensagem", "Candidato já foi classificado anteriormente. Não é possível reclassificar.",
                            "campusNome", cand.getCampus() != null ? cand.getCampus().getNome() : "N/A",
                            "turno", cand.getTurno() != null ? cand.getTurno() : "N/A"));
                }
                
                // VERIFICAÇÃO: Se já foi habilitado, retornar erro
                if (cand.getSituacao() == SituacaoCandidato.HABILITADO) {
                    logger.warn("Tentativa de classificar candidato {} que já está habilitado", id);
                    return ResponseEntity.ok(Map.of(
                            "sucesso", false,
                            "mensagem", "Candidato já foi habilitado. Não é possível reclassificar.",
                            "campusNome", cand.getCampus() != null ? cand.getCampus().getNome() : "N/A",
                            "turno", cand.getTurno() != null ? cand.getTurno() : "N/A"));
                }
                
                Campus campus = cand.getCampus();
                if (campus == null)
                    return ResponseEntity.ok(Map.of("sucesso", false, "mensagem", "Candidato não possui campus definido"));
                
                // Buscar vagas no CampusEditalTurno (novo sistema)
                Edital edital = cand.getEdital();
                if (edital == null) {
                    return ResponseEntity.ok(Map.of("sucesso", false, "mensagem", "Candidato não possui edital definido"));
                }
                
                com.example.energif.model.CampusEdital campusEdital = campusEditalRepository.findByCampusAndEdital(campus, edital);
                if (campusEdital == null) {
                    return ResponseEntity.ok(Map.of("sucesso", false, "mensagem", "CampusEdital não encontrado para este candidato"));
                }
                
                // Buscar CampusEditalTurno pelo turno do candidato
                String turno = cand.getTurno() != null ? cand.getTurno() : "UNICO";
                com.example.energif.model.CampusEditalTurno turnoVagas = campusEditalTurnoRepository.findByCampusEditalAndTurno(campusEdital, turno);
                
                if (turnoVagas == null) {
                    return ResponseEntity.ok(Map.of("sucesso", false, "mensagem", "Nenhuma vaga configurada para o turno '" + turno + "'"));
                }
                
                // Validar se existe vaga de Classificado disponível
                int vagasDisponiveisClassificado = turnoVagas.getVagasClassificadoDisponiveis();
                if (vagasDisponiveisClassificado <= 0) {
                    resultado = Map.of(
                            "sucesso", false,
                            "mensagem", "Não há vagas de Classificado disponíveis no campus " + campus.getNome() + " para o turno " + turno,
                            "campusNome", campus.getNome(),
                            "turno", turno,
                            "vagasDisponiveis", vagasDisponiveisClassificado);
                    return ResponseEntity.ok(resultado);
                }

                // Consumir uma vaga de Classificado (baseado no gênero do candidato)
                Character genero = cand.getGenero();
                if (com.example.energif.model.Genero.FEMININO.getCodigo().equals(genero)) {
                    turnoVagas.setVagasClassificadoFemininoOcupadas(turnoVagas.getVagasClassificadoFemininoOcupadas() + 1);
                } else {
                    turnoVagas.setVagasClassificadoMasculinoOcupadas(turnoVagas.getVagasClassificadoMasculinoOcupadas() + 1);
                }
                campusEditalTurnoRepository.save(turnoVagas);
                
                cand.setSituacao(SituacaoCandidato.CLASSIFICADO);
                cand.setMotivoNaoClassificacao(null);
                candidatoRepository.save(cand);
                
                int vagasRestantes = turnoVagas.getVagasClassificadoDisponiveis();
                resultado = Map.of(
                        "sucesso", true,
                        "mensagem", "Candidato marcado como classificado com sucesso",
                        "vagasDisponiveis", vagasRestantes,
                        "campusNome", campus.getNome(),
                        "turno", turno);
                return ResponseEntity.ok(resultado);

            } else if ("ELIMINADO".equals(situacaoNormalized)) {
                logger.info("Tentando marcar candidato {} como ELIMINADO", id);
                Candidato cand = candidatoRepository.findById(id).orElse(null);
                if (cand == null)
                    return ResponseEntity.badRequest()
                            .body(Map.of("sucesso", false, "mensagem", "Candidato não encontrado"));
                // validate motivo: do not allow marking as ELIMINADO without motivo
                if (motivo == null || motivo.isBlank()) {
                    return ResponseEntity.ok(Map.of("sucesso", false, "mensagem", "Selecione um motivo antes de marcar como eliminado"));
                }

                cand.setSituacao(SituacaoCandidato.ELIMINADO);
                cand.setMotivoNaoClassificacao(motivo);
                candidatoRepository.save(cand);
                return ResponseEntity.ok(Map.of("sucesso", true, "mensagem", "Candidato marcado como eliminado"));

            } else if ("CADASTRO_RESERVA".equals(situacaoNormalized)) {
                // Marca o candidato como Cadastro de Reserva (suplente) e salva
                logger.info("Marcando candidato {} como CADASTRO_RESERVA", id);
                Candidato candidato = candidatoRepository.findById(id).orElse(null);
                if (candidato == null) {
                    logger.warn("Candidato {} não encontrado para marcar cadastro de reserva", id);
                    return ResponseEntity.badRequest()
                            .body(Map.of("sucesso", false, "mensagem", "Candidato não encontrado"));
                }

                // Validar se existem vagas de cadastro de reserva disponíveis
                Campus campus = candidato.getCampus();
                if (campus == null) {
                    logger.error("Candidato {} não possui campus definido", id);
                    return ResponseEntity.badRequest()
                            .body(Map.of("sucesso", false, "mensagem", "Candidato não possui campus definido"));
                }

                // Verificar se existem vagas de cadastro de reserva configuradas
                Integer vagasCadastroReserva = campus.getNumeroVagasCadastroReserva();
                if (vagasCadastroReserva == null || vagasCadastroReserva <= 0) {
                    logger.warn("Nenhuma vaga de cadastro de reserva disponível no campus {} para candidato {}",
                            campus.getNome(), id);
                    resultado = Map.of(
                            "sucesso", false,
                            "mensagem",
                            "Não existem vagas de cadastro de reserva disponíveis no campus " + campus.getNome()
                                    + ". Por favor, configure o número de vagas de reserva no cadastro do campus.",
                            "campusNome", campus.getNome(),
                            "vagasDisponiveis", 0);
                    return ResponseEntity.ok(resultado);
                }

                candidato.setTipoVaga(TipoVaga.CADASTRO_RESERVA);
                candidato.setSituacao(SituacaoCandidato.HABILITADO);
                candidato.setMotivoNaoClassificacao(null);
                candidatoRepository.save(candidato);
                resultado = Map.of(
                        "sucesso", true,
                        "mensagem", "Candidato marcado como cadastro de reserva com sucesso",
                        "tipoVaga", "CADASTRO_RESERVA");
                return ResponseEntity.ok(resultado);

            } else if ("HABILITADO".equals(situacaoNormalized)) {
                // mark as HABILITADO — com verificação de vagas
                logger.info("Tentando marcar candidato {} como HABILITADO", id);
                Candidato cand = candidatoRepository.findById(id).orElse(null);
                if (cand == null)
                    return ResponseEntity.ok(Map.of("sucesso", false, "mensagem", "Candidato não encontrado"));
                
                // VERIFICAÇÃO: Se já foi habilitado, retornar erro
                if (cand.getSituacao() == SituacaoCandidato.HABILITADO) {
                    logger.warn("Tentativa de reabilitar candidato {} que já está habilitado", id);
                    return ResponseEntity.ok(Map.of(
                            "sucesso", false,
                            "mensagem", "Candidato já foi habilitado anteriormente. Não é possível reabilitar.",
                            "campusNome", cand.getCampus() != null ? cand.getCampus().getNome() : "N/A",
                            "turno", cand.getTurno() != null ? cand.getTurno() : "N/A"));
                }
                
                Campus campus = cand.getCampus();
                if (campus == null)
                    return ResponseEntity.ok(Map.of("sucesso", false, "mensagem", "Candidato não possui campus definido"));
                
                // Buscar vagas no CampusEditalTurno (novo sistema)
                Edital edital = cand.getEdital();
                if (edital == null) {
                    return ResponseEntity.ok(Map.of("sucesso", false, "mensagem", "Candidato não possui edital definido"));
                }
                
                com.example.energif.model.CampusEdital campusEdital = campusEditalRepository.findByCampusAndEdital(campus, edital);
                if (campusEdital == null) {
                    return ResponseEntity.ok(Map.of("sucesso", false, "mensagem", "CampusEdital não encontrado para este candidato"));
                }
                
                // Buscar CampusEditalTurno pelo turno do candidato
                String turno = cand.getTurno() != null ? cand.getTurno() : "UNICO";
                com.example.energif.model.CampusEditalTurno turnoVagas = campusEditalTurnoRepository.findByCampusEditalAndTurno(campusEdital, turno);
                
                if (turnoVagas == null) {
                    return ResponseEntity.ok(Map.of("sucesso", false, "mensagem", "Nenhuma vaga configurada para o turno '" + turno + "'"));
                }
                
                // Validar se existe vaga de Habilitado disponível
                int vagasDisponiveisHabilitado = turnoVagas.getVagasHabilitadoDisponiveis();
                if (vagasDisponiveisHabilitado <= 0) {
                    resultado = Map.of(
                            "sucesso", false,
                            "mensagem", "Não há vagas de Habilitado disponíveis no campus " + campus.getNome() + " para o turno " + turno,
                            "campusNome", campus.getNome(),
                            "turno", turno,
                            "vagasDisponiveis", vagasDisponiveisHabilitado);
                    return ResponseEntity.ok(resultado);
                }

                // Consumir uma vaga de Habilitado (baseado no gênero do candidato)
                Character genero = cand.getGenero();
                if (com.example.energif.model.Genero.FEMININO.getCodigo().equals(genero)) {
                    turnoVagas.setVagasHabilitadoFemininoOcupadas(turnoVagas.getVagasHabilitadoFemininoOcupadas() + 1);
                } else {
                    turnoVagas.setVagasHabilitadoMasculinoOcupadas(turnoVagas.getVagasHabilitadoMasculinoOcupadas() + 1);
                }
                campusEditalTurnoRepository.save(turnoVagas);
                
                cand.setSituacao(SituacaoCandidato.HABILITADO);
                cand.setTipoVaga(TipoVaga.HABILITADO);
                cand.setMotivoNaoClassificacao(null);
                candidatoRepository.save(cand);
                
                int vagasRestantes = turnoVagas.getVagasHabilitadoDisponiveis();
                resultado = Map.of(
                        "sucesso", true,
                        "mensagem", "Candidato marcado como habilitado com sucesso",
                        "vagasDisponiveis", vagasRestantes,
                        "campusNome", campus.getNome(),
                        "turno", turno);
                return ResponseEntity.ok(resultado);


            } else if ("PENDENTE".equals(situacaoNormalized)) {
                // mark as PENDENTE
                Candidato cand = candidatoRepository.findById(id).orElse(null);
                if (cand == null)
                    return ResponseEntity.badRequest()
                            .body(Map.of("sucesso", false, "mensagem", "Candidato não encontrado"));
                cand.setSituacao(SituacaoCandidato.PENDENTE);
                cand.setMotivoNaoClassificacao(null);
                candidatoRepository.save(cand);
                return ResponseEntity.ok(Map.of("sucesso", true, "mensagem", "Candidato marcado como pendente"));

            } else {
                logger.warn("Situação inválida: {}", situacao);
                resultado = Map.of(
                        "sucesso", false,
                        "mensagem", "Situação inválida: " + situacao);
                return ResponseEntity.ok(resultado);
            }

        } catch (Exception ex) {
            logger.error("ERRO ao atualizar situação do candidato {}", id, ex);
            Map<String, Object> erro = Map.of(
                    "sucesso", false,
                    "mensagem", "Erro interno: " + ex.getMessage());
            return ResponseEntity.badRequest().body(erro);
        }
    }

    // Generate PDF report for a given campus and optional turno
    @GetMapping("/report")
    public void gerarRelatorio(@RequestParam(name = "campusId", required = false) Long campusId,
            @RequestParam(name = "turno", required = false) String turno,
            jakarta.servlet.http.HttpServletResponse response) {
        try {
            // prepare filename
            String filename = "relatorio_resultado_final.pdf";
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

            // build PDF using OpenPDF
            com.lowagie.text.Document doc = new com.lowagie.text.Document();
            com.lowagie.text.pdf.PdfWriter.getInstance(doc, response.getOutputStream());
            doc.open();

            // Se um campus específico foi solicitado
            if (campusId != null) {
                com.example.energif.model.Campus campus = campusRepository.findById(campusId).orElse(null);
                java.util.List<com.example.energif.model.Candidato> candidatos;
                
                if (turno != null && !turno.isBlank()) {
                    candidatos = candidatoRepository
                            .findAllByCampusIdAndTurnoOrderByDataInscricaoAscHoraInscricao(campusId, turno);
                } else {
                    candidatos = candidatoRepository.findAllByCampusIdOrderByDataInscricaoAscHoraInscricao(campusId);
                }

                // Agrupar por campus para usar o serviço
                java.util.Map<com.example.energif.model.Campus, java.util.List<com.example.energif.model.Candidato>> grouped = 
                    java.util.Map.of(campus, candidatos);
                java.util.List<com.example.energif.model.Campus> campusList = java.util.List.of(campus);
                
                relatorioService.gerarRelatorioPDF(doc, campusList, grouped, turno);
            } else {
                // Sem campus específico: gerar para todos
                java.util.List<com.example.energif.model.Candidato> all = candidatoRepository.findAll(
                        org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC,
                                "dataInscricao", "horaInscricao"));
                
                if (turno != null && !turno.isBlank()) {
                    all = all.stream().filter(c -> c.getTurno() != null && turno.equals(c.getTurno())).toList();
                }

                java.util.Map<com.example.energif.model.Campus, java.util.List<com.example.energif.model.Candidato>> grouped = all
                        .stream()
                        .collect(java.util.stream.Collectors.groupingBy(com.example.energif.model.Candidato::getCampus,
                                java.util.LinkedHashMap::new, java.util.stream.Collectors.toList()));

                java.util.List<com.example.energif.model.Campus> campusList = new java.util.ArrayList<>(grouped.keySet());
                relatorioService.gerarRelatorioPDF(doc, campusList, grouped, turno);
            }

            doc.close();

        } catch (Exception e) {
            logger.error("Erro ao gerar relatorio PDF", e);
            try {
                response.sendError(500, "Erro ao gerar relatorio: " + e.getMessage());
            } catch (java.io.IOException ex) {
            }
        }
    }

    private Campus resolveCampus(String campusId) {
        try {
            Long cid = Long.parseLong(campusId);
            return campusRepository.findById(cid).orElse(null);
        } catch (NumberFormatException nfe) {
            return campusRepository.findByNome(campusId);
        }
    }

    // Generate PDF report for preliminary list of registered candidates
    @GetMapping("/report-preliminar")
    public void gerarRelatorioPreliminar(
            @RequestParam(name = "order", required = false, defaultValue = "newest") String order,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "campusId", required = false) Long campusId,
            @RequestParam(name = "genero", required = false) String genero,
            @RequestParam(name = "idade", required = false) String idade,
            @RequestParam(name = "situacao", required = false) String situacao,
            @RequestParam(name = "turno", required = false) String turno,
            jakarta.servlet.http.HttpServletResponse response) {
        try {
            // prepare filename
            String filename = "relacao_preliminar_candidatos_inscritos.pdf";
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

            // Normalize filter params
            String generoNorm = (genero != null && !genero.isBlank()) ? genero.trim().toUpperCase() : null;
            Character generoChar = (generoNorm != null && !generoNorm.isEmpty()) ? generoNorm.charAt(0) : null;
            String idadeNorm = (idade != null && !idade.isBlank()) ? idade.trim() : null;
            String situacaoFilter = (situacao != null && !situacao.isBlank()) ? situacao.trim() : null;
            String turnoNorm = (turno != null && !turno.isBlank()) ? turno.trim() : null;

            // Build list of candidates
            java.util.List<com.example.energif.model.Candidato> candidatos;
            boolean usingFilters = (q != null && !q.isBlank()) || campusId != null || generoChar != null
                    || (idadeNorm != null && !idadeNorm.isBlank()) || situacaoFilter != null || turnoNorm != null;

            if (usingFilters) {
                String qParam = (q != null && !q.isBlank()) ? q.trim().toLowerCase() : null;
                org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 
                        10000, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "nome"));
                org.springframework.data.domain.Page<com.example.energif.model.Candidato> pageResult = 
                        candidatoRepository.searchCombined(qParam, campusId, generoChar, idadeNorm, situacaoFilter, turnoNorm, pageable);
                candidatos = pageResult.getContent();
            } else {
                candidatos = candidatoRepository.findAll(
                        org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "nome"));
            }

            // Get edital description if there are candidatos
            String editalDescricao = "PROEXC nº 06/2024";
            if (!candidatos.isEmpty() && candidatos.get(0).getEdital() != null) {
                editalDescricao = candidatos.get(0).getEdital().getDescricao();
            }

            // Build PDF
            com.lowagie.text.Document doc = new com.lowagie.text.Document();
            com.lowagie.text.pdf.PdfWriter.getInstance(doc, response.getOutputStream());
            doc.open();

            relatorioService.gerarRelatorioPreliminar(doc, candidatos, editalDescricao);

            doc.close();

        } catch (Exception e) {
            logger.error("Erro ao gerar relatorio preliminar PDF", e);
            try {
                response.sendError(500, "Erro ao gerar relatorio: " + e.getMessage());
            } catch (java.io.IOException ex) {
            }
        }
    }

}
