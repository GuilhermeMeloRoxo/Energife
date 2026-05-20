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

    private static final Color CINZA_CLARO = new Color(220, 220, 220);
    private static final Color CINZA_ESCURO = new Color(100, 100, 100);
    private static final java.util.Locale PT_BR = new java.util.Locale("pt", "BR");

    public void gerarRelatorioPDF(Document doc, List<Campus> campusList, 
                                   Map<Campus, List<Candidato>> grouped, String turno) throws DocumentException {
        
        Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
        Font headerFont = new Font(Font.HELVETICA, 12, Font.BOLD, Color.BLACK);
        Font infoBoldFont = new Font(Font.HELVETICA, 10, Font.BOLD);
        Font normalFont = new Font(Font.HELVETICA, 8);

        // Título "Relação Final" COM BORDA
        String editalDescricao = turno != null && !turno.isBlank() ? turno : "PROEXC nº 06/2024";
        String titulo = "Relação Final dos Candidatos Inscritos no Edital " + editalDescricao + 
                        " - Ingresso no Qualifica Mais EnergIFE";
        
        PdfPTable titleTable = new PdfPTable(1);
        titleTable.setWidthPercentage(100);
        PdfPCell titleCell = new PdfPCell(new Phrase(titulo, titleFont));
        titleCell.setBackgroundColor(CINZA_CLARO);
        titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        titleCell.setPadding(15);
        titleCell.setBorderColor(Color.BLACK);
        titleCell.setBorderWidth(2.5f);
        titleTable.addCell(titleCell);
        doc.add(titleTable);
        doc.add(Chunk.NEWLINE);

        String turnoLine = (turno != null && !turno.isBlank()) ? turno : "";
        DateTimeFormatter dateF = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter timeF = DateTimeFormatter.ofPattern("HH:mm");

        // Ordenar campus alfabeticamente
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
            
            // Agrupar por turno
                Map<String, List<Candidato>> byTurno = candidatos.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                        c -> normalizeTurno(c.getTurno()),
                        java.util.LinkedHashMap::new, 
                        java.util.stream.Collectors.toList()));

            for (Map.Entry<String, List<Candidato>> turnoEntry : byTurno.entrySet()) {
                String turnoName = turnoEntry.getKey();
                
                // Campus e Turno em UMA LINHA, COM BORDA, EM NEGRITO
                PdfPTable infoTable = new PdfPTable(1);
                infoTable.setWidthPercentage(100);
                PdfPCell infoCell = new PdfPCell(new Phrase("Campus: " + campusName + "     |     " + turnoName, infoBoldFont));
                infoCell.setBackgroundColor(CINZA_CLARO);
                infoCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                infoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                infoCell.setPadding(10);
                infoCell.setBorderColor(Color.BLACK);
                infoCell.setBorderWidth(2.5f);
                infoTable.addCell(infoCell);
                doc.add(infoTable);
                doc.add(Chunk.NEWLINE);

                // Criar tabela com cabeçalho cinza
                PdfPTable table = new PdfPTable(new float[] { 2f, 3f, 2.5f, 2.5f });
                table.setWidthPercentage(100);

                // Adicionar células de cabeçalho com cor cinza, negrito, bordas mais grossas
                String[] headerTexts = { "Data e Hora da Inscrição", "Nome Completo", "Classificação", "Situação" };
                for (String headerText : headerTexts) {
                    PdfPCell cell = new PdfPCell(new Phrase(headerText, headerFont));
                    cell.setBackgroundColor(CINZA_CLARO);
                    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    cell.setPadding(10);
                    cell.setBorderColor(Color.BLACK);
                    cell.setBorderWidth(2.5f);
                    table.addCell(cell);
                }

                // Adicionar dados com texto centralizado
                int rank = 0;
                for (Candidato c : turnoEntry.getValue()) {
                    String dateTime = "-";
                    if (c.getDataInscricao() != null) {
                        dateTime = c.getDataInscricao().format(dateF);
                        if (c.getHoraInscricao() != null)
                            dateTime += " " + c.getHoraInscricao().format(timeF);
                    }

                    // Data/Hora centralizado
                    PdfPCell cellData = new PdfPCell(new Phrase(dateTime, normalFont));
                    cellData.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cellData.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    cellData.setPadding(8);
                    cellData.setBorderColor(CINZA_ESCURO);
                    cellData.setBorderWidth(2f);
                    table.addCell(cellData);

                    // Nome centralizado
                    String nomeUpper = c.getNome() != null ? c.getNome().toUpperCase(PT_BR) : "-";
                    PdfPCell cellNome = new PdfPCell(new Phrase(nomeUpper, normalFont));
                    cellNome.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cellNome.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    cellNome.setPadding(8);
                    cellNome.setBorderColor(CINZA_ESCURO);
                    cellNome.setBorderWidth(2f);
                    table.addCell(cellNome);

                    // Classificação
                    PdfPCell cellClassif;
                    if (c.getSituacao() == SituacaoCandidato.CLASSIFICADO) {
                        rank++;
                        cellClassif = new PdfPCell(new Phrase(rank + "°", normalFont));
                    } else {
                        cellClassif = new PdfPCell(new Phrase("-", normalFont));
                    }
                    cellClassif.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cellClassif.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    cellClassif.setPadding(12);
                    cellClassif.setPaddingLeft(15);
                    cellClassif.setBorderColor(CINZA_ESCURO);
                    cellClassif.setBorderWidth(2f);
                    table.addCell(cellClassif);

                    // Situação
                    String situ = c.getSituacao().getDescricao();
                    if (c.getMotivoNaoClassificacao() != null && !c.getMotivoNaoClassificacao().isBlank()) {
                        situ += " - " + c.getMotivoNaoClassificacao();
                    }
                    PdfPCell cellSitu = new PdfPCell(new Phrase(situ, normalFont));
                    cellSitu.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cellSitu.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    cellSitu.setPadding(8);
                    cellSitu.setBorderColor(CINZA_ESCURO);
                    cellSitu.setBorderWidth(2f);
                    table.addCell(cellSitu);
                }

                doc.add(table);
                doc.add(Chunk.NEWLINE);
            }

            // Page break entre campi
            doc.newPage();
        }
    }

    public void gerarRelatorioPreliminar(Document doc, List<Candidato> candidatos, String editalDescricao) throws DocumentException {
        Font titleFont = new Font(Font.HELVETICA, 14, Font.BOLD);
        Font headerFont = new Font(Font.HELVETICA, 11, Font.BOLD, Color.BLACK);
        Font infoBoldFont = new Font(Font.HELVETICA, 10, Font.BOLD);
        Font normalFont = new Font(Font.HELVETICA, 9);

        // Título com descrição do edital
        String titulo = "Relação Preliminar dos Candidatos Inscritos no Edital " + 
                        (editalDescricao != null ? editalDescricao : "PROEXC nº 06/2024") + 
                        " - Ingresso no Qualifica Mais EnergIFE";
        
        PdfPTable titleTable = new PdfPTable(1);
        titleTable.setWidthPercentage(100);
        PdfPCell titleCell = new PdfPCell(new Phrase(titulo, titleFont));
        titleCell.setBackgroundColor(CINZA_CLARO);
        titleCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        titleCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        titleCell.setPadding(15);
        titleCell.setBorderColor(Color.BLACK);
        titleCell.setBorderWidth(2.5f);
        titleTable.addCell(titleCell);
        doc.add(titleTable);
        doc.add(Chunk.NEWLINE);

        DateTimeFormatter dateTimeF = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        // Agrupar por campus
        Map<Campus, List<Candidato>> grouped = candidatos.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                Candidato::getCampus,
                java.util.LinkedHashMap::new,
                java.util.stream.Collectors.toList()));

        // Ordenar campus alfabeticamente
        java.util.List<Map.Entry<Campus, List<Candidato>>> sortedCampusList = grouped.entrySet().stream()
            .sorted((e1, e2) -> {
                String n1 = e1.getKey() != null ? e1.getKey().getNome() : "";
                String n2 = e2.getKey() != null ? e2.getKey().getNome() : "";
                return n1.compareTo(n2);
            })
            .collect(java.util.stream.Collectors.toList());

        for (Map.Entry<Campus, List<Candidato>> campusEntry : sortedCampusList) {
            Campus campus = campusEntry.getKey();
            List<Candidato> candidatosCampus = campusEntry.getValue();

            String campusName = campus != null ? campus.getNome() : "Sem Campus";

            // Agrupar por turno
            Map<String, List<Candidato>> byTurno = candidatosCampus.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    c -> normalizeTurno(c.getTurno()),
                    java.util.LinkedHashMap::new,
                    java.util.stream.Collectors.toList()));

            for (Map.Entry<String, List<Candidato>> turnoEntry : byTurno.entrySet()) {
                String turnoName = turnoEntry.getKey();
                List<Candidato> candidatosTurno = turnoEntry.getValue();

                // Ordenar por ordem de inscrição (mais antigo primeiro)
                java.text.Collator collator = java.text.Collator.getInstance(PT_BR);
                collator.setStrength(java.text.Collator.PRIMARY);

                candidatosTurno.sort((c1, c2) -> {
                    java.time.LocalDate d1 = c1.getDataInscricao();
                    java.time.LocalDate d2 = c2.getDataInscricao();

                    if (d1 != null && d2 != null) {
                        int byDate = d1.compareTo(d2);
                        if (byDate != 0) {
                            return byDate;
                        }
                    } else if (d1 != null) {
                        return -1;
                    } else if (d2 != null) {
                        return 1;
                    }

                    java.time.LocalTime h1 = c1.getHoraInscricao();
                    java.time.LocalTime h2 = c2.getHoraInscricao();
                    if (h1 != null && h2 != null) {
                        int byTime = h1.compareTo(h2);
                        if (byTime != 0) {
                            return byTime;
                        }
                    } else if (h1 != null) {
                        return -1;
                    } else if (h2 != null) {
                        return 1;
                    }

                    String n1 = c1.getNome() != null ? c1.getNome() : "";
                    String n2 = c2.getNome() != null ? c2.getNome() : "";
                    return collator.compare(n1.toUpperCase(PT_BR), n2.toUpperCase(PT_BR));
                });

                // Campus e Turno em UMA LINHA, COM BORDA, EM NEGRITO
                PdfPTable infoTable = new PdfPTable(1);
                infoTable.setWidthPercentage(100);
                PdfPCell infoCell = new PdfPCell(new Phrase("Campus: " + campusName + "     |     " + turnoName, infoBoldFont));
                infoCell.setBackgroundColor(CINZA_CLARO);
                infoCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                infoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                infoCell.setPadding(10);
                infoCell.setBorderColor(Color.BLACK);
                infoCell.setBorderWidth(2.5f);
                infoTable.addCell(infoCell);
                doc.add(infoTable);
                doc.add(Chunk.NEWLINE);

                // Criar tabela com dados do turno
                PdfPTable table = new PdfPTable(new float[] { 2.5f, 3f, 1.5f, 2.5f });
                table.setWidthPercentage(100);

                // Cabeçalho da tabela
                String[] headerTexts = { "Data e Hora da Inscrição", "Nome Completo", "Classificação", "Situação" };
                for (String headerText : headerTexts) {
                    PdfPCell cell = new PdfPCell(new Phrase(headerText, headerFont));
                    cell.setBackgroundColor(CINZA_CLARO);
                    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    cell.setPadding(10);
                    cell.setBorderColor(Color.BLACK);
                    cell.setBorderWidth(2.5f);
                    table.addCell(cell);
                }

                // Adicionar dados dos candidatos
                int rank = 0;
                for (Candidato c : candidatosTurno) {
                    // Data e Hora da Inscrição
                    String dateTime = "-";
                    if (c.getDataInscricao() != null && c.getHoraInscricao() != null) {
                        dateTime = java.time.LocalDateTime.of(c.getDataInscricao(), c.getHoraInscricao()).format(dateTimeF);
                    } else if (c.getDataInscricao() != null) {
                        dateTime = c.getDataInscricao().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                    }

                    PdfPCell cellData = new PdfPCell(new Phrase(dateTime, normalFont));
                    cellData.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cellData.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    cellData.setPadding(8);
                    cellData.setBorderColor(CINZA_ESCURO);
                    cellData.setBorderWidth(1f);
                    table.addCell(cellData);

                    // Nome Completo
                    String nomeUpper = c.getNome() != null ? c.getNome().toUpperCase(PT_BR) : "-";
                    PdfPCell cellNome = new PdfPCell(new Phrase(nomeUpper, normalFont));
                    cellNome.setHorizontalAlignment(Element.ALIGN_LEFT);
                    cellNome.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    cellNome.setPadding(8);
                    cellNome.setBorderColor(CINZA_ESCURO);
                    cellNome.setBorderWidth(1f);
                    table.addCell(cellNome);

                    // Classificação
                    PdfPCell cellClassif;
                    if (c.getSituacao() == SituacaoCandidato.CLASSIFICADO) {
                        rank++;
                        cellClassif = new PdfPCell(new Phrase(rank + "°", normalFont));
                    } else {
                        cellClassif = new PdfPCell(new Phrase("-", normalFont));
                    }
                    cellClassif.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cellClassif.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    cellClassif.setPadding(8);
                    cellClassif.setBorderColor(CINZA_ESCURO);
                    cellClassif.setBorderWidth(1f);
                    table.addCell(cellClassif);

                    // Situação
                    String situ = c.getSituacao().getDescricao();
                    if (c.getMotivoNaoClassificacao() != null && !c.getMotivoNaoClassificacao().isBlank()) {
                        situ += " - " + c.getMotivoNaoClassificacao();
                    }
                    PdfPCell cellSitu = new PdfPCell(new Phrase(situ, normalFont));
                    cellSitu.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cellSitu.setVerticalAlignment(Element.ALIGN_MIDDLE);
                    cellSitu.setPadding(8);
                    cellSitu.setBorderColor(CINZA_ESCURO);
                    cellSitu.setBorderWidth(1f);
                    table.addCell(cellSitu);
                }

                doc.add(table);
                doc.add(Chunk.NEWLINE);
            }

            // Page break entre campi
            doc.newPage();
        }
    }

    private String normalizeTurno(String turnoRaw) {
        if (turnoRaw == null || turnoRaw.isBlank()) {
            return "(sem turno)";
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
            return "(sem turno)";
        }

        return "Turno da " + trimmed.toLowerCase(PT_BR);
    }
}
