
package com.example.hrms.repo;

import com.example.hrms.domain.Shift;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ShiftRepository extends JpaRepository<Shift, Long> {
  Optional<Shift> findByCode(String code);
  boolean existsByCode(String code);
  void deleteByCode(String code);
}
