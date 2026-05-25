package com.example.energif.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.energif.model.Campus;
import com.example.energif.model.CampusEdital;
import com.example.energif.model.CampusEditalTurno;
import com.example.energif.model.Candidato;
import com.example.energif.model.SituacaoCandidato;
import com.example.energif.repository.CampusEditalRepository;
import com.example.energif.repository.CampusEditalTurnoRepository;
import com.example.energif.repository.CampusRepository;
import com.example.energif.repository.CandidatoRepository;

/**
 * Utility that reads an .xlsx file and imports rows into the Candidato table.
 * It looks for columns (by header text approximate match):
 * - Carimbo de data/hora
 * - Campus (cidade) e turno
 * - Nome Completo
 * - Gênero
 * - Data de Nascimento
 * - CPF
 */
@Component
public class Filtro {

    private static final Logger log = LoggerFactory.getLogger(Filtro.class);

    private final CandidatoRepository candidatoRepository;
    private final CampusRepository campusRepository;
    private final com.example.energif.repository.EditalRepository editalRepository;
    private final CampusEditalRepository campusEditalRepository;
    private final CampusEditalTurnoRepository turnoRepository;

    public Filtro(CandidatoRepository candidatoRepository, CampusRepository campusRepository,
            com.example.energif.repository.EditalRepository editalRepository,
            CampusEditalRepository campusEditalRepository, CampusEditalTurnoRepository turnoRepository) {
        this.candidatoRepository = candidatoRepository;
        this.campusRepository = campusRepository;
        this.editalRepository = editalRepository;
        this.campusEditalRepository = campusEditalRepository;
        this.turnoRepository = turnoRepository;
    }

    public int importarXlsx(File xlsxFile) throws Exception {
        return importarXlsx(xlsxFile, null);
    }
    public int importarXlsx(File xlsxFile, String editalDescricao) throws Exception {
        com.example.energif.model.Edital targetEdital = null;
        if (editalDescricao != null && !editalDescricao.isBlank()) {
            // find-or-create by descricao
            var list = editalRepository.findAll();
            for (var ed : list) {
                if (editalDescricao.equals(ed.getDescricao())) {
                    targetEdital = ed;
                    break;
                }
            }
            if (targetEdital == null) {
                targetEdital = new com.example.energif.model.Edital();
                targetEdital.setDescricao(editalDescricao);
                targetEdital = editalRepository.save(targetEdital);
            }
        }

        try (InputStream is = new FileInputStream(xlsxFile);
            Workbook workbook = new XSSFWorkbook(is)) {

            int imported = 0;
            int totalSheets = workbook.getNumberOfSheets();

            // ====================================================================
            // ABRE O LOOP PARA PERCORRER TODAS AS ABAS DO ARQUIVO
            // ====================================================================
            for (int i = 0; i < totalSheets; i++) {
                Sheet sheet = workbook.getSheetAt(i);
                
                // Opcional: Se quiser pular a aba geral de respostas brutas que está vazia
                if (sheet.getSheetName().equalsIgnoreCase("Respostas ao formulário 1") && totalSheets > 1) {
                    log.info("Ignorando a aba de respostas brutas: {}", sheet.getSheetName());
                    continue; 
                }

                log.info("Processando a Aba [{} / {}]: {}", (i + 1), totalSheets, sheet.getSheetName());

                Iterator<Row> rowIt = sheet.iterator();
                if (!rowIt.hasNext()) {
                    continue; // Se esta aba específica estiver vazia, pula para a próxima aba
                }

                // Lê o cabeçalho desta aba específica
                Row header = rowIt.next();
                Map<String, Integer> colIndex = mapHeaderIndices(header);

                // Loop para ler as linhas DA ABA ATUAL
                while (rowIt.hasNext()) {
                    Row r = rowIt.next();
                    try {
                        // Passa a linha e os índices mapeados desta aba
                        Candidato c = mapRowToCandidato(r, colIndex);
                        if (c != null) {
                            if (targetEdital != null)
                                c.setEdital(targetEdital);
                            
                            candidatoRepository.save(c);

                            if (c.getCampus() != null && c.getEdital() != null) {
                                if (c.getTurno() != null && !c.getTurno().isBlank()) {
                                    criarTurnoSeNaoExistir(c.getCampus(), c.getEdital(), c.getTurno());
                                }
                            }

                            imported++;
                        }
                    } catch (Exception ex) {
                        log.warn("Falha ao importar linha {} na aba {}: {}", r.getRowNum(), sheet.getSheetName(), ex.getMessage());
                    }
                } // Fim do while (linhas da aba)
                
            } // Fim do for (abas do arquivo)

            log.info("Import finished - {} records imported from {}", imported, xlsxFile.getName());
            return imported;
        }
    }
    private Map<String, Integer> mapHeaderIndices(Row header) {
        Map<String, Integer> map = new HashMap<>();

        System.out.println("=== DEBUG HEADER COLUMNS ===");
        for (Cell cell : header) {
            String txt = safeString(cell);
            System.out.println("Coluna " + cell.getColumnIndex() + ": '" + txt + "'");
            String normalizedTxt = normalizeHeaderText(txt);

            // BUSCA EXATA pelas colunas específicas
            switch (normalizedTxt) {
                case "carimbo de data/hora":
                case "timestamp":
                    map.put("timestamp", cell.getColumnIndex());
                    System.out.println(">>> CARIMBO DE DATA/HORA EXATO encontrado na coluna: " + cell.getColumnIndex());
                    break;
                case "campus (cidade) e turno:":
                case "campus (cidade) e turno":
                    map.put("campus_turno", cell.getColumnIndex());
                    System.out.println(">>> CAMPUS E TURNO EXATO encontrado na coluna: " + cell.getColumnIndex());
                    break;
                case "nome completo":
                    map.put("nome", cell.getColumnIndex());
                    System.out.println(">>> NOME COMPLETO EXATO encontrado na coluna: " + cell.getColumnIndex());
                    break;
                case "genero":
                case "gênero":
                    map.put("genero", cell.getColumnIndex());
                    System.out.println(">>> GÊNERO EXATO encontrado na coluna: " + cell.getColumnIndex());
                    break;
                case "data de nascimento":
                    map.put("dataNascimento", cell.getColumnIndex());
                    System.out.println(">>> DATA DE NASCIMENTO EXATA encontrada na coluna: " + cell.getColumnIndex());
                    break;
                case "cpf":
                    map.put("cpf", cell.getColumnIndex());
                    System.out.println(">>> CPF EXATO encontrado na coluna: " + cell.getColumnIndex());
                    break;
                case "situacao":
                    map.put("situacao", cell.getColumnIndex());
                    System.out.println(">>> SITUAÇÃO EXATA encontrada na coluna: " + cell.getColumnIndex());
                    break;
                case "motivo":
                    map.put("motivo", cell.getColumnIndex());
                    System.out.println(">>> MOTIVO EXATO encontrado na coluna: " + cell.getColumnIndex());
                    break;
                default:
                    break;
            }
            // REMOVIDO: busca por "tipo_vaga" pois agora é determinado pelo gênero
        }

        // Busca aproximada (fallback) para colunas que não foram encontradas exatamente
        System.out.println("=== BUSCA APROXIMADA (FALLBACK) ===");
        for (Cell cell : header) {
            String txt = safeString(cell).toLowerCase();
            String normalizedTxt = normalizeHeaderText(txt);
            
            // Fallback para Campus e Turno separados
            if (!map.containsKey("campus") && (txt.contains("campus") || txt.contains("cidade"))) {
                map.put("campus", cell.getColumnIndex());
                System.out.println(">>> CAMPUS (aproximado) encontrado na coluna: " + cell.getColumnIndex());
            }
            if (!map.containsKey("turno") && txt.contains("turno")) {
                map.put("turno", cell.getColumnIndex());
                System.out.println(">>> TURNO (aproximado) encontrado na coluna: " + cell.getColumnIndex());
            }
            
            // Fallback para Timestamp
            if (!map.containsKey("timestamp") && (txt.contains("timestamp") || txt.contains("data/hora") || txt.contains("carimbo"))) {
                map.put("timestamp", cell.getColumnIndex());
                System.out.println(">>> TIMESTAMP (aproximado) encontrado na coluna: " + cell.getColumnIndex());
            }
            
            // Fallback para Nome
            if (!map.containsKey("nome") && (txt.contains("nome") || txt.contains("candidat"))) {
                map.put("nome", cell.getColumnIndex());
                System.out.println(">>> NOME (aproximado) encontrado na coluna: " + cell.getColumnIndex());
            }
            
            // Fallback para Gênero
            if (!map.containsKey("genero") && (txt.contains("genero") || txt.contains("gênero") || txt.contains("sexo"))) {
                map.put("genero", cell.getColumnIndex());
                System.out.println(">>> GÊNERO (aproximado) encontrado na coluna: " + cell.getColumnIndex());
            }
            
            // Fallback para Data de Nascimento
            if (!map.containsKey("dataNascimento") && (txt.contains("nascimento") || txt.contains("nasc") || txt.contains("data_nasc"))) {
                map.put("dataNascimento", cell.getColumnIndex());
                System.out.println(">>> DATA DE NASCIMENTO (aproximado) encontrada na coluna: " + cell.getColumnIndex());
            }
            
            // Fallback para CPF
            if (!map.containsKey("cpf") && txt.contains("cpf")) {
                map.put("cpf", cell.getColumnIndex());
                System.out.println(">>> CPF (aproximado) encontrado na coluna: " + cell.getColumnIndex());
            }

            if (!map.containsKey("situacao") && (normalizedTxt.contains("situacao"))) {
                map.put("situacao", cell.getColumnIndex());
                System.out.println(">>> SITUAÇÃO (aproximado) encontrada na coluna: " + cell.getColumnIndex());
            }

            if (!map.containsKey("motivo") && (normalizedTxt.contains("motivo"))) {
                map.put("motivo", cell.getColumnIndex());
                System.out.println(">>> MOTIVO (aproximado) encontrado na coluna: " + cell.getColumnIndex());
            }
        }

        // Verificação se todas as colunas obrigatórias foram encontradas
        System.out.println("=== VERIFICAÇÃO DE COLUNAS OBRIGATÓRIAS ===");
        String[] obrigatorias = { "timestamp", "nome", "genero", "dataNascimento", "cpf", "situacao", "motivo" };
        for (String col : obrigatorias) {
            if (!map.containsKey(col)) {
                System.out.println("!!! ERRO: Coluna obrigatória não encontrada: " + col);
            } else {
                System.out.println("✓ Coluna " + col + " encontrada na posição: " + map.get(col));
            }
        }

        // Campus é obrigatório (seja como campus_turno ou separado)
        if (!map.containsKey("campus_turno") && !map.containsKey("campus")) {
            System.out.println("!!! ERRO: Nenhuma coluna de CAMPUS encontrada (campus_turno ou campus)");
        } else {
            System.out.println("✓ Campus encontrado (campus_turno ou campus)");
        }

        System.out.println("Map final: " + map);
        System.out.println("=============================");

        return map;
    }

    /**
     * Preserva o turno exatamente como está no arquivo.
     */
    private String normalizeTurno(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        log.debug("Turno preservado do arquivo: '{}'", s);
        return s;
    }

    private String normalizeHeaderText(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{M}+", ""); 
        normalized = normalized.replaceAll("[\\p{Z}\\s]+", " "); 
        normalized = normalized.replaceAll("\\p{C}+", ""); 
        return normalized.toLowerCase().trim();
    }

    /**
     * Converte uma string (lida do arquivo Excel) para o enum SituacaoCandidato.
     * Tenta encontrar a correspondência exata ou aproximada.
     * Se não conseguir converter, retorna PENDENTE como padrão.
     */
    private String normalizeStatusText(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{M}+", "");
        normalized = normalized.replaceAll("[\\p{Z}\\s]+", " ");
        normalized = normalized.replaceAll("\\p{C}+", "");
        return normalized.trim().toUpperCase();
    }

    private SituacaoCandidato converterStringSituacao(String str) {
        if (str == null || str.isBlank()) {
            log.debug("Situação vazia - usando padrão PENDENTE");
            return SituacaoCandidato.PENDENTE;
        }

        String normalized = normalizeStatusText(str);
        if ("CLASSIFICADO".equals(normalized)) {
            return SituacaoCandidato.CLASSIFICADO;
        }
        if ("NAO CLASSIFICADO".equals(normalized)) {
            return SituacaoCandidato.ELIMINADO;
        }

        log.warn("Situação '{}' (normalizado='{}') não é um valor esperado; usando padrão PENDENTE", str, normalized);
        return SituacaoCandidato.PENDENTE;
    }

    private SituacaoCandidato determinarSituacaoImportacao(String situacaoStr) {
        if (situacaoStr == null || situacaoStr.isBlank()) {
            return SituacaoCandidato.PENDENTE;
        }

        return converterStringSituacao(situacaoStr);
    }

    private Candidato mapRowToCandidato(Row r, Map<String, Integer> cols) {
        // read fields
        String nome = getCellString(r, cols.get("nome"));
        if (nome == null || nome.isBlank())
            return null; // skip empty rows

        String rawCpf = getCellString(r, cols.get("cpf"));
        String cpf = rawCpf;
        if (cpf != null) {
            cpf = cpf.replaceAll("\\D+", "");
        }

        if (rawCpf != null && !rawCpf.isBlank()) {
            log.debug("Row {}: raw CPF='{}' -> sanitized='{}'", r.getRowNum(), rawCpf, cpf);
        }

        String generoStr = getCellString(r, cols.get("genero"));
        Character genero = null;
        if (generoStr != null && !generoStr.isBlank()) {
            // Normaliza o gênero para M ou F
            if (generoStr.toUpperCase().startsWith("M") || generoStr.equalsIgnoreCase("Masculino")) {
                genero = 'M';
            } else if (generoStr.toUpperCase().startsWith("F") || generoStr.equalsIgnoreCase("Feminino")) {
                genero = 'F';
            }
        }

        LocalDate dataNasc = getCellLocalDate(r, cols.get("dataNascimento"));

        // timestamp -> dataInscricao + horaInscricao
        LocalDate dataInscricao = null;
        LocalTime horaInscricao = null;
        if (cols.containsKey("timestamp")) {
            Cell tsCell = r.getCell(cols.get("timestamp"));
            if (tsCell != null && tsCell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(tsCell)) {
                Instant instant = tsCell.getDateCellValue().toInstant();
                LocalDateTime ldt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
                dataInscricao = ldt.toLocalDate();
                horaInscricao = ldt.toLocalTime();
            } else {
                String s = getCellString(r, cols.get("timestamp"));
                if (s != null && !s.isBlank()) {
                    try {
                        LocalDateTime ldt = LocalDateTime.parse(s);
                        dataInscricao = ldt.toLocalDate();
                        horaInscricao = ldt.toLocalTime();
                    } catch (Exception ignore) {
                    }
                }
            }
        }

        // campus and turno may be in the same column
        String campusName = null;
        String turno = null;
        
        if (cols.containsKey("campus_turno")) {
            String v = getCellString(r, cols.get("campus_turno"));
            log.debug("Row {}: Valor bruto campus_turno: '{}'", r.getRowNum(), v);
            if (v != null) {
                // Tenta divisão por separador único (-, |, /, ,) com limite de 2 partes
                String[] parts = v.split("[-|,|/]", 2);
                log.debug("Row {}: Split por separador [-|,|/]: {} partes -> ['{}', '{}']", 
                    r.getRowNum(), parts.length, 
                    parts.length > 0 ? parts[0] : "", 
                    parts.length > 1 ? parts[1] : "");
                
                if (parts.length == 2) {
                    campusName = parts[0].trim();
                    turno = normalizeTurno(parts[1].trim());
                    log.debug("Row {}: Campus='{}', Turno após normalização='{}'", r.getRowNum(), campusName, turno);
                } else {
                    // Se não achou separador, tenta split por espaço duplo
                    String[] ws = v.split("\\s{2,}|\\r?\\n");
                    log.debug("Row {}: Split por espaço duplo: {} partes", r.getRowNum(), ws.length);
                    if (ws.length >= 2) {
                        campusName = ws[0].trim();
                        turno = normalizeTurno(ws[1].trim());
                        log.debug("Row {}: Campus='{}', Turno após normalização='{}'", r.getRowNum(), campusName, turno);
                    } else {
                        campusName = v.trim();
                        log.debug("Row {}: Nenhuma divisão encontrada, campusName='{}'", r.getRowNum(), campusName);
                    }
                }
            }
        } else {
            // Campus e turno em colunas separadas (fallback)
            campusName = getCellString(r, cols.get("campus"));
            turno = getCellString(r, cols.get("turno"));
            if (turno != null) {
                turno = normalizeTurno(turno);
            }
            log.debug("Row {}: Campus e turno em colunas separadas - Campus='{}', Turno bruto='{}'", 
                    r.getRowNum(), campusName, turno);
        }

        // NOTA: TipoVaga será determinado automaticamente pela AlocacaoVagaService
        // durante o processamento de alocação de vagas, com base na ordem de inscrição,
        // situação (classificado/habilitado) e gênero do candidato.
        // Durante o import, deixamos tipoVaga como null.

        // resolve or create campus
        Campus campus = null;
        if (campusName != null && !campusName.isBlank()) {
            campus = campusRepository.findByNome(campusName);
            if (campus == null) {
                campus = new Campus();
                campus.setNome(campusName);
                // Define valores padrão para vagas: zero para forçar configuração manual
                campus.setNumeroVagasReservadas(0);
                campus.setNumeroVagasAmplaConcorrencia(0);
                campus.setVagasReservadasOcupadas(0);
                campus.setVagasAmplaOcupadas(0);
                campus = campusRepository.save(campus);
                log.info("Novo campus criado: {} com vagas padrão", campusName);
            }
        }

        Candidato c = new Candidato();
        c.setNome(nome);
        c.setCpf(cpf);
        c.setGenero(genero);
        c.setDataNascimento(dataNasc);
        c.setCampus(campus);
        c.setTurno(turno);
        c.setDataInscricao(dataInscricao);
        c.setHoraInscricao(horaInscricao);
        
        // Converter string de situação para enum SituacaoCandidato
        String situacaoStr = getCellString(r, cols.get("situacao"));
        String situacaoNormalized = normalizeStatusText(situacaoStr);
        log.debug("Row {}: situacao raw='{}', normalized='{}', col={}", r.getRowNum(), situacaoStr, situacaoNormalized, cols.get("situacao"));
        SituacaoCandidato situacao = determinarSituacaoImportacao(situacaoStr);
        c.setSituacao(situacao);

        // Ler motivo apenas quando a situação for NÃO CLASSIFICADO
        if (situacao == SituacaoCandidato.ELIMINADO) {
            String motivo = getCellString(r, cols.get("motivo"));
            if (motivo != null && !motivo.isBlank()) {
                c.setMotivoNaoClassificacao(motivo);
            }
        }

        // TipoVaga será definido pela AlocacaoVagaService durante alocação de vagas

        log.debug("Candidato mapeado: {} - Gênero: {} - Campus: {}",
                nome, genero, campusName);

        return c;
    }

    // helpers
    private String safeString(Cell cell) {
        if (cell == null)
            return "";
        
        // LOG DE SUPORTE: remova ou comente após descobrir o problema
        if (log.isDebugEnabled() && cell.getColumnIndex() == 43) {
            Row rowAtual = cell.getRow();
            // Pega o valor da coluna anterior (42) e da próxima (44) para ver se o dado mudou de lugar
            Cell colAnterior = rowAtual.getCell(cell.getColumnIndex() - 1);
            Cell colProxima = rowAtual.getCell(cell.getColumnIndex() + 1);

            log.debug("Mapeamento na Aba: {} | Candidato: {} | Col 42 (AQ): '{}' | Col 43 (AR - Atual): '{}' | Col 44 (AS): '{}'", 
                    cell.getSheet().getSheetName(),
                    rowAtual.getCell(4) != null ? rowAtual.getCell(4).toString() : "Nome ñ encontrado", // Ajuste o 4 para o índice do Nome se necessário
                    colAnterior,
                    cell, // O que ele leu como BLANK
                    colProxima);
        }
        try {
            DataFormatter fmt = new DataFormatter();
            return fmt.formatCellValue(cell);
        } catch (Exception e) {
            return cell.toString();
        }
    }



    private String getCellString(Row r, Integer idx) {
        if (idx == null)
            return null;
        
        // Altere esta linha para usar a política de retorno de célula vazia/branca
        Cell c = r.getCell(idx, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        
        if (c == null)
            return null;
            
        String s = safeString(c);
        return s != null ? s.trim() : null;
    }

    private LocalDate getCellLocalDate(Row r, Integer idx) {
        if (idx == null)
            return null;
        Cell c = r.getCell(idx);
        if (c == null)
            return null;

        try {
            // 1. Primeiro tenta como data numérica do Excel
            if (c.getCellType() == CellType.NUMERIC) {
                if (DateUtil.isCellDateFormatted(c)) {
                    java.util.Date date = c.getDateCellValue();
                    Instant instant = date.toInstant();
                    LocalDate result = LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).toLocalDate();
                    log.debug("Data numérica formatada (Excel): {} -> {}", date, result);
                    return result;
                } else {
                    // Número serial do Excel (dias desde 1900-01-01)
                    double numericValue = c.getNumericCellValue();
                    java.util.Date date = DateUtil.getJavaDate(numericValue);
                    LocalDate result = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                    log.debug("Data numérica serial (Excel): {} -> {}", numericValue, result);
                    return result;
                }
            }

            // 2. Tenta como string - SEM TRANSFORMAÇÕES
            String s = safeString(c);
            if (s == null || s.isBlank())
                return null;

            s = s.trim();
            log.debug("Tentando parsear data string: '{}'", s);

            // 3. Tenta formato ISO (yyyy-MM-dd) - ORIGINAL DO ARQUIVO
            try {
                LocalDate result = LocalDate.parse(s);
                log.debug("Data parseada com sucesso (ISO): {} -> {}", s, result);
                return result;
            } catch (Exception e1) {
                log.debug("Não é formato ISO: {}", s);
            }

            // 4. Tenta formatos brasileiros (dd/MM/yyyy, dd.MM.yyyy, dd-MM-yyyy)
            // SEM CORRIGIR ANOS COM 2 DÍGITOS - mantém como está no arquivo
            String[] separators = { "/", "\\.", "-" };

            for (String sep : separators) {
                try {
                    String[] parts = s.split(sep);
                    if (parts.length == 3) {
                        int dia = Integer.parseInt(parts[0].trim());
                        int mes = Integer.parseInt(parts[1].trim());
                        int ano = Integer.parseInt(parts[2].trim());

                        // Valida dia e mês
                        if (dia >= 1 && dia <= 31 && mes >= 1 && mes <= 12) {
                            LocalDate result = LocalDate.of(ano, mes, dia);
                            log.debug("Data parseada (DD/MM/YYYY): {} -> {}", s, result);
                            return result;
                        }
                    }
                } catch (Exception e) {
                    log.debug("Falha ao parsear com separador '{}': {}", sep, e.getMessage());
                }
            }

            // 5. Tenta formato americano (MM/dd/yyyy) - SEM CORRIGIR ANOS
            try {
                String[] parts = s.split("[/-]");
                if (parts.length == 3) {
                    int mes = Integer.parseInt(parts[0].trim());
                    int dia = Integer.parseInt(parts[1].trim());
                    int ano = Integer.parseInt(parts[2].trim());

                    if (mes >= 1 && mes <= 12 && dia >= 1 && dia <= 31) {
                        LocalDate result = LocalDate.of(ano, mes, dia);
                        log.debug("Data parseada (MM/DD/YYYY): {} -> {}", s, result);
                        return result;
                    }
                }
            } catch (Exception e) {
                log.debug("Falha ao parsear formato americano: {}", e.getMessage());
            }

            // Data não conseguiu ser parseada - retorna null
            log.warn("Linha {}: Data de nascimento INVÁLIDA não pôde ser parseada: '{}'. " +
                     "Candidato será importado com data_nascimento = null. " +
                     "Por favor, verifique e delete este candidato se necessário.", 
                     r.getRowNum(), s);
            return null;

        } catch (Exception ex) {
            log.error("Erro ao processar data na linha {}: {}", r.getRowNum(), ex.getMessage());
            return null;
        }
    }

    /**
     * Cria automaticamente um registro de turno (CampusEditalTurno) se não existir.
     * Isso garante que quando um candidato é importado, a turno correspondente é
     * registrada.
     */
    /**
     * Guarantee that a CampusEdital exists and create the given turno if missing.
     * Also provide a helper to create the three default turnos (Manhã/Tarde/Noite)
     * when a CampusEdital is present.
     */
    private void criarTurnoSeNaoExistir(Campus campus, com.example.energif.model.Edital edital, String turno) {
        try {
            // Buscar CampusEdital
            CampusEdital ce = campusEditalRepository.findByCampusAndEdital(campus, edital);
            if (ce == null) {
                // Criar CampusEdital automaticamente se não existir
                log.info("CampusEdital não encontrado para campus={} edital={}. Criando automaticamente...", 
                    campus.getId(), edital.getId());
                ce = new CampusEdital();
                ce.setCampus(campus);
                ce.setEdital(edital);
                ce = campusEditalRepository.save(ce);
                log.info("CampusEdital criado com sucesso: id={}", ce.getId());
            }

            // Verificar se turno já existe
            CampusEditalTurno existing = turnoRepository.findByCampusEditalAndTurno(ce, turno);
            if (existing != null) {
                log.debug("Turno '{}' já existe para CampusEdital {}", turno, ce.getId());
                return;
            }

            // Criar nova turno com vagas padrão (0) - será configurado manualmente pelo
            // admin
            CampusEditalTurno newTurno = new CampusEditalTurno();
            newTurno.setCampusEdital(ce);
            newTurno.setTurno(turno);
            newTurno.setNumeroVagasReservadas(0);
            newTurno.setNumeroVagasAmplaConcorrencia(0);
            newTurno.setVagasReservadasOcupadas(0);
            newTurno.setVagasAmplaOcupadas(0);
            turnoRepository.save(newTurno);

            log.info("Nova turno criada automaticamente: '{}' para CampusEdital {}", turno, ce.getId());
        } catch (Exception e) {
            log.warn("Erro ao criar turno automaticamente: {}", e.getMessage());
        }
    }

    /**
     * Ensure a CampusEdital exists for campus+edital and create the three default
     * turnos (Manhã, Tarde, Noite) if they don't exist yet. This is used when a
     * campus is created automatically during import and we want to pre-provision
     * the three shifts so imports and later classifications operate per-turno.
     */
    private void criarTurnosPadraoSeNaoExistir(Campus campus, com.example.energif.model.Edital edital) {
        try {
            CampusEdital ce = campusEditalRepository.findByCampusAndEdital(campus, edital);
            if (ce == null) {
                // create the linking record if missing
                ce = new CampusEdital();
                ce.setCampus(campus);
                ce.setEdital(edital);
                // default vagas = 0 – administrative configuration required later
                ce.setNumeroVagasReservadas(0);
                ce.setNumeroVagasAmplaConcorrencia(0);
                campusEditalRepository.save(ce);
                log.info("CampusEdital criado automaticamente para campus={} edital={}", campus.getNome(),
                        edital.getDescricao());
            }

            String[] padrao = new String[] { "Manhã", "Tarde", "Noite" };
            for (String t : padrao) {
                CampusEditalTurno existing = turnoRepository.findByCampusEditalAndTurno(ce, t);
                if (existing == null) {
                    CampusEditalTurno newTurno = new CampusEditalTurno();
                    newTurno.setCampusEdital(ce);
                    newTurno.setTurno(t);
                    newTurno.setNumeroVagasReservadas(0);
                    newTurno.setNumeroVagasAmplaConcorrencia(0);
                    newTurno.setVagasReservadasOcupadas(0);
                    newTurno.setVagasAmplaOcupadas(0);
                    turnoRepository.save(newTurno);
                    log.info("Turno padrão criado: '{}' para CampusEdital {}", t, ce.getId());
                }
            }
        } catch (Exception e) {
            log.warn("Erro ao criar turnos padrão para campus {} e edital {}: {}", campus.getNome(),
                    edital.getDescricao(), e.getMessage());
        }
    }

}