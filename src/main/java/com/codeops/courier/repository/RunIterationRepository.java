package com.codeops.courier.repository;

import com.codeops.courier.entity.RunIteration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RunIterationRepository extends JpaRepository<RunIteration, UUID> {

    List<RunIteration> findByRunResultIdOrderByIterationNumber(UUID runResultId);
}
