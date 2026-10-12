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
import org.springframework.transaction.annotation.Isolation;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class SchoolService {
    @Autowired private com.miaoyu.barc.api.mapper.CatalogMutationMapper catalogMutationMapper;
    // 等待业务行锁后读取最新提交数据，避免鉴权读在可重复读隔离下固定旧快照。
    @Autowired private com.miaoyu.barc.api.file.service.ObjectStorageService objectStorage;

    @Autowired
    private SchoolMapper schoolMapper;
    @Autowired
    private ImageUrlResolver imageUrlResolver;
    @Autowired
    private CosGcService cosGcService;
    @Autowired
    private CosConfig cosConfig;

    private static final Pattern EN_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9 ]{0,79}$");

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
        // 集中限制容量与长整型偏移，防止极端分页参数放大查询或溢出。
        var paging = new com.miaoyu.barc.utils.pojo.PageInitPojo(page, size);
        keyword = com.miaoyu.barc.utils.RequestInput.search(keyword, 100);
        size = paging.getPageSize();
        int offset = paging.getOffset();
        List<SchoolModel> list = schoolMapper.selectByPage(keyword, offset, size);
        long total = schoolMapper.countByKeyword(keyword);
        list.forEach(this::resolveImages);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, result));
    }

    public ResponseEntity<J> checkIdAvailable(String id) {
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, schoolMapper.countIncludingDeleted(id) == 0));
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
        uuidIndex = 0,
        identity = UserIdentityEnum.MANAGER,
        targetPermission = PermissionConst.ADMINISTRATOR,
        isSuchElseRequire = false,
        isHasElseUpper = false // 基础资料管理按副馆长及以上层级授权，与管理端路由一致。
    )})
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<J> createSchool(String uuid, SchoolModel school) {
        catalogMutationMapper.lock("foundation");
        com.miaoyu.barc.api.file.service.EntityInput.names(school.getCn_name(), school.getJp_name(), school.getKr_name(), school.getIntroduce());
        String enName = school.getEn_name();
        if (enName == null || enName.isEmpty() || !EN_NAME_PATTERN.matcher(enName).matches()) {
            return ResponseEntity.ok(new J(1, "en_name 格式不正确", null));
        }
        String id = enName.toLowerCase(java.util.Locale.ROOT).replace(" ", "_");
        school.setId(id);
        if (schoolMapper.countIncludingDeleted(id) > 0) {
            return ResponseEntity.ok(new J(1, "ID 已存在", null));
        }
        school.setLogo(objectStorage.replace(uuid, school.getLogo_key() != null ? school.getLogo_key() : school.getLogo(), null));
        school.setBeautify_logo(objectStorage.replace(uuid, school.getBeautify_logo_key() != null ? school.getBeautify_logo_key() : school.getBeautify_logo(), null));
        school.setBg(objectStorage.replace(uuid, school.getBg_key() != null ? school.getBg_key() : school.getBg(), null));
        if (schoolMapper.insert(school) != 1) throw new com.miaoyu.barc.exception.ApiException(new com.miaoyu.barc.response.ErrorR().normal("创建失败"));
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, school));
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
        uuidIndex = 0,
        identity = UserIdentityEnum.MANAGER,
        targetPermission = PermissionConst.ADMINISTRATOR,
        isSuchElseRequire = false,
        isHasElseUpper = false // 基础资料管理按副馆长及以上层级授权，与管理端路由一致。
    )})
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<J> updateSchool(String uuid, String id, SchoolModel school) {
        catalogMutationMapper.lock("foundation");
        SchoolModel existing = schoolMapper.selectByIdForUpdate(id);
        if (existing == null) {
            return ResponseEntity.ok(new J(1, "学园不存在", null));
        }
        // 忽略 en_name 修改
        school.setId(id);
        com.miaoyu.barc.api.file.service.EntityInput.names(school.getCn_name(), school.getJp_name(), school.getKr_name(), school.getIntroduce());
        com.miaoyu.barc.api.file.service.EntityInput.immutableEnglish(school.getEn_name(), existing.getEn_name());
        school.setEn_name(existing.getEn_name());
        // 旧 key 入 GC
        school.setLogo(objectStorage.replace(uuid, school.getLogo_key() != null ? school.getLogo_key() : school.getLogo(), existing.getLogo()));
        enqueueIfKeyChanged(existing.getLogo(), school.getLogo());
        school.setBeautify_logo(objectStorage.replace(uuid, school.getBeautify_logo_key() != null ? school.getBeautify_logo_key() : school.getBeautify_logo(), existing.getBeautify_logo()));
        enqueueIfKeyChanged(existing.getBeautify_logo(), school.getBeautify_logo());
        school.setBg(objectStorage.replace(uuid, school.getBg_key() != null ? school.getBg_key() : school.getBg(), existing.getBg()));
        enqueueIfKeyChanged(existing.getBg(), school.getBg());
        if (schoolMapper.update(school) != 1) throw new com.miaoyu.barc.exception.ApiException(new com.miaoyu.barc.response.ErrorR().normal("保存失败"));
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, school));
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
        uuidIndex = 0,
        identity = UserIdentityEnum.MANAGER,
        targetPermission = PermissionConst.ADMINISTRATOR,
        isSuchElseRequire = false,
        isHasElseUpper = false // 基础资料管理按副馆长及以上层级授权，与管理端路由一致。
    )})
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<J> deleteSchool(String uuid, String id) {
        catalogMutationMapper.lock("foundation");
        SchoolModel existing = schoolMapper.selectByIdForUpdate(id);
        if (existing == null) {
            return ResponseEntity.ok(new J(1, "学园不存在", null));
        }
        // 所有 key 入 GC
        enqueueIfKey(existing.getLogo());
        enqueueIfKey(existing.getBeautify_logo());
        enqueueIfKey(existing.getBg());
        schoolMapper.childImageKeys(id).forEach(this::enqueueIfKey);
        schoolMapper.softDeleteStudentsBySchool(id);
        schoolMapper.softDeleteClubsBySchool(id);
        // 删除失败时与子项修改、回收队列一并回滚。
        if (schoolMapper.softDelete(id) != 1) throw com.miaoyu.barc.utils.RequestInput.invalid("学园删除失败");
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, null));
    }

    private void resolveImages(SchoolModel school) {
        school.setLogo_key(school.getLogo());
        school.setLogo_url(imageUrlResolver.resolve(school.getLogo()));
        school.setLogo(school.getLogo_url());
        school.setBeautify_logo_key(school.getBeautify_logo());
        school.setBeautify_logo_url(imageUrlResolver.resolve(school.getBeautify_logo()));
        school.setBeautify_logo(school.getBeautify_logo_url());
        school.setBg_key(school.getBg());
        school.setBg_url(imageUrlResolver.resolve(school.getBg()));
        school.setBg(school.getBg_url());
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
