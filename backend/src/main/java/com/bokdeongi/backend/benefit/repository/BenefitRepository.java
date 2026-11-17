package com.bokdeongi.backend.benefit.repository;

import com.bokdeongi.backend.benefit.entity.Benefit;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BenefitRepository extends JpaRepository<Benefit, Long> {

    Optional<Benefit> findByServiceId(String serviceId);
}
