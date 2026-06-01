package com.miaoyu.barc.api.service;

import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.api.mapper.ClubMapper;
import com.miaoyu.barc.api.mapper.SchoolMapper;
import com.miaoyu.barc.api.model.SchoolClubModel;
import com.miaoyu.barc.api.model.SchoolModel;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.tencent.cos.CosConfig;
import com.miaoyu.barc.utils.tencent.cos.ImageUrlResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class ClubService {
    @Autowired
    private ClubMapper clubMapper;
    @Autowired
    private SchoolMapper schoolMapper;
    @Autowired
    private ImageUrlResolver imageUrlResolver;
    @Autowired
    private CosGcService cosGcService;
    @Autowired
    private CosConfig cosConfig;

    private static final Pattern EN_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9 ]{0,254}$");

    public ResponseEntity<J> getAllClubsService() {
        List<SchoolClubModel> list = clubMapper.selectAll();
        list.forEach(this::resolveImages);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, list));
    }

    public ResponseEntity<J> getClubsBySchoolService(String schoolId) {
        List<SchoolClubModel> list = clubMapper.selectBySchool(schoolId);
        list.forEach(this::resolveImages);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, list));
    }

    public ResponseEntity<J> getClubByIdService(String id) {
        SchoolClubModel club = clubMapper.selectById(id);
        if (club == null) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        resolveImages(club);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, club));
    }

    public ResponseEntity<J> getClubsByPage(String schoolId, String keyword, int page, int size) {
        int offset = (page - 1) * size;
        List<SchoolClubModel> list;
        long total;
        if (schoolId == null || schoolId.isEmpty()) {
            list = clubMapper.selectAllByPage(keyword, offset, size);
            total = clubMapper.countAllByKeyword(keyword);
        } else {
            list = clubMapper.selectByPage(schoolId, keyword, offset, size);
            total = clubMapper.countByKeyword(schoolId, keyword);
        }
        list.forEach(this::resolveImages);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, result));
    }

    public ResponseEntity<J> checkIdAvailable(String id) {
        SchoolClubModel existing = clubMapper.selectById(id);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, existing == null));
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
        uuidIndex = 0,
        identity = UserIdentityEnum.MANAGER,
        targetPermission = PermissionConst.ADMINISTRATOR,
        isSuchElseRequire = false,
        isHasElseUpper = true
    )})
    @Transactional
    public ResponseEntity<J> createClub(String uuid, SchoolClubModel club, String schoolId) {
        String enName = club.getEn_name();
        if (enName == null || enName.isEmpty() || !EN_NAME_PATTERN.matcher(enName).matches()) {
            return ResponseEntity.ok(new J(1, "en_name 格式不正确", null));
        }
        if (schoolId == null || schoolId.isEmpty()) {
            return ResponseEntity.ok(new J(1, "school_id 不能为空", null));
        }
        String id = enName.toLowerCase().replace(" ", "_");
        club.setId(id);
        if (clubMapper.selectById(id) != null) {
            return ResponseEntity.ok(new J(1, "ID 已存在", null));
        }
        SchoolModel school = schoolMapper.selectById(schoolId);
        if (school == null) {
            return ResponseEntity.ok(new J(1, "所属学园不存在", null));
        }
        club.setSchool(schoolId);
        clubMapper.insert(club);
        clubMapper.upsertSchoolClub(schoolId, id);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, club));
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
        uuidIndex = 0,
        identity = UserIdentityEnum.MANAGER,
        targetPermission = PermissionConst.ADMINISTRATOR,
        isSuchElseRequire = false,
        isHasElseUpper = true
    )})
    @Transactional
    public ResponseEntity<J> updateClub(String uuid, String id, SchoolClubModel club, String schoolId) {
        SchoolClubModel existing = clubMapper.selectById(id);
        if (existing == null) {
            return ResponseEntity.ok(new J(1, "部团不存在", null));
        }
        club.setId(id);
        club.setEn_name(existing.getEn_name());
        enqueueIfKeyChanged(existing.getLogo(), club.getLogo());
        enqueueIfKeyChanged(existing.getBg(), club.getBg());
        if (schoolId != null && !schoolId.isEmpty() && !schoolId.equals(existing.getSchool())) {
            SchoolModel school = schoolMapper.selectById(schoolId);
            if (school == null) {
                return ResponseEntity.ok(new J(1, "目标学园不存在", null));
            }
            clubMapper.deleteSchoolClub(id);
            clubMapper.upsertSchoolClub(schoolId, id);
        }
        clubMapper.update(club);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, club));
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
        uuidIndex = 0,
        identity = UserIdentityEnum.MANAGER,
        targetPermission = PermissionConst.ADMINISTRATOR,
        isSuchElseRequire = false,
        isHasElseUpper = true
    )})
    @Transactional
    public ResponseEntity<J> deleteClub(String uuid, String id) {
        SchoolClubModel existing = clubMapper.selectById(id);
        if (existing == null) {
            return ResponseEntity.ok(new J(1, "部团不存在", null));
        }
        enqueueIfKey(existing.getLogo());
        enqueueIfKey(existing.getBg());
        clubMapper.softDeleteStudentsByClub(id);
        clubMapper.softDelete(id);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, null));
    }

    private void resolveImages(SchoolClubModel club) {
        club.setLogo(imageUrlResolver.resolve(club.getLogo()));
        club.setBg(imageUrlResolver.resolve(club.getBg()));
    }

    private void enqueueIfKeyChanged(String oldValue, String newValue) {
        if (oldValue != null && !oldValue.equals(newValue) && imageUrlResolver.isKey(oldValue)) {
            cosGcService.enqueue(cosConfig.getImage().getBucketName(), oldValue);
        }
    }

    private void enqueueIfKey(String value) {
        if (imageUrlResolver.isKey(value)) {
            cosGcService.enqueue(cosConfig.getImage().getBucketName(), value);
        }
    }
}
