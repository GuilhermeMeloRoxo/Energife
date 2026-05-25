package com.example.energif.repository;

import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.energif.model.Vaga;
import com.example.energif.model.Campus;
import com.example.energif.model.Edital;

@Repository
public interface VagaRepository extends JpaRepository<Vaga, Integer> {
    
    Optional<Vaga> findByCampusAndEdital(Campus campus, Edital edital);
    
    List<Vaga> findByCampus(Campus campus);
    
    List<Vaga> findByEdital(Edital edital);
}
