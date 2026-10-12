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
import org.springframework.transaction.annotation.Isolation;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class StudentService {
    @Autowired private com.miaoyu.barc.api.mapper.CatalogMutationMapper catalogMutationMapper;
    // 等待业务行锁后读取最新提交数据，避免鉴权读在可重复读隔离下固定旧快照。
    @Autowired private com.miaoyu.barc.api.mapper.SchoolMapper schoolMapper;
    @Autowired private com.miaoyu.barc.api.file.service.ObjectStorageService objectStorage;

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

    private static final Pattern EN_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9 ]{0,79}$");

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
        // 集中限制容量与长整型偏移，防止极端分页参数放大查询或溢出。
        var paging = new com.miaoyu.barc.utils.pojo.PageInitPojo(page, size);
        keyword = com.miaoyu.barc.utils.RequestInput.search(keyword, 100);
        size = paging.getPageSize();
        int offset = paging.getOffset();
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
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, studentMapper.countIncludingDeleted(id) == 0));
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
        uuidIndex = 0,
        identity = UserIdentityEnum.MANAGER,
        targetPermission = PermissionConst.ADMINISTRATOR,
        isSuchElseRequire = false,
        isHasElseUpper = false // 基础资料管理按副馆长及以上层级授权，与管理端路由一致。
    )})
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<J> createStudent(String uuid, StudentModel student, String clubId) {
        catalogMutationMapper.lock("foundation");
        com.miaoyu.barc.api.file.service.EntityInput.names(student.getCn_name(), student.getJp_name(), student.getKr_name(), student.getIntroduce());
        String enName = student.getEn_name();
        if (enName == null || enName.isEmpty() || !EN_NAME_PATTERN.matcher(enName).matches()) {
            return ResponseEntity.ok(new J(1, "en_name 格式不正确", null));
        }
        if (clubId == null || clubId.isEmpty()) {
            return ResponseEntity.ok(new J(1, "club_id 不能为空", null));
        }
        String id = enName.toLowerCase(java.util.Locale.ROOT).replace(" ", "_");
        student.setId(id);
        if (studentMapper.countIncludingDeleted(id) > 0) {
            return ResponseEntity.ok(new J(1, "ID 已存在", null));
        }
        SchoolClubModel club = clubMapper.selectByIdForUpdate(clubId);
        if (club == null) {
            return ResponseEntity.ok(new J(1, "所属部团不存在", null));
        }
        // 与学园级联删除互斥，不能在软删除学园下并发创建新学生。
        if (club.getSchool() == null || schoolMapper.selectByIdForUpdate(club.getSchool()) == null) return ResponseEntity.ok(new J(1, "所属学园不存在", null));
        if ((student.getAvatar_rectangle_key() == null && student.getAvatar_rectangle() == null) || (student.getBody_image_key() == null && student.getBody_image() == null)) throw new com.miaoyu.barc.exception.ApiException(new com.miaoyu.barc.response.ErrorR().normal("学生矩形头像和立绘必填"));
        student.setClub(clubId);
        student.setSchool(club.getSchool());
        student.setAvatar_square(objectStorage.replace(uuid, student.getAvatar_square_key() != null ? student.getAvatar_square_key() : student.getAvatar_square(), null));
        student.setAvatar_rectangle(objectStorage.replace(uuid, student.getAvatar_rectangle_key() != null ? student.getAvatar_rectangle_key() : student.getAvatar_rectangle(), null));
        student.setBody_image(objectStorage.replace(uuid, student.getBody_image_key() != null ? student.getBody_image_key() : student.getBody_image(), null));
        if (student.getAvatar_rectangle() == null || student.getBody_image() == null) throw new com.miaoyu.barc.exception.ApiException(new com.miaoyu.barc.response.ErrorR().normal("学生矩形头像和立绘必填"));
        if (studentMapper.insert(student) != 1) throw new com.miaoyu.barc.exception.ApiException(new com.miaoyu.barc.response.ErrorR().normal("创建失败"));
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, student));
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
        uuidIndex = 0,
        identity = UserIdentityEnum.MANAGER,
        targetPermission = PermissionConst.ADMINISTRATOR,
        isSuchElseRequire = false,
        isHasElseUpper = false // 基础资料管理按副馆长及以上层级授权，与管理端路由一致。
    )})
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<J> updateStudent(String uuid, String id, StudentModel student, String clubId) {
        catalogMutationMapper.lock("foundation");
        StudentModel existing = studentMapper.selectByIdForUpdate(id);
        if (existing == null) {
            return ResponseEntity.ok(new J(1, "学生不存在", null));
        }
        student.setId(id);
        com.miaoyu.barc.api.file.service.EntityInput.names(student.getCn_name(), student.getJp_name(), student.getKr_name(), student.getIntroduce());
        com.miaoyu.barc.api.file.service.EntityInput.immutableEnglish(student.getEn_name(), existing.getEn_name());
        student.setEn_name(existing.getEn_name());
        // 保留数据库中的原始图片 COS key，不使用前端传入的值
        student.setAvatar_square(existing.getAvatar_square());
        student.setAvatar_rectangle(existing.getAvatar_rectangle());
        student.setBody_image(existing.getBody_image());
        if (clubId != null && !clubId.isEmpty() && !clubId.equals(existing.getClub())) {
            SchoolClubModel club = clubMapper.selectByIdForUpdate(clubId);
            if (club == null) {
                return ResponseEntity.ok(new J(1, "目标部团不存在", null));
            }
            if (club.getSchool() == null || schoolMapper.selectByIdForUpdate(club.getSchool()) == null) return ResponseEntity.ok(new J(1, "目标学园不存在", null));
            student.setClub(clubId);
            student.setSchool(club.getSchool());
        } else {
            student.setClub(existing.getClub());
            student.setSchool(existing.getSchool());
        }
        if (studentMapper.update(student) != 1) throw new com.miaoyu.barc.exception.ApiException(new com.miaoyu.barc.response.ErrorR().normal("保存失败"));
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, student));
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
        uuidIndex = 0,
        identity = UserIdentityEnum.MANAGER,
        targetPermission = PermissionConst.ADMINISTRATOR,
        isSuchElseRequire = false,
        isHasElseUpper = false // 基础资料管理按副馆长及以上层级授权，与管理端路由一致。
    )})
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<J> deleteStudent(String uuid, String id) {
        catalogMutationMapper.lock("foundation");
        StudentModel existing = studentMapper.selectByIdForUpdate(id);
        if (existing == null) {
            return ResponseEntity.ok(new J(1, "学生不存在", null));
        }
        enqueueIfKey(existing.getAvatar_square());
        enqueueIfKey(existing.getAvatar_rectangle());
        enqueueIfKey(existing.getBody_image());
        // 父事务中的 GC 登记与字段修改必须同时成功。
        if (studentMapper.softDelete(id) != 1) throw com.miaoyu.barc.utils.RequestInput.invalid("学生资料保存失败");
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, null));
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
        uuidIndex = 0,
        identity = UserIdentityEnum.MANAGER,
        targetPermission = PermissionConst.ADMINISTRATOR,
        isSuchElseRequire = false,
        isHasElseUpper = false // 基础资料管理按副馆长及以上层级授权，与管理端路由一致。
    )})
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<J> updateAvatarSquare(String uuid, String id, String value) {
        catalogMutationMapper.lock("foundation");
        StudentModel existing = studentMapper.selectByIdForUpdate(id);
        if (existing == null) {
            return ResponseEntity.ok(new J(1, "学生不存在", null));
        }
        value = objectStorage.replace(uuid, value, existing.getAvatar_square());
        enqueueIfKeyChanged(existing.getAvatar_square(), value);
        // 父事务中的 GC 登记与字段修改必须同时成功。
        if (studentMapper.updateAvatarSquare(id, value) != 1) throw com.miaoyu.barc.utils.RequestInput.invalid("学生资料保存失败");
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, value));
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
        uuidIndex = 0,
        identity = UserIdentityEnum.MANAGER,
        targetPermission = PermissionConst.ADMINISTRATOR,
        isSuchElseRequire = false,
        isHasElseUpper = false // 基础资料管理按副馆长及以上层级授权，与管理端路由一致。
    )})
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<J> updateAvatarRectangle(String uuid, String id, String value) {
        catalogMutationMapper.lock("foundation");
        StudentModel existing = studentMapper.selectByIdForUpdate(id);
        if (existing == null) {
            return ResponseEntity.ok(new J(1, "学生不存在", null));
        }
        value = objectStorage.replace(uuid, value, existing.getAvatar_rectangle());
        // 矩形头像为必填资源；在登记旧图回收和执行 SQL 前拒绝清空。
        if (value == null) throw com.miaoyu.barc.utils.RequestInput.invalid("学生矩形头像必填");
        enqueueIfKeyChanged(existing.getAvatar_rectangle(), value);
        // 父事务中的 GC 登记与字段修改必须同时成功。
        if (studentMapper.updateAvatarRectangle(id, value) != 1) throw com.miaoyu.barc.utils.RequestInput.invalid("学生资料保存失败");
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, value));
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
        uuidIndex = 0,
        identity = UserIdentityEnum.MANAGER,
        targetPermission = PermissionConst.ADMINISTRATOR,
        isSuchElseRequire = false,
        isHasElseUpper = false // 基础资料管理按副馆长及以上层级授权，与管理端路由一致。
    )})
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<J> updateBodyImage(String uuid, String id, String value) {
        catalogMutationMapper.lock("foundation");
        StudentModel existing = studentMapper.selectByIdForUpdate(id);
        if (existing == null) {
            return ResponseEntity.ok(new J(1, "学生不存在", null));
        }
        value = objectStorage.replace(uuid, value, existing.getBody_image());
        // 立绘为必填资源；禁止用空字符串绕过创建时的必填约束。
        if (value == null) throw com.miaoyu.barc.utils.RequestInput.invalid("学生立绘必填");
        enqueueIfKeyChanged(existing.getBody_image(), value);
        // 父事务中的 GC 登记与字段修改必须同时成功。
        if (studentMapper.updateBodyImage(id, value) != 1) throw com.miaoyu.barc.utils.RequestInput.invalid("学生资料保存失败");
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, value));
    }

    /** 共用读取转换供首页聚合使用，始终保留原始 key 并生成展示 URL。 */
    public void resolveImages(StudentModel student) {
        student.setAvatar_square_key(student.getAvatar_square());
        student.setAvatar_square_url(imageUrlResolver.resolve(student.getAvatar_square()));
        student.setAvatar_square(student.getAvatar_square_url());
        student.setAvatar_rectangle_key(student.getAvatar_rectangle());
        student.setAvatar_rectangle_url(imageUrlResolver.resolve(student.getAvatar_rectangle()));
        student.setAvatar_rectangle(student.getAvatar_rectangle_url());
        student.setBody_image_key(student.getBody_image());
        student.setBody_image_url(imageUrlResolver.resolve(student.getBody_image()));
        student.setBody_image(student.getBody_image_url());
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
