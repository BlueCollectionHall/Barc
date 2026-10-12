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
import org.springframework.transaction.annotation.Isolation;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class ClubService {
    @Autowired private com.miaoyu.barc.api.mapper.CatalogMutationMapper catalogMutationMapper;
    // 等待业务行锁后读取最新提交数据，避免鉴权读在可重复读隔离下固定旧快照。
    @Autowired private com.fasterxml.jackson.databind.ObjectMapper json;
    @Autowired private com.miaoyu.barc.api.file.service.ObjectStorageService objectStorage;

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

    private static final Pattern EN_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9 ]{0,79}$");

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
        // 集中限制容量与长整型偏移，防止极端分页参数放大查询或溢出。
        var paging = new com.miaoyu.barc.utils.pojo.PageInitPojo(page, size);
        keyword = com.miaoyu.barc.utils.RequestInput.search(keyword, 100);
        size = paging.getPageSize();
        int offset = paging.getOffset();
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
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, clubMapper.countIncludingDeleted(id) == 0));
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
        uuidIndex = 0,
        identity = UserIdentityEnum.MANAGER,
        targetPermission = PermissionConst.ADMINISTRATOR,
        isSuchElseRequire = false,
        isHasElseUpper = false // 基础资料管理按副馆长及以上层级授权，与管理端路由一致。
    )})
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<J> createClub(String uuid, SchoolClubModel club, String schoolId) {
        catalogMutationMapper.lock("foundation");
        com.miaoyu.barc.api.file.service.EntityInput.names(club.getCn_name(), club.getJp_name(), club.getKr_name(), null);
        String enName = club.getEn_name();
        if (enName == null || enName.isEmpty() || !EN_NAME_PATTERN.matcher(enName).matches()) {
            return ResponseEntity.ok(new J(1, "en_name 格式不正确", null));
        }
        if (schoolId == null || schoolId.isEmpty()) {
            return ResponseEntity.ok(new J(1, "school_id 不能为空", null));
        }
        String id = enName.toLowerCase(java.util.Locale.ROOT).replace(" ", "_");
        club.setId(id);
        if (clubMapper.countIncludingDeleted(id) > 0) {
            return ResponseEntity.ok(new J(1, "ID 已存在", null));
        }
        SchoolModel school = schoolMapper.selectByIdForUpdate(schoolId);
        if (school == null) {
            return ResponseEntity.ok(new J(1, "所属学园不存在", null));
        }
        club.setSchool(schoolId);
        club.setLogo(objectStorage.replace(uuid, club.getLogo_key() != null ? club.getLogo_key() : club.getLogo(), null));
        club.setBg(objectStorage.replace(uuid, club.getBg_key() != null ? club.getBg_key() : club.getBg(), null));
        if (clubMapper.insert(club) != 1) throw new com.miaoyu.barc.exception.ApiException(new com.miaoyu.barc.response.ErrorR().normal("创建失败"));
        if (clubMapper.upsertSchoolClub(schoolId, id) < 1) throw com.miaoyu.barc.utils.RequestInput.invalid("部团学园关系保存失败");
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, club));
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
        uuidIndex = 0,
        identity = UserIdentityEnum.MANAGER,
        targetPermission = PermissionConst.ADMINISTRATOR,
        isSuchElseRequire = false,
        isHasElseUpper = false // 基础资料管理按副馆长及以上层级授权，与管理端路由一致。
    )})
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<J> updateClub(String uuid, String id, SchoolClubModel club, String schoolId) {
        catalogMutationMapper.lock("foundation");
        SchoolClubModel existing = clubMapper.selectByIdForUpdate(id);
        if (existing == null) {
            return ResponseEntity.ok(new J(1, "部团不存在", null));
        }
        club.setId(id);
        com.miaoyu.barc.api.file.service.EntityInput.names(club.getCn_name(), club.getJp_name(), club.getKr_name(), null);
        com.miaoyu.barc.api.file.service.EntityInput.immutableEnglish(club.getEn_name(), existing.getEn_name());
        club.setEn_name(existing.getEn_name());
        // 关系只由独立 schoolId 命令控制，不能把正文中的伪造 school 回显为已保存关系。
        club.setSchool(existing.getSchool());
        club.setLogo(objectStorage.replace(uuid, club.getLogo_key() != null ? club.getLogo_key() : club.getLogo(), existing.getLogo()));
        club.setBg(objectStorage.replace(uuid, club.getBg_key() != null ? club.getBg_key() : club.getBg(), existing.getBg()));
        if (schoolId != null && !schoolId.isEmpty() && !schoolId.equals(existing.getSchool())) {
            SchoolModel school = schoolMapper.selectByIdForUpdate(schoolId);
            if (school == null) {
                return ResponseEntity.ok(new J(1, "目标学园不存在", null));
            }
            clubMapper.deleteSchoolClub(id);
            if (clubMapper.upsertSchoolClub(schoolId, id) < 1) throw com.miaoyu.barc.utils.RequestInput.invalid("部团学园关系保存失败");
            try {
                String previousStudents = json.writeValueAsString(clubMapper.studentsBeforeMove(id));
                if (clubMapper.recordMove(java.util.UUID.randomUUID().toString(), uuid, id, existing.getSchool(), schoolId, previousStudents) != 1) throw com.miaoyu.barc.utils.RequestInput.invalid("部团迁移审计保存失败");
            } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
                throw new IllegalStateException("无法记录部团迁移依据", failure);
            }
            clubMapper.moveStudents(id, schoolId);
            club.setSchool(schoolId);
        }
        if (club.getSchool() == null) club.setSchool(existing.getSchool());
        if (clubMapper.update(club) != 1) throw new com.miaoyu.barc.exception.ApiException(new com.miaoyu.barc.response.ErrorR().normal("保存失败"));
        enqueueIfKeyChanged(existing.getLogo(), club.getLogo());
        enqueueIfKeyChanged(existing.getBg(), club.getBg());
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, club));
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
        uuidIndex = 0,
        identity = UserIdentityEnum.MANAGER,
        targetPermission = PermissionConst.ADMINISTRATOR,
        isSuchElseRequire = false,
        isHasElseUpper = false // 基础资料管理按副馆长及以上层级授权，与管理端路由一致。
    )})
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<J> deleteClub(String uuid, String id) {
        catalogMutationMapper.lock("foundation");
        SchoolClubModel existing = clubMapper.selectByIdForUpdate(id);
        if (existing == null) {
            return ResponseEntity.ok(new J(1, "部团不存在", null));
        }
        enqueueIfKey(existing.getLogo());
        enqueueIfKey(existing.getBg());
        clubMapper.studentImageKeys(id).forEach(this::enqueueIfKey);
        clubMapper.softDeleteStudentsByClub(id);
        if (clubMapper.softDelete(id) != 1) throw com.miaoyu.barc.utils.RequestInput.invalid("部团删除失败");
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, null));
    }

    private void resolveImages(SchoolClubModel club) {
        club.setLogo_key(club.getLogo());
        club.setLogo_url(imageUrlResolver.resolve(club.getLogo()));
        club.setLogo(club.getLogo_url());
        club.setBg_key(club.getBg());
        club.setBg_url(imageUrlResolver.resolve(club.getBg()));
        club.setBg(club.getBg_url());
    }

    /** 移动前提供影响人数，实际保存仍会在事务内重新校验并记录完整变更依据。 */
    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.ADMINISTRATOR, isSuchElseRequire = false)})
    public ResponseEntity<J> movePreview(String uuid, String id, String schoolId) {
        SchoolClubModel existing = clubMapper.selectById(id);
        if (existing == null || schoolMapper.selectById(schoolId) == null) return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, Map.of("affected_students", clubMapper.studentsBeforeMove(id).size())));
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
