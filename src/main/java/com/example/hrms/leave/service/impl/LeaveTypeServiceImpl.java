package com.example.hrms.leave.service.impl;

import com.example.hrms.leave.domain.LeaveType;
import com.example.hrms.leave.repo.LeaveTypeRepository;
import com.example.hrms.leave.service.LeaveTypeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service @Transactional
public class LeaveTypeServiceImpl implements LeaveTypeService {
  private final LeaveTypeRepository repo;
  public LeaveTypeServiceImpl(LeaveTypeRepository repo){ this.repo = repo; }

  @Override public List<LeaveType> list(String orgId){ return repo.findByOrgIdAndActiveTrue(orgId); }
  @Override public LeaveType create(LeaveType dto){ return repo.save(dto); }
  @Override public LeaveType update(Long id, LeaveType dto){
    dto.setId(id);
    return repo.save(dto);
  }
  @Override public void activate(Long id){ repo.findById(id).ifPresent(t -> { t.setActive(true); repo.save(t); }); }
  @Override public void deactivate(Long id){ repo.findById(id).ifPresent(t -> { t.setActive(false); repo.save(t); }); }
}
