package com.example.energif.web;

import java.util.HashMap;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.energif.model.Campus;
import com.example.energif.model.CampusEditalTurno;
import com.example.energif.model.Vaga;
import com.example.energif.repository.CampusRepository;
import com.example.energif.repository.CampusEditalTurnoRepository;
import com.example.energif.repository.VagaRepository;
import com.example.energif.service.AlocacaoVagaService;

@Controller
@RequestMapping("/campus")
public class CampusController {

    private static final Logger logger = LoggerFactory.getLogger(CampusController.class);

    private final CampusRepository campusRepository;
    private final com.example.energif.repository.CampusEditalRepository campusEditalRepository;
    private final CampusEditalTurnoRepository campusEditalTurnoRepository;
    private final VagaRepository vagaRepository;
    private final AlocacaoVagaService alocacaoVagaService;

    public CampusController(CampusRepository campusRepository,
            com.example.energif.repository.CampusEditalRepository campusEditalRepository,
            CampusEditalTurnoRepository campusEditalTurnoRepository,
            VagaRepository vagaRepository,
            AlocacaoVagaService alocacaoVagaService) {
        this.campusRepository = campusRepository;
        this.campusEditalRepository = campusEditalRepository;
        this.campusEditalTurnoRepository = campusEditalTurnoRepository;
        this.vagaRepository = vagaRepository;
        this.alocacaoVagaService = alocacaoVagaService;
    }

    @GetMapping("/novo")
    public String novoForm(Model model) {
        model.addAttribute("campus", new Campus());
        model.addAttribute("campuses", campusRepository.findAll(Sort.by("nome")));
        return "cadastro-campus";
    }

    // No CampusController - método listarCampus:
    // Agora busca CampusEditalTurno para exibir linhas separadas por turno
    @GetMapping("/list")
    public String listarCampus(Model model) {
        var turnos = campusEditalRepository.findAll()
                .stream()
                .flatMap(ce -> java.util.Optional.ofNullable(ce.getTurnos()).orElse(java.util.Collections.emptyList())
                        .stream()
                        .map(t -> Map.ofEntries(
                                Map.entry("id", t.getId()),
                                Map.entry("campusId", ce.getCampus().getId()),
                                Map.entry("campusNome", ce.getCampus().getNome()),
                                Map.entry("editalId", ce.getEdital() != null ? ce.getEdital().getId() : null),
                                Map.entry("editalDescricao",
                                        ce.getEdital() != null && ce.getEdital().getDescricao() != null
                                                ? ce.getEdital().getDescricao()
                                                : "Sem Edital"),
                                Map.entry("turno", t.getTurno()),
                                Map.entry("numeroVagasReservadas", t.getNumeroVagasReservadas()),
                                Map.entry("numeroVagasAmplaConcorrencia", t.getNumeroVagasAmplaConcorrencia()),
                                Map.entry("numeroVagasClassificado", t.getNumeroVagasClassificado()),
                                Map.entry("numeroVagasHabilitado", t.getNumeroVagasHabilitado()),
                                Map.entry("numeroVagasCadastroReserva", t.getNumeroVagasCadastroReserva()),
                                Map.entry("vagasReservadasOcupadas", t.getVagasReservadasOcupadas()),
                                Map.entry("vagasAmplaOcupadas", t.getVagasAmplaOcupadas()),
                                Map.entry("vagasClassificadoOcupadas", t.getVagasClassificadoOcupadas()),
                                Map.entry("vagasHabilitadoOcupadas", t.getVagasHabilitadoOcupadas()),
                                Map.entry("vagasReservadasDisponiveis", t.getVagasReservadasDisponiveis()),
                                Map.entry("vagasAmplaDisponiveis", t.getVagasAmplaDisponiveis()),
                                Map.entry("vagasClassificadoDisponiveis", t.getVagasClassificadoDisponiveis()),
                                Map.entry("vagasHabilitadoDisponiveis", t.getVagasHabilitadoDisponiveis()),
                                Map.entry("campusEditalTurnoId", t.getId()))))
                .sorted((a, b) -> {
                    int cmpCampus = Objects.toString(a.get("campusNome"), "")
                            .compareTo(Objects.toString(b.get("campusNome"), ""));
                    if (cmpCampus != 0)
                        return cmpCampus;
                    int cmpEdital = Objects.toString(a.get("editalDescricao"), "")
                            .compareTo(Objects.toString(b.get("editalDescricao"), ""));
                    if (cmpEdital != 0)
                        return cmpEdital;
                    return Objects.toString(a.get("turno"), "").compareTo(Objects.toString(b.get("turno"), ""));
                })
                .toList();

        model.addAttribute("turnos", turnos);
        model.addAttribute("campuses", campusRepository.findAll(Sort.by("nome")));
        return "lista-campus";
    }

    @PostMapping
    public String criar(@ModelAttribute Campus campus) {
        logger.info("Criando campus: {}", campus.getNome());
        // avoid duplicates: if a campus with same nome exists, update numbers instead
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

    // CORREÇÃO: Usar @PathVariable em vez de @RequestParam
    // No CampusController - adicione estes métodos:
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

    @PostMapping("/alocar-todas")
    public String alocarTodasVagas() {
        alocacaoVagaService.alocarTodosCampus();
        return "redirect:/campus"; // Altere para a rota correta da sua página
    }

}
