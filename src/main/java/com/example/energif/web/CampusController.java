package com.example.energif.web;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.energif.model.Campus;
import com.example.energif.model.CampusEdital;
import com.example.energif.model.CampusEditalTurno;
import com.example.energif.repository.CampusEditalTurnoRepository;
import com.example.energif.repository.CampusRepository;
import com.example.energif.service.AlocacaoVagaService;

@Controller
@RequestMapping("/campus")
public class CampusController {

    private static final Logger logger = LoggerFactory.getLogger(CampusController.class);

    private final CampusRepository campusRepository;
    private final com.example.energif.repository.CampusEditalRepository campusEditalRepository;
    private final CampusEditalTurnoRepository campusEditalTurnoRepository;
    private final AlocacaoVagaService alocacaoVagaService;
    private final com.example.energif.repository.CandidatoRepository candidatoRepository;

    public CampusController(CampusRepository campusRepository,
            com.example.energif.repository.CampusEditalRepository campusEditalRepository,
            CampusEditalTurnoRepository campusEditalTurnoRepository,
            AlocacaoVagaService alocacaoVagaService,
            com.example.energif.repository.CandidatoRepository candidatoRepository) {
        this.campusRepository = campusRepository;
        this.campusEditalRepository = campusEditalRepository;
        this.campusEditalTurnoRepository = campusEditalTurnoRepository;
        this.alocacaoVagaService = alocacaoVagaService;
        this.candidatoRepository = candidatoRepository;
    }

    @GetMapping("/novo")
    public String novoForm(Model model) {
        model.addAttribute("campus", new Campus());
        model.addAttribute("campuses", campusRepository.findAll(Sort.by("nome")));
        return "cadastro-campus";
    }

    @GetMapping("/list")
    public String listarCampus(Model model) {
        var allCampuses = campusRepository.findAll(Sort.by("nome"));
        
        var turnos = campusEditalRepository.findAll()
                .stream()
                .flatMap(ce -> {
                    if (ce.getCampus() == null) {
                        return java.util.stream.Stream.empty();
                    }
                var candidatosDoCampusEdital = candidatoRepository
                    .findByCampusIdAndEditalIdOrderByDataInscricaoAscHoraInscricaoAsc(
                        ce.getCampus().getId(),
                        ce.getEdital().getId());
                        return java.util.Optional.ofNullable(ce.getTurnos())
                            .orElse(java.util.Collections.emptyList())
                            .stream()
                            .map(t -> {
                    var candidatosDoTurno = candidatosDoCampusEdital.stream()
                        .filter(c -> t.getTurno().equals(c.getTurno()))
                        .toList();

                    int totalCandidatosInscritos = candidatosDoTurno.size();
                    int candidatosEliminados = (int) candidatosDoTurno.stream()
                        .filter(c -> c.getSituacao() == com.example.energif.model.SituacaoCandidato.ELIMINADO)
                        .count();
                    int candidatosHabilitados = (int) candidatosDoTurno.stream()
                        .filter(c -> c.getSituacao() == com.example.energif.model.SituacaoCandidato.HABILITADO)
                        .count();
                    int candidatosPendentes = (int) candidatosDoTurno.stream()
                        .filter(c -> c.getSituacao() == com.example.energif.model.SituacaoCandidato.PENDENTE)
                        .count();

                    return TurnoView.from(ce, t, candidatosPendentes, candidatosEliminados, candidatosHabilitados, totalCandidatosInscritos);
                            });
                })
                .sorted((a, b) -> {
                    int cmpCampus = Objects.toString(a.getCampusNome(), "")
                        .compareTo(Objects.toString(b.getCampusNome(), ""));
                    if (cmpCampus != 0)
                        return cmpCampus;
                    int cmpEdital = Objects.toString(a.getEditalDescricao(), "")
                        .compareTo(Objects.toString(b.getEditalDescricao(), ""));
                    if (cmpEdital != 0)
                        return cmpEdital;
                    return Objects.toString(a.getTurno(), "").compareTo(Objects.toString(b.getTurno(), ""));
                })
                .toList();

        var campusGrupos = montarCampusGrupos(allCampuses, turnos);

        model.addAttribute("turnos", turnos);
        model.addAttribute("campuses", allCampuses);
        model.addAttribute("campusGrupos", campusGrupos);
        return "lista-campus";
    }

    private List<CampusGrupoView> montarCampusGrupos(List<Campus> campuses, List<TurnoView> turnos) {
        var campusGrupos = new ArrayList<CampusGrupoView>();
        for (var campus : campuses) {
            var turnosDoCampus = turnos.stream()
                    .filter(turno -> Objects.equals(turno.getCampusId(), campus.getId()))
                    .sorted(this::compararTurnos)
                    .toList();
            campusGrupos.add(new CampusGrupoView(campus, turnosDoCampus));
        }
        return campusGrupos;
    }

    private int compararTurnos(TurnoView turno1, TurnoView turno2) {
        int ordem1 = getOrdenTurno(turno1.getTurno());
        int ordem2 = getOrdenTurno(turno2.getTurno());
        return Integer.compare(ordem1, ordem2);
    }

    private int getOrdenTurno(String turno) {
        if (turno == null) return 999;
        String turnoNormalizado = turno.trim().toLowerCase();
        if (turnoNormalizado.contains("manhã") || turnoNormalizado.contains("manha")) {
            return 1;
        } else if (turnoNormalizado.contains("tarde")) {
            return 2;
        } else if (turnoNormalizado.contains("noite")) {
            return 3;
        }
        return 999;
    }

    @PostMapping
    public String criar(@ModelAttribute Campus campus) {
        logger.info("Criando campus: {}", campus.getNome());
        Campus existing = campusRepository.findByNome(campus.getNome());
        if (existing != null) {
            existing.setNumeroVagasAmplaConcorrencia(campus.getNumeroVagasAmplaConcorrencia());
            existing.setNumeroVagasReservadas(campus.getNumeroVagasReservadas());
            existing.setNumeroVagasCadastroReserva(campus.getNumeroVagasCadastroReserva());
            existing.setNumeroVagasClassificado(campus.getNumeroVagasClassificado());
            existing.setNumeroVagasHabilitado(campus.getNumeroVagasHabilitado());
            campusRepository.save(existing);
            return "redirect:/campus/novo?updated";
        }
        campusRepository.save(campus);
        return "redirect:/campus/novo?success";
    }

    @PostMapping("/{id}/editar-ajax")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> editarCampusAjax(@PathVariable("id") Long id,
            @RequestParam Integer numeroVagasReservadas,
            @RequestParam Integer numeroVagasAmplaConcorrencia,
            @RequestParam(required = false, defaultValue = "0") Integer numeroVagasCadastroReserva,
            @RequestParam(required = false, defaultValue = "0") Integer numeroVagasClassificado,
            @RequestParam(required = false, defaultValue = "0") Integer numeroVagasHabilitado) {
        logger.info("Iniciando atualização do turno/campus ID: {}", id);

        try {
            // Verificar se é um ID de CampusEditalTurno (novo) ou Campus (legado)
            CampusEditalTurno turno = campusEditalTurnoRepository.findById(id).orElse(null);

            if (turno != null) {
                logger.info("Atualizando CampusEditalTurno ID: {}", id);
                // Atualizar turno
                turno.setNumeroVagasReservadas(numeroVagasReservadas);
                turno.setNumeroVagasAmplaConcorrencia(numeroVagasAmplaConcorrencia);
                turno.setNumeroVagasCadastroReserva(numeroVagasCadastroReserva);
                turno.setNumeroVagasClassificado(numeroVagasClassificado);
                turno.setNumeroVagasHabilitado(numeroVagasHabilitado);
                CampusEditalTurno turnoSalvo = campusEditalTurnoRepository.save(turno);

                Map<String, Object> campusData = new HashMap<>();
                campusData.put("id", turnoSalvo.getId());
                campusData.put("turno", turnoSalvo.getTurno());
                campusData.put("numeroVagasReservadas", turnoSalvo.getNumeroVagasReservadas());
                campusData.put("numeroVagasAmplaConcorrencia", turnoSalvo.getNumeroVagasAmplaConcorrencia());
                campusData.put("numeroVagasCadastroReserva", turnoSalvo.getNumeroVagasCadastroReserva());
                campusData.put("numeroVagasClassificado", turnoSalvo.getNumeroVagasClassificado());
                campusData.put("numeroVagasHabilitado", turnoSalvo.getNumeroVagasHabilitado());
                campusData.put("vagasReservadasOcupadas", turnoSalvo.getVagasReservadasOcupadas());
                campusData.put("vagasAmplaOcupadas", turnoSalvo.getVagasAmplaOcupadas());
                campusData.put("vagasClassificadoOcupadas", turnoSalvo.getVagasClassificadoOcupadas());
                campusData.put("vagasHabilitadoOcupadas", turnoSalvo.getVagasHabilitadoOcupadas());
                campusData.put("vagasReservadasDisponiveis", turnoSalvo.getVagasReservadasDisponiveis());
                campusData.put("vagasAmplaDisponiveis", turnoSalvo.getVagasAmplaDisponiveis());
                campusData.put("vagasClassificadoDisponiveis", turnoSalvo.getVagasClassificadoDisponiveis());
                campusData.put("vagasHabilitadoDisponiveis", turnoSalvo.getVagasHabilitadoDisponiveis());

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("campus", campusData);

                logger.info("Turno atualizado com sucesso. Respondendo com JSON...");
                return ResponseEntity.ok(response);
            } else {
                logger.info("CampusEditalTurno não encontrado. Tentando como Campus legacy ID: {}", id);
                // Tentar como Campus (compatibilidade legada)
                Campus campus = campusRepository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("Campus ou Turno não encontrado"));

                campus.setNumeroVagasReservadas(numeroVagasReservadas);
                campus.setNumeroVagasAmplaConcorrencia(numeroVagasAmplaConcorrencia);
                campus.setNumeroVagasCadastroReserva(numeroVagasCadastroReserva);
                campus.setNumeroVagasClassificado(numeroVagasClassificado);
                campus.setNumeroVagasHabilitado(numeroVagasHabilitado);
                Campus campusSalvo = campusRepository.save(campus);

                // Retornar os dados atualizados
                Map<String, Object> campusData = new HashMap<>();
                campusData.put("id", campusSalvo.getId());
                campusData.put("nome", campusSalvo.getNome());
                campusData.put("numeroVagasReservadas", campusSalvo.getNumeroVagasReservadas());
                campusData.put("numeroVagasAmplaConcorrencia", campusSalvo.getNumeroVagasAmplaConcorrencia());
                campusData.put("numeroVagasCadastroReserva", campusSalvo.getNumeroVagasCadastroReserva());
                campusData.put("numeroVagasClassificado", campusSalvo.getNumeroVagasClassificado());
                campusData.put("numeroVagasHabilitado", campusSalvo.getNumeroVagasHabilitado());
                campusData.put("vagasReservadasOcupadas", campusSalvo.getVagasReservadasOcupadas());
                campusData.put("vagasAmplaOcupadas", campusSalvo.getVagasAmplaOcupadas());
                campusData.put("vagasClassificadoOcupadas", campusSalvo.getVagasClassificadoOcupadas());
                campusData.put("vagasHabilitadoOcupadas", campusSalvo.getVagasHabilitadoOcupadas());
                campusData.put("vagasReservadasDisponiveis", campusSalvo.getVagasReservadasDisponiveis());
                campusData.put("vagasAmplaDisponiveis", campusSalvo.getVagasAmplaDisponiveis());
                campusData.put("vagasClassificadoDisponiveis", campusSalvo.getVagasClassificadoDisponiveis());
                campusData.put("vagasHabilitadoDisponiveis", campusSalvo.getVagasHabilitadoDisponiveis());

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("campus", campusData);

                logger.info("Campus atualizado com sucesso. Respondendo com JSON...");
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            logger.error("Erro ao editar campus/turno {}: {}", id, e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PostMapping("/{id}/excluir-ajax")
    @ResponseBody
    public ResponseEntity<?> excluirCampusAjax(@PathVariable("id") Long id) {
        try {
            // Verificar se é um ID de CampusEditalTurno (novo) ou Campus (legado)
            CampusEditalTurno turno = campusEditalTurnoRepository.findById(id).orElse(null);

            if (turno != null) {
                // Excluir turno
                // Verificar se há candidatos associados ao turno
                if (turno.getVagasReservadasOcupadas() > 0 || turno.getVagasAmplaOcupadas() > 0) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "success", false,
                            "error", "Não é possível excluir este turno pois existem candidatos associados."));
                }

                campusEditalTurnoRepository.deleteById(id);
                return ResponseEntity.ok(Map.of("success", true));
            } else {
                // Tentar como Campus (compatibilidade legada)
                Campus campus = campusRepository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("Campus ou Turno não encontrado"));

                // Verifica se há candidatos associados ao campus
                if (campus.getCandidatos() != null && !campus.getCandidatos().isEmpty()) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "success", false,
                            "error", "Não é possível excluir o campus pois existem " +
                                    campus.getCandidatos().size() + " candidatos associados a ele."));
                }

                // Verifica se há registros em campus_edital que referenciam este campus
                java.util.List<com.example.energif.model.CampusEdital> vinculacoes = campusEditalRepository
                        .findAllByCampusId(id);
                if (vinculacoes != null && !vinculacoes.isEmpty()) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "success", false,
                            "error", "Não é possível excluir o campus pois existem " + vinculacoes.size()
                                    + " vínculos com editais (remova-os primeiro)."));
                }

                campusRepository.deleteById(id);
                return ResponseEntity.ok(Map.of("success", true));
            }
        } catch (Exception e) {
            logger.error("Erro ao excluir campus/turno {}", id, e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/alocar-vagas-lote")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> alocarVagasLote(@RequestBody List<Map<String, Object>> dadosVagas) {
        logger.info("Recebido {} turnos para alocação de vagas", dadosVagas.size());
        
        try {
            for (Map<String, Object> vaga : dadosVagas) {
                Long campusEditalTurnoId = ((Number) vaga.get("campusEditalTurnoId")).longValue();
                Integer quantidade = ((Number) vaga.get("quantidade")).intValue();
                
                // Buscar o turno
                CampusEditalTurno turno = campusEditalTurnoRepository.findById(campusEditalTurnoId)
                        .orElse(null);
                
                if (turno != null && quantidade > 0) {
                    logger.info("Atualizando vagas para turno ID {} com quantidade {}", campusEditalTurnoId, quantidade);
                    
                    int vagasReservadas = Math.max(1, (int) Math.ceil(quantidade * 0.2));
                    int vagasAmplaConcorrencia = Math.max(0, quantidade - vagasReservadas);

                    // Distribui o total informado entre reservadas (mulheres) e ampla concorrência
                    turno.setNumeroVagasReservadas(vagasReservadas);
                    turno.setNumeroVagasAmplaConcorrencia(vagasAmplaConcorrencia);
                    turno.setNumeroVagasCadastroReserva(0);
                    turno.setNumeroVagasClassificado(0);
                    // NÃO zera numeroVagasHabilitado - será calculado como o restante
                    turno.setNumeroVagasHabilitado(quantidade);
                    campusEditalTurnoRepository.save(turno);
                    
                    // Processar a alocação para este turno
                    alocacaoVagaService.processarAlocacaoVagasPorTurno(turno);
                }
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Todas as vagas foram alocadas com sucesso!");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Erro ao alocar vagas em lote", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @SuppressWarnings("unused")
    private static final class CampusGrupoView {
        private final Campus campus;
        private final List<TurnoView> turnos;

        private CampusGrupoView(Campus campus, List<TurnoView> turnos) {
            this.campus = campus;
            this.turnos = turnos;
        }

        public Campus getCampus() {
            return campus;
        }

        public List<TurnoView> getTurnos() {
            return turnos;
        }
    }

    @SuppressWarnings("unused")
    private static final class TurnoView {
        private final Long id;
        private final Long campusId;
        private final String campusNome;
        private final Long editalId;
        private final String editalDescricao;
        private final String turno;
        private final Integer numeroVagasReservadas;
        private final Integer numeroVagasAmplaConcorrencia;
        private final Integer vagasReservadasOcupadas;
        private final Integer vagasAmplaOcupadas;
        private final Integer vagasReservadasDisponiveis;
        private final Integer vagasAmplaDisponiveis;
        private final Long campusEditalTurnoId;
        private final Integer numeroCandidatosPendentes;
        private final Integer numeroCandidatosEliminados;
        private final Integer numeroCandidatosHabilitados;
        private final Integer numeroCandidatosInscritos;

        private TurnoView(Long id, Long campusId, String campusNome, Long editalId, String editalDescricao,
                String turno, Integer numeroVagasReservadas, Integer numeroVagasAmplaConcorrencia,
                Integer vagasReservadasOcupadas, Integer vagasAmplaOcupadas,
            Integer vagasReservadasDisponiveis, Integer vagasAmplaDisponiveis, Long campusEditalTurnoId,
            Integer numeroCandidatosPendentes, Integer numeroCandidatosEliminados,
            Integer numeroCandidatosHabilitados,
            Integer numeroCandidatosInscritos) {
            this.id = id;
            this.campusId = campusId;
            this.campusNome = campusNome;
            this.editalId = editalId;
            this.editalDescricao = editalDescricao;
            this.turno = turno;
            this.numeroVagasReservadas = numeroVagasReservadas;
            this.numeroVagasAmplaConcorrencia = numeroVagasAmplaConcorrencia;
            this.vagasReservadasOcupadas = vagasReservadasOcupadas;
            this.vagasAmplaOcupadas = vagasAmplaOcupadas;
            this.vagasReservadasDisponiveis = vagasReservadasDisponiveis;
            this.vagasAmplaDisponiveis = vagasAmplaDisponiveis;
            this.campusEditalTurnoId = campusEditalTurnoId;
            this.numeroCandidatosPendentes = numeroCandidatosPendentes;
            this.numeroCandidatosEliminados = numeroCandidatosEliminados;
            this.numeroCandidatosHabilitados = numeroCandidatosHabilitados;
            this.numeroCandidatosInscritos = numeroCandidatosInscritos;
        }

        private static TurnoView from(CampusEdital campusEdital, CampusEditalTurno turno,
                Integer pendentes, Integer eliminados, Integer habilitados, Integer inscritos) {
            return new TurnoView(
                    turno.getId(),
                    campusEdital.getCampus().getId(),
                    campusEdital.getCampus().getNome(),
                    campusEdital.getEdital() != null ? campusEdital.getEdital().getId() : null,
                    campusEdital.getEdital() != null && campusEdital.getEdital().getDescricao() != null
                            ? campusEdital.getEdital().getDescricao()
                            : "Sem Edital",
                    turno.getTurno(),
                    turno.getNumeroVagasReservadas(),
                    turno.getNumeroVagasAmplaConcorrencia(),
                    turno.getVagasReservadasOcupadas(),
                    turno.getVagasAmplaOcupadas(),
                    turno.getVagasReservadasDisponiveis(),
                    turno.getVagasAmplaDisponiveis(),
                    turno.getId(),
                    pendentes,
                    eliminados,
                    habilitados,
                    inscritos);
        }

        public Long getId() { return id; }
        public Long getCampusId() { return campusId; }
        public String getCampusNome() { return campusNome; }
        public Long getEditalId() { return editalId; }
        public String getEditalDescricao() { return editalDescricao; }
        public String getTurno() { return turno; }
        public Integer getNumeroVagasReservadas() { return numeroVagasReservadas; }
        public Integer getNumeroVagasAmplaConcorrencia() { return numeroVagasAmplaConcorrencia; }
        public Integer getVagasReservadasOcupadas() { return vagasReservadasOcupadas; }
        public Integer getVagasAmplaOcupadas() { return vagasAmplaOcupadas; }
        public Integer getVagasReservadasDisponiveis() { return vagasReservadasDisponiveis; }
        public Integer getVagasAmplaDisponiveis() { return vagasAmplaDisponiveis; }
        public Long getCampusEditalTurnoId() { return campusEditalTurnoId; }
        public Integer getNumeroCandidatosPendentes() { return numeroCandidatosPendentes; }
        public Integer getNumeroCandidatosEliminados() { return numeroCandidatosEliminados; }
        public Integer getNumeroCandidatosHabilitados() { return numeroCandidatosHabilitados; }
        public Integer getNumeroCandidatosInscritos() { return numeroCandidatosInscritos; }
        public Integer getTotalVagas() {
            return getNumeroVagasReservadas() + getNumeroVagasAmplaConcorrencia();
        }
    }

}
