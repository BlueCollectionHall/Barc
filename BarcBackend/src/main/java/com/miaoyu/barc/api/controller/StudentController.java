package com.miaoyu.barc.api.controller;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.api.model.StudentModel;
import com.miaoyu.barc.api.service.StudentService;
import com.miaoyu.barc.utils.J;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/student")
public class StudentController {
    @Autowired
    private StudentService studentService;

    @IgnoreAuth
    @GetMapping("/list")
    public ResponseEntity<J> list(
            @RequestParam(value = "club_id", required = false) String clubId,
            @RequestParam(value = "keyword", required = false, defaultValue = "") String keyword,
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "size", required = false, defaultValue = "20") int size) {
        return studentService.getStudentsByPage(clubId, keyword, page, size);
    }

    @IgnoreAuth
    @GetMapping("/{id}")
    public ResponseEntity<J> getById(@PathVariable("id") String id) {
        return studentService.getStudentByIdService(id);
    }

    @PostMapping("")
    public ResponseEntity<J> create(@RequestBody StudentModel student, @RequestParam("club_id") String clubId, HttpServletRequest request) {
        String uuid = (String) request.getAttribute("uuid");
        return studentService.createStudent(uuid, student, clubId);
    }

    @PutMapping("/{id}")
    public ResponseEntity<J> update(@PathVariable("id") String id, @RequestBody StudentModel student, @RequestParam(value = "club_id", required = false) String clubId, HttpServletRequest request) {
        String uuid = (String) request.getAttribute("uuid");
        return studentService.updateStudent(uuid, id, student, clubId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<J> delete(@PathVariable("id") String id, HttpServletRequest request) {
        String uuid = (String) request.getAttribute("uuid");
        return studentService.deleteStudent(uuid, id);
    }

    @PutMapping("/{id}/avatar_square")
    public ResponseEntity<J> updateAvatarSquare(@PathVariable("id") String id, @RequestParam("value") String value, HttpServletRequest request) {
        String uuid = (String) request.getAttribute("uuid");
        return studentService.updateAvatarSquare(uuid, id, value);
    }

    @PutMapping("/{id}/avatar_rectangle")
    public ResponseEntity<J> updateAvatarRectangle(@PathVariable("id") String id, @RequestParam("value") String value, HttpServletRequest request) {
        String uuid = (String) request.getAttribute("uuid");
        return studentService.updateAvatarRectangle(uuid, id, value);
    }

    @PutMapping("/{id}/body_image")
    public ResponseEntity<J> updateBodyImage(@PathVariable("id") String id, @RequestParam("value") String value, HttpServletRequest request) {
        String uuid = (String) request.getAttribute("uuid");
        return studentService.updateBodyImage(uuid, id, value);
    }

    @IgnoreAuth
    @GetMapping("/check_id_available")
    public ResponseEntity<J> checkIdAvailable(@RequestParam("id") String id) {
        return studentService.checkIdAvailable(id);
    }
}
