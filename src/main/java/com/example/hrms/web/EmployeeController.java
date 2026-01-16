package com.example.hrms.web;

import com.example.hrms.domain.Employee;
import com.example.hrms.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/employees")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS, RequestMethod.PATCH})
public class EmployeeController {
    private final EmployeeService service;
    public EmployeeController(EmployeeService service) { this.service = service; }

    @GetMapping
    public List<Employee> list(){ return service.list(); }

    // ✅ name the path variable explicitly
    @GetMapping("/{empCode}")
    public ResponseEntity<Employee> get(@PathVariable("empCode") String empCode){
        return service.getByEmpCode(empCode).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Employee> create(@Valid @RequestBody Employee e){
        Employee saved = service.upsert(e);
        return ResponseEntity.created(URI.create("/api/employees/" + saved.getEmpCode())).body(saved);
    }

    @PutMapping("/{empCode}")
    public ResponseEntity<Employee> update(@PathVariable("empCode") String empCode,
        @Valid @RequestBody Employee e) {
        e.setEmpCode(empCode);
        Employee saved = service.upsert(e);
        return ResponseEntity.ok(saved);
    }

    // ✅ name the path variable explicitly
    @DeleteMapping("/{empCode}")
    public ResponseEntity<Void> delete(@PathVariable("empCode") String empCode){
        service.delete(empCode);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bulk-upload")
    public List<Employee> bulkUpload(@RequestBody List<Employee> list){
        return service.bulkUpsert(list);
    }

    @PostMapping("/import-excel")
    public List<Employee> importExcel(@RequestParam("file") MultipartFile file) throws Exception {
        return service.importExcel(file);
    }
}
