package com.miaoyu.barc.api.service;

import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.api.mapper.SchoolMapper;
import com.miaoyu.barc.api.model.SchoolModel;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.tencent.cos.CosBucketConfigEnum;
import com.miaoyu.barc.utils.tencent.cos.CosConfig;
import com.miaoyu.barc.utils.tencent.cos.ImageUrlResolver;
import com.miaoyu.barc.api.service.CosGcService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class SchoolService {
    @Autowired
    private SchoolMapper schoolMapper;
    @Autowired
    private ImageUrlResolver imageUrlResolver;
    @Autowired
    private CosGcService cosGcService;
    @Autowired
    private CosConfig cosConfig;

    private static final Pattern EN_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9 ]{0,254}$");

    public ResponseEntity<J> getAllSchoolsService() {
        List<SchoolModel> list = schoolMapper.selectAll();
        list.forEach(this::resolveImages);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, list));
    }

    public ResponseEntity<J> getSchoolById(String id) {
        SchoolModel school = schoolMapper.selectById(id);
        if (school == null) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        resolveImages(school);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, school));
    }

    public ResponseEntity<J> getSchoolsByPage(String keyword, int page, int size) {
        int offset = (page - 1) * size;
        List<SchoolModel> list = schoolMapper.selectByPage(keyword, offset, size);
        long total = schoolMapper.countByKeyword(keyword);
        list.forEach(this::resolveImages);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, result));
    }

    public ResponseEntity<J> checkIdAvailable(String id) {
        SchoolModel existing = schoolMapper.selectById(id);
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
    public ResponseEntity<J> createSchool(String uuid, SchoolModel school) {
        String enName = school.getEn_name();
        if (enName == null || enName.isEmpty() || !EN_NAME_PATTERN.matcher(enName).matches()) {
            return ResponseEntity.ok(new J(1, "en_name 格式不正确", null));
        }
        String id = enName.toLowerCase().replace(" ", "_");
        school.setId(id);
        if (schoolMapper.selectById(id) != null) {
            return ResponseEntity.ok(new J(1, "ID 已存在", null));
        }
        schoolMapper.insert(school);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, school));
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
        uuidIndex = 0,
        identity = UserIdentityEnum.MANAGER,
        targetPermission = PermissionConst.ADMINISTRATOR,
        isSuchElseRequire = false,
        isHasElseUpper = true
    )})
    @Transactional
    public ResponseEntity<J> updateSchool(String uuid, String id, SchoolModel school) {
        SchoolModel existing = schoolMapper.selectById(id);
        if (existing == null) {
            return ResponseEntity.ok(new J(1, "学园不存在", null));
        }
        // 忽略 en_name 修改
        school.setId(id);
        school.setEn_name(existing.getEn_name());
        // 旧 key 入 GC
        enqueueIfKeyChanged(existing.getLogo(), school.getLogo());
        enqueueIfKeyChanged(existing.getBeautify_logo(), school.getBeautify_logo());
        enqueueIfKeyChanged(existing.getBg(), school.getBg());
        schoolMapper.update(school);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, school));
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
        uuidIndex = 0,
        identity = UserIdentityEnum.MANAGER,
        targetPermission = PermissionConst.ADMINISTRATOR,
        isSuchElseRequire = false,
        isHasElseUpper = true
    )})
    @Transactional
    public ResponseEntity<J> deleteSchool(String uuid, String id) {
        SchoolModel existing = schoolMapper.selectById(id);
        if (existing == null) {
            return ResponseEntity.ok(new J(1, "学园不存在", null));
        }
        // 所有 key 入 GC
        enqueueIfKey(existing.getLogo());
        enqueueIfKey(existing.getBeautify_logo());
        enqueueIfKey(existing.getBg());
        schoolMapper.softDeleteStudentsBySchool(id);
        schoolMapper.softDeleteClubsBySchool(id);
        schoolMapper.softDelete(id);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, null));
    }

    private void resolveImages(SchoolModel school) {
        school.setLogo(imageUrlResolver.resolve(school.getLogo()));
        school.setBeautify_logo(imageUrlResolver.resolve(school.getBeautify_logo()));
        school.setBg(imageUrlResolver.resolve(school.getBg()));
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
