package com.miaoyu.barc.api.service;

import com.miaoyu.barc.api.mapper.ClubMapper;
import com.miaoyu.barc.api.model.SchoolClubModel;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.utils.J;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class ClubService {
    @Autowired
    private ClubMapper clubMapper;

    public ResponseEntity<J> getAllClubsService() {
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, clubMapper.selectAll()));
    }

    public ResponseEntity<J> getClubsBySchoolService(String schoolId) {
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, clubMapper.selectBySchool(schoolId)));
    }

    public ResponseEntity<J> getClubByIdService(String clubId) {
        SchoolClubModel club = clubMapper.selectById(clubId);
        if (Objects.isNull(club)) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, club));
    }
}
