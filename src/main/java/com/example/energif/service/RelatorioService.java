package com.example.energif.service;

import java.awt.Color;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.example.energif.model.Campus;
import com.example.energif.model.Candidato;
import com.example.energif.model.SituacaoCandidato;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;

@Service
public class RelatorioService {

    // Tons de cinza e definições padrão baseadas no SUAP
    private static final Color CINZA_CABECALHO = new Color(240, 240, 240);
    private static final Color CINZA_LINHA_BORDA = new Color(0, 0, 0); // Linhas pretas finas oficiais
    private static final java.util.Locale PT_BR = new java.util.Locale("pt", "BR");

    public void gerarRelatorioPDF(Document doc, List<Campus> campusList, 
                                    Map<Campus, List<Candidato>> grouped, String turno) throws DocumentException {
        
        Font titleFont = new Font(Font.HELVETICA, 12, Font.BOLD, Color.BLACK);
        Font headerFont = new Font(Font.HELVETICA, 9, Font.BOLD, Color.BLACK);
        Font subTitleFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.BLACK);
        Font normalFont = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.BLACK);

        PdfPTable titleTable = new PdfPTable(1);
        titleTable.setWidthPercentage(100);
        PdfPCell titleCell = new PdfPCell(new Phrase("Resultado Final", titleFont));
        titleCell.setBackgroundColor(CINZA_CABECALHO);
        titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        titleCell.setPadding(6);
        titleCell.setBorderColor(CINZA_LINHA_BORDA);
        titleCell.setBorderWidth(0.5f);
        titleTable.addCell(titleCell);
        doc.add(titleTable);

        DateTimeFormatter dateF = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter timeF = DateTimeFormatter.ofPattern("HH:mm:ss");

        campusList.sort((c1, c2) -> {
            String n1 = c1 != null ? c1.getNome() : "";
            String n2 = c2 != null ? c2.getNome() : "";
            return n1.compareTo(n2);
        });

        for (Campus campus : campusList) {
            List<Candidato> candidatos = grouped.get(campus);
            if (candidatos == null || candidatos.isEmpty()) {
                continue;
            }

            String campusName = campus != null ? campus.getNome() : "Sem Campus";
            
            Map<String, List<Candidato>> byTurno = candidatos.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    c -> normalizeTurno(c.getTurno()),
                    () -> new java.util.LinkedHashMap<String, List<Candidato>>(),
                    java.util.stream.Collectors.toList()));

                for (Map.Entry<String, List<Candidato>> turnoEntry : byTurno.entrySet().stream()
                    .sorted((e1, e2) -> compararTurnos(e1.getKey(), e2.getKey()))
                    .toList()) {
                String turnoName = turnoEntry.getKey();
                
                PdfPTable infoTable = new PdfPTable(1);
                infoTable.setWidthPercentage(100);
                String subTituloTexto = campusName + " - " + turnoName;
                PdfPCell infoCell = new PdfPCell(new Phrase(subTituloTexto, subTitleFont));
                infoCell.setBackgroundColor(CINZA_CABECALHO);
                infoCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                infoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                infoCell.setPadding(5);
                infoCell.setBorderColor(CINZA_LINHA_BORDA);
                infoCell.setBorderWidth(0.5f);
                infoTable.addCell(infoCell);
                doc.add(infoTable);

                PdfPTable table = new PdfPTable(new float[] { 2.2f, 4.5f, 1.3f, 4f });
                table.setWidthPercentage(100);

                String[] headerTexts = { "Data e Hora da Inscrição", "Nome Completo", "Classificação", "Situação" };
                for (String headerText : headerTexts) {
                    PdfPCell cell = new PdfPCell(new Phrase(headerText, headerFont));
                    cell.setBackgroundColor(CINZA_CABECALHO);
                    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    cell.setPadding(5);
                    cell.setBorderColor(CINZA_LINHA_BORDA);
                    cell.setBorderWidth(0.5f);
                    table.addCell(cell);
                }

                int rank = 0;
                for (Candidato c : turnoEntry.getValue()) {
                    String dateTime = "-";
                    if (c.getDataInscricao() != null) {
                        dateTime = c.getDataInscricao().format(dateF);
                        if (c.getHoraInscricao() != null)
                            dateTime += " " + c.getHoraInscricao().format(timeF);
                    }

                    PdfPCell cellData = new PdfPCell(new Phrase(dateTime, normalFont));
                    cellData.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cellData.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    cellData.setPadding(4);
                    cellData.setBorderColor(CINZA_LINHA_BORDA);
                    cellData.setBorderWidth(0.5f);
                    table.addCell(cellData);

                    String nomeUpper = c.getNome() != null ? c.getNome().toUpperCase(PT_BR) : "-";
                    PdfPCell cellNome = new PdfPCell(new Phrase(nomeUpper, normalFont));
                    cellNome.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cellNome.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    cellNome.setPadding(4);
                    cellNome.setBorderColor(CINZA_LINHA_BORDA);
                    cellNome.setBorderWidth(0.5f);
                    table.addCell(cellNome);

                    String classifText = "-";
                    if (c.getSituacao() == SituacaoCandidato.CLASSIFICADO || c.getSituacao() == SituacaoCandidato.HABILITADO) {
                        rank++;
                        classifText = rank + "º";
                    }
                    PdfPCell cellClassif = new PdfPCell(new Phrase(classifText, normalFont));
                    cellClassif.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cellClassif.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    cellClassif.setPadding(4);
                    cellClassif.setBorderColor(CINZA_LINHA_BORDA);
                    cellClassif.setBorderWidth(0.5f);
                    table.addCell(cellClassif);

                    String situ = c.getSituacao().getDescricao();
                    if (c.getMotivoNaoClassificacao() != null && !c.getMotivoNaoClassificacao().isBlank()) {
                        situ += " - " + c.getMotivoNaoClassificacao();
                    }
                    PdfPCell cellSitu = new PdfPCell(new Phrase(situ, normalFont));
                    cellSitu.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cellSitu.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    cellSitu.setPadding(4);
                    cellSitu.setBorderColor(CINZA_LINHA_BORDA);
                    cellSitu.setBorderWidth(0.5f);
                    table.addCell(cellSitu);
                }

                doc.add(table);
                doc.add(Chunk.NEWLINE);
            }

            doc.newPage();
        }
    }



    public void gerarRelatorioPreliminar(Document doc, List<Candidato> candidatos, String editalDescricao) throws DocumentException {
        Font titleFont = new Font(Font.HELVETICA, 12, Font.BOLD, Color.BLACK);
        Font headerFont = new Font(Font.HELVETICA, 9, Font.BOLD, Color.BLACK);
        Font subTitleFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.BLACK);
        Font normalFont = new Font(Font.HELVETICA, 8, Font.NORMAL, Color.BLACK);

        PdfPTable titleTable = new PdfPTable(1);
        titleTable.setWidthPercentage(100);
        PdfPCell titleCell = new PdfPCell(new Phrase("Resultado Preliminar", titleFont));
        titleCell.setBackgroundColor(CINZA_CABECALHO);
        titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        titleCell.setPadding(6);
        titleCell.setBorderColor(CINZA_LINHA_BORDA);
        titleCell.setBorderWidth(0.5f);
        titleTable.addCell(titleCell);
        doc.add(titleTable);

        DateTimeFormatter dateTimeF = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

        Map<Campus, List<Candidato>> grouped = candidatos.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                Candidato::getCampus,
                java.util.LinkedHashMap::new,
                java.util.stream.Collectors.toList()));

        java.util.List<Map.Entry<Campus, List<Candidato>>> sortedCampusList = grouped.entrySet().stream()
            .sorted((e1, e2) -> {
                String n1 = e1.getKey() != null ? e1.getKey().getNome() : "";
                String n2 = e2.getKey() != null ? e2.getKey().getNome() : "";
                return n1.compareTo(n2);
            })
            .collect(java.util.stream.Collectors.toList());

        for (Map.Entry<Campus, List<Candidato>> campusEntry : sortedCampusList) {
            Campus campus = campusEntry.getKey();
            List<Candidato> candidatosLista = campusEntry.getValue();

            String campusName = campus != null ? campus.getNome() : "Sem Campus";

            Map<String, List<Candidato>> byTurno = candidatosLista.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    c -> normalizeTurno(c.getTurno()),
                    () -> new java.util.LinkedHashMap<String, List<Candidato>>(),
                    java.util.stream.Collectors.toList()));

                for (Map.Entry<String, List<Candidato>> turnoEntry : byTurno.entrySet().stream()
                    .sorted((e1, e2) -> compararTurnos(e1.getKey(), e2.getKey()))
                    .toList()) {
                String turnoName = turnoEntry.getKey();
                List<Candidato> candidatosTurno = turnoEntry.getValue();

                java.text.Collator collator = java.text.Collator.getInstance(PT_BR);
                collator.setStrength(java.text.Collator.PRIMARY);

                candidatosTurno.sort((c1, c2) -> {
                    java.time.LocalDate d1 = c1.getDataInscricao();
                    java.time.LocalDate d2 = c2.getDataInscricao();

                    if (d1 != null && d2 != null) {
                        int byDate = d1.compareTo(d2);
                        if (byDate != 0) return byDate;
                    } else if (d1 != null) {
                        return -1;
                    } else if (d2 != null) {
                        return 1;
                    }

                    java.time.LocalTime h1 = c1.getHoraInscricao();
                    java.time.LocalTime h2 = c2.getHoraInscricao();
                    if (h1 != null && h2 != null) {
                        int byTime = h1.compareTo(h2);
                        if (byTime != 0) return byTime;
                    } else if (h1 != null) {
                        return -1;
                    } else if (h2 != null) {
                        return 1;
                    }

                    String n1 = c1.getNome() != null ? c1.getNome() : "";
                    String n2 = c2.getNome() != null ? c2.getNome() : "";
                    return collator.compare(n1.toUpperCase(PT_BR), n2.toUpperCase(PT_BR));
                });

                PdfPTable infoTable = new PdfPTable(1);
                infoTable.setWidthPercentage(100);
                String subTituloTexto = campusName + " - " + turnoName;
                PdfPCell infoCell = new PdfPCell(new Phrase(subTituloTexto, subTitleFont));
                infoCell.setBackgroundColor(CINZA_CABECALHO);
                infoCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                infoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                infoCell.setPadding(5);
                infoCell.setBorderColor(CINZA_LINHA_BORDA);
                infoCell.setBorderWidth(0.5f);
                infoTable.addCell(infoCell);
                doc.add(infoTable);

                PdfPTable table = new PdfPTable(new float[] { 2.2f, 4.5f, 1.3f, 4f });
                table.setWidthPercentage(100);

                String[] headerTexts = { "Data e Hora da Inscrição", "Nome Completo", "Classificação", "Situação" };
                for (String headerText : headerTexts) {
                    PdfPCell cell = new PdfPCell(new Phrase(headerText, headerFont));
                    cell.setBackgroundColor(CINZA_CABECALHO);
                    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    cell.setPadding(5);
                    cell.setBorderColor(CINZA_LINHA_BORDA);
                    cell.setBorderWidth(0.5f);
                    table.addCell(cell);
                }

                int rank = 0;
                for (Candidato c : candidatosTurno) {
                    String dateTime = "-";
                    if (c.getDataInscricao() != null && c.getHoraInscricao() != null) {
                        dateTime = java.time.LocalDateTime.of(c.getDataInscricao(), c.getHoraInscricao()).format(dateTimeF);
                    } else if (c.getDataInscricao() != null) {
                        dateTime = c.getDataInscricao().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                    }

                    PdfPCell cellData = new PdfPCell(new Phrase(dateTime, normalFont));
                    cellData.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cellData.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    cellData.setPadding(4);
                    cellData.setBorderColor(CINZA_LINHA_BORDA);
                    cellData.setBorderWidth(0.5f);
                    table.addCell(cellData);

                    String nomeUpper = c.getNome() != null ? c.getNome().toUpperCase(PT_BR) : "-";
                    PdfPCell cellNome = new PdfPCell(new Phrase(nomeUpper, normalFont));
                    cellNome.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cellNome.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    cellNome.setPadding(4);
                    cellNome.setBorderColor(CINZA_LINHA_BORDA);
                    cellNome.setBorderWidth(0.5f);
                    table.addCell(cellNome);

                    String classifText = "-";
                    if (c.getSituacao() == SituacaoCandidato.CLASSIFICADO || c.getSituacao() == SituacaoCandidato.HABILITADO) {
                        rank++;
                        classifText = rank + "º";
                    }
                    PdfPCell cellClassif = new PdfPCell(new Phrase(classifText, normalFont));
                    cellClassif.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cellClassif.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    cellClassif.setPadding(4);
                    cellClassif.setBorderColor(CINZA_LINHA_BORDA);
                    cellClassif.setBorderWidth(0.5f);
                    table.addCell(cellClassif);

                    String situ = c.getSituacao().getDescricao();
                    if (c.getMotivoNaoClassificacao() != null && !c.getMotivoNaoClassificacao().isBlank()) {
                        situ += " - " + c.getMotivoNaoClassificacao();
                    }
                    PdfPCell cellSitu = new PdfPCell(new Phrase(situ, normalFont));
                    cellSitu.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cellSitu.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    cellSitu.setPadding(4);
                    cellSitu.setBorderColor(CINZA_LINHA_BORDA);
                    cellSitu.setBorderWidth(0.5f);
                    table.addCell(cellSitu);
                }

                doc.add(table);
                doc.add(Chunk.NEWLINE);
            }

            doc.newPage();
        }
    }
    private String normalizeTurno(String turnoRaw) {
        if (turnoRaw == null || turnoRaw.isBlank()) {
            return "Turno não Informado";
        }

        String trimmed = turnoRaw.trim();
        String lower = trimmed.toLowerCase(PT_BR);

        if (lower.startsWith("turno da ")) {
            trimmed = trimmed.substring(9).trim();
        } else if (lower.startsWith("turno de ")) {
            trimmed = trimmed.substring(9).trim();
        } else if (lower.startsWith("turno ")) {
            trimmed = trimmed.substring(6).trim();
        }

        if (trimmed.isBlank()) {
            return "Turno não Informado";
        }

        // Garante a primeira letra maiúscula (Ex: "Turno da Noite")
        String primeiraLetra = trimmed.substring(0, 1).toUpperCase(PT_BR);
        String resto = trimmed.substring(1).toLowerCase(PT_BR);
        
        return "Turno da " + primeiraLetra + resto;
    }

    private int compararTurnos(String turno1, String turno2) {
        return Integer.compare(getOrdemTurno(turno1), getOrdemTurno(turno2));
    }

    private int getOrdemTurno(String turno) {
        if (turno == null) {
            return 999;
        }

        String turnoNormalizado = turno.toLowerCase(PT_BR);
        if (turnoNormalizado.contains("manhã") || turnoNormalizado.contains("manha")) {
            return 1;
        }
        if (turnoNormalizado.contains("tarde")) {
            return 2;
        }
        if (turnoNormalizado.contains("noite")) {
            return 3;
        }
        return 999;
    }
}
