package com.bokdeongi.backend.benefit.repository;

import com.bokdeongi.backend.benefit.entity.BenefitRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BenefitRuleRepository extends JpaRepository<BenefitRule, Long> {

    List<BenefitRule> findByBenefitIdOrderByRuleGroupAscIdAsc(Long benefitId);
}
