package com.miaoyu.barc.background.service;

import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.api.service.CosGcService;
import com.miaoyu.barc.background.enumeration.BackgroundFestivalEnum;
import com.miaoyu.barc.background.enumeration.BackgroundModuleEnum;
import com.miaoyu.barc.background.enumeration.BackgroundTimePeriodEnum;
import com.miaoyu.barc.background.mapper.BackgroundImageMapper;
import com.miaoyu.barc.background.model.BackgroundImageModel;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.response.SuccessR;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.utils.GenerateUUID;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.tencent.cos.CosBucketConfigEnum;
import com.miaoyu.barc.utils.tencent.cos.CosConfig;
import com.miaoyu.barc.utils.tencent.cos.CosService;
import com.miaoyu.barc.utils.tencent.cos.ImageUrlResolver;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BackgroundImageService {

    /** 图片大小上限 1MB */
    private static final long MAX_FILE_SIZE = 1024 * 1024;
    /** 桶内路径前缀：client/bg/{module}/ */
    private static final String BG_PATH_PREFIX = "client/bg/";

    @Autowired
    private BackgroundImageMapper backgroundImageMapper;
    @Autowired
    private CosService cosService;
    @Autowired
    private CosConfig cosConfig;
    @Autowired
    private CosGcService cosGcService;
    @Autowired
    private ImageUrlResolver imageUrlResolver;

    // ============ 管理端（1匹配鉴权，馆长 ADVANCED_ADMINISTRATOR） ============

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.ADVANCED_ADMINISTRATOR,
            isSuchElseRequire = false,
            isHasElseUpper = true
    )})
    public ResponseEntity<J> modulesService(String uuid) {
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, BackgroundModuleEnum.options()));
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.ADVANCED_ADMINISTRATOR,
            isSuchElseRequire = false,
            isHasElseUpper = true
    )})
    public ResponseEntity<J> uploadService(String uuid, MultipartFile file, String module, String timePeriod, String festival) {
        BackgroundModuleEnum moduleEnum = BackgroundModuleEnum.fromCode(module);
        if (moduleEnum == null) {
            return ResponseEntity.ok(new J(1, "背景模块不合法: " + module, null));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.ok(new J(1, "请选择要上传的图片", null));
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.ok(new J(1, "图片大小不能超过 1MB", null));
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.ok(new J(1, "仅支持上传图片文件", null));
        }
        // 校验并归一化场景标签（时段 + 节日，空串视为任意）
        String normTime = normalizeSceneValue(timePeriod);
        String normFestival = normalizeSceneValue(festival);
        if (normTime != null && BackgroundTimePeriodEnum.fromCode(normTime) == null) {
            return ResponseEntity.ok(new J(1, "时段不合法: " + timePeriod, null));
        }
        if (normFestival != null && BackgroundFestivalEnum.fromCode(normFestival) == null) {
            return ResponseEntity.ok(new J(1, "节日不合法: " + festival, null));
        }
        // 文件名：UUID第一段 - 到秒的时间戳 . 扩展名
        String uuidSeg = new GenerateUUID().getUuid32u().substring(0, 8);
        String ext = StringUtils.getFilenameExtension(file.getOriginalFilename());
        long epochSec = System.currentTimeMillis() / 1000;
        String objectKey = BG_PATH_PREFIX + moduleEnum.getCode() + "/" + uuidSeg + "-" + epochSec + (ext != null ? "." + ext : "");

        J upload = cosService.uploadFileWithKey(file, objectKey, CosBucketConfigEnum.image);
        if (upload.getCode() != 0) {
            return ResponseEntity.ok(upload);
        }

        BackgroundImageModel model = new BackgroundImageModel();
        model.setId(new GenerateUUID().getUuid32u());
        model.setModule(moduleEnum.getCode());
        model.setObject_key(objectKey);
        model.setFilename(file.getOriginalFilename());
        model.setSort_order(backgroundImageMapper.selectMaxSortOrder(moduleEnum.getCode()) + 1);
        model.setEnabled(true);
        model.setTime_period(normTime);
        model.setFestival(normFestival);
        model.setCreated_by(uuid);
        int insert = backgroundImageMapper.insert(model);
        if (insert > 0) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(true, model));
        }
        return ResponseEntity.ok(new J(1, "背景图记录保存失败", null));
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.ADVANCED_ADMINISTRATOR,
            isSuchElseRequire = false,
            isHasElseUpper = true
    )})
    public ResponseEntity<J> listService(String uuid, String module) {
        BackgroundModuleEnum moduleEnum = BackgroundModuleEnum.fromCode(module);
        if (moduleEnum == null) {
            return ResponseEntity.ok(new J(1, "背景模块不合法: " + module, null));
        }
        List<BackgroundImageModel> list = backgroundImageMapper.selectByModule(moduleEnum.getCode());
        list.forEach(this::resolveDisplayUrl);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, list));
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.ADVANCED_ADMINISTRATOR,
            isSuchElseRequire = false,
            isHasElseUpper = true
    )})
    public ResponseEntity<J> reorderService(String uuid, List<OrderItem> items) {
        if (items != null) {
            for (OrderItem item : items) {
                if (item == null || item.getId() == null) {
                    continue;
                }
                backgroundImageMapper.updateSortOrder(item.getId(), item.getSort_order() != null ? item.getSort_order() : 0);
            }
        }
        return ResponseEntity.ok(new SuccessR().normal("排序已更新"));
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.ADVANCED_ADMINISTRATOR,
            isSuchElseRequire = false,
            isHasElseUpper = true
    )})
    public ResponseEntity<J> setEnabledService(String uuid, String id, boolean enabled) {
        BackgroundImageModel existing = backgroundImageMapper.selectById(id);
        if (existing == null) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        int rows = backgroundImageMapper.updateEnabled(id, enabled);
        if (rows > 0) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(true, existing));
        }
        return ResponseEntity.ok(new J(1, "更新失败", null));
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.ADVANCED_ADMINISTRATOR,
            isSuchElseRequire = false,
            isHasElseUpper = true
    )})
    public ResponseEntity<J> updateSceneService(String uuid, String id, String timePeriod, String festival) {
        BackgroundImageModel existing = backgroundImageMapper.selectById(id);
        if (existing == null) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        // 空串归一化为 NULL（表示任意时段 / 非节日），非空则校验合法性
        String normTime = normalizeSceneValue(timePeriod);
        String normFestival = normalizeSceneValue(festival);
        if (normTime != null && BackgroundTimePeriodEnum.fromCode(normTime) == null) {
            return ResponseEntity.ok(new J(1, "时段不合法: " + timePeriod, null));
        }
        if (normFestival != null && BackgroundFestivalEnum.fromCode(normFestival) == null) {
            return ResponseEntity.ok(new J(1, "节日不合法: " + festival, null));
        }
        int rows = backgroundImageMapper.updateScene(id, normTime, normFestival);
        existing.setTime_period(normTime);
        existing.setFestival(normFestival);
        if (rows > 0) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(true, existing));
        }
        return ResponseEntity.ok(new J(1, "更新失败", null));
    }

    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.ADVANCED_ADMINISTRATOR,
            isSuchElseRequire = false,
            isHasElseUpper = true
    )})
    public ResponseEntity<J> deleteService(String uuid, String id) {
        BackgroundImageModel existing = backgroundImageMapper.selectById(id);
        if (existing == null) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        // 交给 GC 任务清理 COS 对象，避免阻塞
        cosGcService.enqueue(cosConfig.getImage().getBucketName(), existing.getObject_key());
        int rows = backgroundImageMapper.softDelete(id);
        return ResponseEntity.ok(new ResourceR().resourceSuch(rows > 0, null));
    }

    // ============ 公开接口（用户端，无需登录） ============

    /**
     * 返回所有「启用」的背景图，按 module 分组，值已解析为签名 URL 并携带场景标签
     */
    public J publicAllService() {
        List<BackgroundImageModel> list = backgroundImageMapper.selectAllEnabled();
        Map<String, List<PublicItem>> grouped = new LinkedHashMap<>();
        for (BackgroundImageModel model : list) {
            PublicItem item = toPublicItem(model);
            if (item == null) {
                continue;
            }
            grouped.computeIfAbsent(model.getModule(), key -> new ArrayList<>()).add(item);
        }
        return new ResourceR().resourceSuch(true, grouped);
    }

    /** 返回某个 module 的启用背景图列表（已解析签名 URL 并携带场景标签） */
    public J publicByModuleService(String module) {
        BackgroundModuleEnum moduleEnum = BackgroundModuleEnum.fromCode(module);
        if (moduleEnum == null) {
            return new ResourceR().resourceSuch(false, null);
        }
        List<BackgroundImageModel> list = backgroundImageMapper.selectEnabledByModule(moduleEnum.getCode());
        List<PublicItem> items = new ArrayList<>();
        for (BackgroundImageModel model : list) {
            PublicItem item = toPublicItem(model);
            if (item != null) {
                items.add(item);
            }
        }
        return new ResourceR().resourceSuch(true, items);
    }

    // ============ 私有辅助 ============

    /** 将模型转换为前端展示用的公开项（URL + 时段 + 节日），签名失败返回 null */
    private PublicItem toPublicItem(BackgroundImageModel model) {
        String url = imageUrlResolver.resolve(model.getObject_key());
        if (url == null) {
            return null;
        }
        PublicItem item = new PublicItem();
        item.setUrl(url);
        item.setTime_period(model.getTime_period());
        item.setFestival(model.getFestival());
        return item;
    }

    /** 场景标签归一化：空串 / 纯空白视为 NULL（表示任意），否则 trim */
    private String normalizeSceneValue(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void resolveDisplayUrl(BackgroundImageModel model) {
        model.setUrl(imageUrlResolver.resolve(model.getObject_key()));
    }

    @Getter
    @Setter
    public static class OrderItem {
        private String id;
        private Integer sort_order;
    }

    /** 管理端更新场景标签的请求体（时段 + 节日，均可空） */
    @Getter
    @Setter
    public static class SceneItem {
        private String time_period;
        private String festival;
    }

    /** 公开接口返回的单条背景图（URL + 时段 + 节日） */
    @Getter
    @Setter
    public static class PublicItem {
        private String url;
        private String time_period;
        private String festival;
    }
}
