package com.miaoyu.barc.api.service;

import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.api.mapper.ClubMapper;
import com.miaoyu.barc.api.mapper.StudentMapper;
import com.miaoyu.barc.api.model.SchoolClubModel;
import com.miaoyu.barc.api.model.StudentModel;
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
public class StudentService {
    @Autowired
    private StudentMapper studentMapper;
    @Autowired
    private ClubMapper clubMapper;
    @Autowired
    private ImageUrlResolver imageUrlResolver;
    @Autowired
    private CosGcService cosGcService;
    @Autowired
    private CosConfig cosConfig;

    private static final Pattern EN_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9 ]{0,254}$");

    public ResponseEntity<J> getAllStudentsService() {
        List<StudentModel> list = studentMapper.selectAll();
        list.forEach(this::resolveImages);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, list));
    }

    public ResponseEntity<J> getStudentsBySchoolService(String schoolId) {
        List<StudentModel> list = studentMapper.selectBySchool(schoolId);
        list.forEach(this::resolveImages);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, list));
    }

    public ResponseEntity<J> getStudentsByClubIdService(String clubId) {
        List<StudentModel> list = studentMapper.selectByClub(clubId);
        list.forEach(this::resolveImages);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, list));
    }

    public ResponseEntity<J> getStudentByIdService(String id) {
        StudentModel student = studentMapper.selectById(id);
        if (student == null) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        resolveImages(student);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, student));
    }

    public ResponseEntity<J> getStudentsByPage(String clubId, String keyword, int page, int size) {
        int offset = (page - 1) * size;
        List<StudentModel> list;
        long total;
        if (clubId == null || clubId.isEmpty()) {
            list = studentMapper.selectAllByPage(keyword, offset, size);
            total = studentMapper.countAllByKeyword(keyword);
        } else {
            list = studentMapper.selectByPage(clubId, keyword, offset, size);
            total = studentMapper.countByKeyword(clubId, keyword);
        }
        list.forEach(this::resolveImages);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, result));
    }

    public ResponseEntity<J> checkIdAvailable(String id) {
        StudentModel existing = studentMapper.selectById(id);
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
    public ResponseEntity<J> createStudent(String uuid, StudentModel student, String clubId) {
        String enName = student.getEn_name();
        if (enName == null || enName.isEmpty() || !EN_NAME_PATTERN.matcher(enName).matches()) {
            return ResponseEntity.ok(new J(1, "en_name 格式不正确", null));
        }
        if (clubId == null || clubId.isEmpty()) {
            return ResponseEntity.ok(new J(1, "club_id 不能为空", null));
        }
        String id = enName.toLowerCase().replace(" ", "_");
        student.setId(id);
        if (studentMapper.selectById(id) != null) {
            return ResponseEntity.ok(new J(1, "ID 已存在", null));
        }
        SchoolClubModel club = clubMapper.selectById(clubId);
        if (club == null) {
            return ResponseEntity.ok(new J(1, "所属部团不存在", null));
        }
        student.setClub(clubId);
        student.setSchool(club.getSchool());
        studentMapper.insert(student);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, student));
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
        uuidIndex = 0,
        identity = UserIdentityEnum.MANAGER,
        targetPermission = PermissionConst.ADMINISTRATOR,
        isSuchElseRequire = false,
        isHasElseUpper = true
    )})
    @Transactional
    public ResponseEntity<J> updateStudent(String uuid, String id, StudentModel student, String clubId) {
        StudentModel existing = studentMapper.selectById(id);
        if (existing == null) {
            return ResponseEntity.ok(new J(1, "学生不存在", null));
        }
        student.setId(id);
        student.setEn_name(existing.getEn_name());
        // 保留数据库中的原始图片 COS key，不使用前端传入的值
        student.setAvatar_square(existing.getAvatar_square());
        student.setAvatar_rectangle(existing.getAvatar_rectangle());
        student.setBody_image(existing.getBody_image());
        if (clubId != null && !clubId.isEmpty() && !clubId.equals(existing.getClub())) {
            SchoolClubModel club = clubMapper.selectById(clubId);
            if (club == null) {
                return ResponseEntity.ok(new J(1, "目标部团不存在", null));
            }
            student.setClub(clubId);
            student.setSchool(club.getSchool());
        } else {
            student.setClub(existing.getClub());
            student.setSchool(existing.getSchool());
        }
        studentMapper.update(student);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, student));
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
        uuidIndex = 0,
        identity = UserIdentityEnum.MANAGER,
        targetPermission = PermissionConst.ADMINISTRATOR,
        isSuchElseRequire = false,
        isHasElseUpper = true
    )})
    @Transactional
    public ResponseEntity<J> deleteStudent(String uuid, String id) {
        StudentModel existing = studentMapper.selectById(id);
        if (existing == null) {
            return ResponseEntity.ok(new J(1, "学生不存在", null));
        }
        enqueueIfKey(existing.getAvatar_square());
        enqueueIfKey(existing.getAvatar_rectangle());
        enqueueIfKey(existing.getBody_image());
        studentMapper.softDelete(id);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, null));
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
        uuidIndex = 0,
        identity = UserIdentityEnum.MANAGER,
        targetPermission = PermissionConst.ADMINISTRATOR,
        isSuchElseRequire = false,
        isHasElseUpper = true
    )})
    @Transactional
    public ResponseEntity<J> updateAvatarSquare(String uuid, String id, String value) {
        StudentModel existing = studentMapper.selectById(id);
        if (existing == null) {
            return ResponseEntity.ok(new J(1, "学生不存在", null));
        }
        enqueueIfKeyChanged(existing.getAvatar_square(), value);
        studentMapper.updateAvatarSquare(id, value);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, value));
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
        uuidIndex = 0,
        identity = UserIdentityEnum.MANAGER,
        targetPermission = PermissionConst.ADMINISTRATOR,
        isSuchElseRequire = false,
        isHasElseUpper = true
    )})
    @Transactional
    public ResponseEntity<J> updateAvatarRectangle(String uuid, String id, String value) {
        StudentModel existing = studentMapper.selectById(id);
        if (existing == null) {
            return ResponseEntity.ok(new J(1, "学生不存在", null));
        }
        enqueueIfKeyChanged(existing.getAvatar_rectangle(), value);
        studentMapper.updateAvatarRectangle(id, value);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, value));
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
        uuidIndex = 0,
        identity = UserIdentityEnum.MANAGER,
        targetPermission = PermissionConst.ADMINISTRATOR,
        isSuchElseRequire = false,
        isHasElseUpper = true
    )})
    @Transactional
    public ResponseEntity<J> updateBodyImage(String uuid, String id, String value) {
        StudentModel existing = studentMapper.selectById(id);
        if (existing == null) {
            return ResponseEntity.ok(new J(1, "学生不存在", null));
        }
        enqueueIfKeyChanged(existing.getBody_image(), value);
        studentMapper.updateBodyImage(id, value);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, value));
    }

    private void resolveImages(StudentModel student) {
        student.setAvatar_square(imageUrlResolver.resolve(student.getAvatar_square()));
        student.setAvatar_rectangle(imageUrlResolver.resolve(student.getAvatar_rectangle()));
        student.setBody_image(imageUrlResolver.resolve(student.getBody_image()));
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
