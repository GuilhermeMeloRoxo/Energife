package com.example.energif.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.energif.model.Candidato;
import com.example.energif.model.SituacaoCandidato;

public interface CandidatoRepository extends JpaRepository<Candidato, Long> {

	// Search by name (contains), campus name (contains) or cpf (exact or contains)
	@Query("select c from Candidato c left join c.campus cp where " +
			"(:q is null or lower(c.nome) like lower(concat('%', :q, '%')) " +
			"or (:q is null or lower(cp.nome) like lower(concat('%', :q, '%'))) " +
			"or (:q is null or c.cpf like concat('%', :q, '%')))"
	)
	Page<Candidato> search(@Param("q") String q, Pageable pageable);

		long countByEditalAndCampus(com.example.energif.model.Edital edital, com.example.energif.model.Campus campus);

	@Query("select c from Candidato c left join c.campus cp where " +
		"(:q is null or lower(c.nome) like lower(concat('%', :q, '%')) " +
		"or (:q is null or lower(cp.nome) like lower(concat('%', :q, '%'))) " +
		"or (:q is null or c.cpf like concat('%', :q, '%'))) " +
		"and (:campusId is null or cp.id = :campusId) " +
		"and (:genero is null or c.genero = :genero)")
	Page<Candidato> searchByCampus(@Param("q") String q, @Param("campusId") Long campusId, @Param("genero") Character genero, Pageable pageable);

    // Combined search supporting q, campusId, genero and age group (maior/minor de 18 anos)
	@Query(value = "SELECT c.* FROM candidato c LEFT JOIN campus cp ON cp.id = c.campus_id " +
    "WHERE (:q IS NULL OR lower(c.nome) LIKE concat('%', :q, '%') OR lower(cp.nome) LIKE concat('%', :q, '%') OR c.cpf LIKE concat('%', :q, '%')) " +
    "AND (:campusId IS NULL OR cp.id = :campusId) " +
    "AND (:genero IS NULL OR c.genero = :genero) " +
    "AND (:idade IS NULL OR (:idade = 'maior' AND c.data_nascimento <= (current_date - INTERVAL '18 years')) OR (:idade = 'menor' AND c.data_nascimento > (current_date - INTERVAL '18 years'))) " +
    "AND (:situacao IS NULL OR c.situacao = :situacao) " +
    "AND (:turno IS NULL OR lower(c.turno) = lower(:turno))", // <-- CLÁUSULA ATUALIZADA PARA USAR SITUACAO
    
    countQuery = "SELECT count(c.id) FROM candidato c LEFT JOIN campus cp ON cp.id = c.campus_id " +
        "WHERE (:q IS NULL OR lower(c.nome) LIKE concat('%', :q, '%') OR lower(cp.nome) LIKE concat('%', :q, '%') OR c.cpf LIKE concat('%', :q, '%')) " +
        "AND (:campusId IS NULL OR cp.id = :campusId) " +
        "AND (:genero IS NULL OR c.genero = :genero) " +
        "AND (:idade IS NULL OR (:idade = 'maior' AND c.data_nascimento <= (current_date - INTERVAL '18 years')) OR (:idade = 'menor' AND c.data_nascimento > (current_date - INTERVAL '18 years'))) " +
        "AND (:situacao IS NULL OR c.situacao = :situacao) " +
        "AND (:turno IS NULL OR lower(c.turno) = lower(:turno))", // <-- CLÁUSULA ATUALIZADA PARA USAR SITUACAO
    nativeQuery = true)
	Page<Candidato> searchCombined(@Param("q") String q,
				   @Param("campusId") Long campusId,
				   @Param("genero") Character genero,
				   @Param("idade") String idade,
				   @Param("situacao") String situacao,@Param("turno") String turno,
                       Pageable pageable);

	    // For report generation: fetch ordered list of candidates by inscription date/time
	    List<Candidato> findAllByCampusIdOrderByDataInscricaoAscHoraInscricao(Long campusId);

	    List<Candidato> findAllByCampusIdAndTurnoOrderByDataInscricaoAscHoraInscricao(Long campusId, String turno);

	    // Para alocação de vagas: buscar candidatos de um campus e edital ordenados por data de inscrição
	    List<Candidato> findByCampusIdAndEditalIdOrderByDataInscricaoAscHoraInscricaoAsc(Long campusId, Long editalId);

		List<Candidato> findByCampusIdAndEditalIdAndTurnoOrderByDataInscricaoAscHoraInscricaoAsc(Long campusId, Long editalId, String turno);

	    @Query("select distinct c.turno from Candidato c")
	    List<String> findDistinctTurno();

		// Find CPFs that appear more than once and their counts
		@Query(value = "SELECT cpf, COUNT(cpf) AS total_de_repeticoes FROM candidato GROUP BY cpf HAVING COUNT(cpf) > 1", nativeQuery = true)
		List<Object[]> findDuplicateCpfs();

		// Count candidates by situation (avoid loading all records)
		long countBySituacao(SituacaoCandidato situacao);

		@Query(value = "SELECT c.situacao, COUNT(c.id) " +
				"FROM candidato c LEFT JOIN campus cp ON cp.id = c.campus_id " +
				"WHERE (:q IS NULL OR lower(c.nome) LIKE concat('%', :q, '%') OR lower(cp.nome) LIKE concat('%', :q, '%') OR c.cpf LIKE concat('%', :q, '%')) " +
				"AND (:campusId IS NULL OR cp.id = :campusId) " +
				"AND (:genero IS NULL OR c.genero = :genero) " +
				"AND (:idade IS NULL OR (:idade = 'maior' AND c.data_nascimento <= (current_date - INTERVAL '18 years')) OR (:idade = 'menor' AND c.data_nascimento > (current_date - INTERVAL '18 years'))) " +
				"AND (:situacao IS NULL OR c.situacao = :situacao) " +
				"AND (:turno IS NULL OR lower(c.turno) = lower(:turno)) " +
				"GROUP BY c.situacao",
				nativeQuery = true)
		List<Object[]> countSituacaoByFilters(@Param("q") String q,
										 @Param("campusId") Long campusId,
										 @Param("genero") Character genero,
										 @Param("idade") String idade,
										 @Param("situacao") String situacao,
										 @Param("turno") String turno);
}
