
package com.example.hrms.web;

import com.example.hrms.domain.Shift;
import com.example.hrms.service.ShiftService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/shifts")
public class ShiftController {
  private final ShiftService service;
  public ShiftController(ShiftService service) { this.service = service; }

  @GetMapping public List<Shift> list(){ return service.list(); }

  @GetMapping("/{code}")
  public ResponseEntity<Shift> get(@PathVariable String code){
    return service.getByCode(code).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
  }

  @PostMapping
  public ResponseEntity<Shift> create(@Valid @RequestBody Shift s){
    Shift saved = service.upsert(s);
    return ResponseEntity.created(URI.create("/api/shifts/" + saved.getCode())).body(saved);
  }

  @PutMapping("/{code}")
  public ResponseEntity<Shift> update(@PathVariable String code, @Valid @RequestBody Shift s){
    s.setCode(code);
    return ResponseEntity.ok(service.upsert(s));
  }

  @DeleteMapping("/{code}")
  public ResponseEntity<Void> delete(@PathVariable String code){
    service.delete(code);
    return ResponseEntity.noContent().build();
  }
}
