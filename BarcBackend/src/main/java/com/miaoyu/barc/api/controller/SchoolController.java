package com.miaoyu.barc.api.controller;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.api.model.SchoolModel;
import com.miaoyu.barc.api.service.SchoolService;
import com.miaoyu.barc.utils.J;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/school")
public class SchoolController {
    @Autowired
    private SchoolService schoolService;

    @IgnoreAuth
    @GetMapping("/list")
    public ResponseEntity<J> list(
            @RequestParam(value = "keyword", required = false, defaultValue = "") String keyword,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "size", required = false, defaultValue = "20") int size) {
        return schoolService.getSchoolsByPage(keyword, page, size);
    }

    @IgnoreAuth
    @GetMapping("/{id}")
    public ResponseEntity<J> getById(@PathVariable("id") String id) {
        return schoolService.getSchoolById(id);
    }

    @PostMapping("")
    public ResponseEntity<J> create(@RequestBody SchoolModel school, HttpServletRequest request) {
        String uuid = (String) request.getAttribute("uuid");
        return schoolService.createSchool(uuid, school);
    }

    @PutMapping("/{id}")
    public ResponseEntity<J> update(@PathVariable("id") String id, @RequestBody SchoolModel school, HttpServletRequest request) {
        String uuid = (String) request.getAttribute("uuid");
        return schoolService.updateSchool(uuid, id, school);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<J> delete(@PathVariable("id") String id, HttpServletRequest request) {
        String uuid = (String) request.getAttribute("uuid");
        return schoolService.deleteSchool(uuid, id);
    }

    @IgnoreAuth
    @GetMapping("/check_id_available")
    public ResponseEntity<J> checkIdAvailable(@RequestParam("id") String id) {
        return schoolService.checkIdAvailable(id);
    }
}
