package com.miaoyu.barc.api.service;

import com.miaoyu.barc.api.mapper.StudentMapper;
import com.miaoyu.barc.api.model.StudentModel;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.utils.J;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class StudentService {
    @Autowired
    private StudentMapper studentMapper;

    public ResponseEntity<J> getAllStudentsService() {
        return ResponseEntity.ok(
                new ResourceR().resourceSuch(true, studentMapper.selectAll())
        );
    }

    public ResponseEntity<J> getStudentsBySchoolService(String schoolId) {
        return ResponseEntity.ok(
                new ResourceR().resourceSuch(true, studentMapper.selectBySchool(schoolId))
        );
    }

    public ResponseEntity<J> getStudentsByClubIdService(String clubId) {
        return ResponseEntity.ok(
                new ResourceR().resourceSuch(true, studentMapper.selectByClub(clubId))
        );
    }

    public ResponseEntity<J> getStudentsByKeywordService(String keyword) {
        List<StudentModel> students = studentMapper.selectAll();
        List<StudentModel> handledList = new ArrayList<>();
        for (StudentModel student : students) {
            if (student.getCn_name().contains(keyword) || student.getEn_name().toLowerCase().contains(keyword) || student.getEn_name().toUpperCase().contains(keyword) || student.getEn_name().contains(keyword)) {
                handledList.add(student);
            }
        }
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, handledList));
    }

    public ResponseEntity<J> getStudentByIdService(String studentId) {
        StudentModel student = studentMapper.selectById(studentId);
        if (Objects.isNull(student)) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, student));
    }
}
