package com.miaoyu.barc.api.service;

import com.miaoyu.barc.api.mapper.SchoolMapper;
import com.miaoyu.barc.api.model.SchoolModel;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.utils.J;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class SchoolService {
    @Autowired
    private SchoolMapper schoolMapper;

    public ResponseEntity<J> getAllSchoolsService() {
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, schoolMapper.selectAll()));
    }

    public ResponseEntity<J> getSchoolById(String schoolId) {
        SchoolModel school = schoolMapper.selectById(schoolId);
        if (Objects.isNull(school)) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, school));
    }
}
