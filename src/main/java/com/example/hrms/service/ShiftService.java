
package com.example.hrms.service;

import com.example.hrms.domain.Shift;
import com.example.hrms.repo.ShiftRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ShiftService {
  private final ShiftRepository repo;
  public ShiftService(ShiftRepository repo) { this.repo = repo; }

  public List<Shift> list() { return repo.findAll(); }
  public Optional<Shift> getByCode(String code) { return repo.findByCode(code); }

  @Transactional
  public Shift upsert(Shift s) {
    return repo.findByCode(s.getCode()).map(cur -> {
      cur.setName(s.getName()); cur.setStartTime(s.getStartTime()); cur.setEndTime(s.getEndTime());
      cur.setBreakMins(s.getBreakMins()); cur.setGraceInMins(s.getGraceInMins()); cur.setGraceOutMins(s.getGraceOutMins());
      cur.setBoundaryAfterMidnightMins(s.getBoundaryAfterMidnightMins()); cur.setRounding(s.getRounding());
      cur.setHalfdayThresholdMins(s.getHalfdayThresholdMins()); cur.setMinWorkMins(s.getMinWorkMins());
      cur.setEffectiveFrom(s.getEffectiveFrom()); cur.setEffectiveTo(s.getEffectiveTo());
      cur.setMon(s.isMon()); cur.setTue(s.isTue()); cur.setWed(s.isWed()); cur.setThu(s.isThu());
      cur.setFri(s.isFri()); cur.setSat(s.isSat()); cur.setSun(s.isSun()); cur.setActive(s.isActive());
      return repo.save(cur);
    }).orElseGet(() -> repo.save(s));
  }

  public void delete(String code) { repo.deleteByCode(code); }
}
