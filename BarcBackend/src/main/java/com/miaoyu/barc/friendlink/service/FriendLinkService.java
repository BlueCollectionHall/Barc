package com.miaoyu.barc.friendlink.service;

import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.api.service.CosGcService;
import com.miaoyu.barc.friendlink.mapper.FriendLinkMapper;
import com.miaoyu.barc.friendlink.model.FriendLinkModel;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.response.ResourceR;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 友情链接服务（管理端「系统 → 友情链接」维护，用户端首页展示）
 *
 * 权限：全部管理端接口统一「1匹配」鉴权（isHasElseUpper = true），目标权限 ADVANCED_ADMINISTRATOR（馆长），
 * 与同组的背景图 / 节日管理完全一致。公开接口走 @IgnoreAuth，不经过本类的权限注解。
 *
 * 上传流程（拍板 Q9，方案 A「先传后落」）：
 * 1、uploadService 只把 logo 传到 COS 并返回 objectKey，**不写数据库**；
 * 2、前端随新增 / 修改表单一并提交该 key，落库只发生在 createService / updateService；
 * 3、与背景图、学园/部团/学生三图完全同构；若用户上传后放弃保存，会产生 COS 孤儿对象，
 *    本期不做补偿（与既有链路一致），待「COS 孤儿对象巡检」独立需求统一治理。
 *
 * 排序（拍板 Q3，不做批量排序接口）：
 * moveService 在**同一事务**内交换相邻两条的 sort_order；已在首位上移 / 末位下移时返回失败提示，
 * 不做静默无操作，避免前端出现「点了没反应」的错觉。
 */
@Service
public class FriendLinkService {

    /** 图片大小上限 1MB（与背景图保持一致，便于前端复用同一套提示文案） */
    private static final long MAX_FILE_SIZE = 1024 * 1024;
    /** 桶内路径前缀：friendlink/logo/（注意不带前导斜杠，COS key 不能以 / 开头） */
    private static final String LOGO_PATH_PREFIX = "friendlink/logo/";
    /** 标题长度上限 */
    private static final int MAX_TITLE_LENGTH = 64;
    /** 链接长度上限 */
    private static final int MAX_URL_LENGTH = 512;
    /** 简介长度上限 */
    private static final int MAX_INTRO_LENGTH = 255;
    /** objectKey 长度上限（与 friend_link.logo_key 列宽一致） */
    private static final int MAX_LOGO_KEY_LENGTH = 255;
    /** 清洗后的「图片名」最大保留长度，避免 key 过长 */
    private static final int MAX_BASE_NAME_LENGTH = 50;
    /** 链接允许的协议前缀（大小写不敏感校验） */
    private static final String[] URL_PREFIXES = { "http://", "https://" };

    @Autowired
    private FriendLinkMapper friendLinkMapper;
    @Autowired
    private CosService cosService;
    @Autowired
    private CosConfig cosConfig;
    @Autowired
    private CosGcService cosGcService;
    @Autowired
    private ImageUrlResolver imageUrlResolver;

    // ============ 管理端（1匹配鉴权，馆长 ADVANCED_ADMINISTRATOR） ============

    /**
     * 上传 logo 到 COS，返回 objectKey（只上传不落库，见类注释的上传流程说明）
     *
     * 命名规则（需求指定）：friendlink/logo/{图片名}_{uuid第一段}_{秒级时间戳}.扩展名
     *
     * @param file 图片文件（≤1MB，content-type 必须以 image/ 开头）
     * @return code=0 时 data 为 objectKey 字符串
     */
    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.ADVANCED_ADMINISTRATOR,
            isSuchElseRequire = false,
            isHasElseUpper = true
    )})
    public ResponseEntity<J> uploadService(String uuid, MultipartFile file) {
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
        String objectKey = buildLogoKey(file);
        // 交给 COS 上传（uploadFileWithKey 不做二次重命名，完全按调用方给定的 key 落桶）
        return ResponseEntity.ok(cosService.uploadFileWithKey(file, objectKey, CosBucketConfigEnum.image));
    }

    /** 管理端列表：全部友链，按 sort_order 升序，逐条把 logo_key 签名成 logo_url */
    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.ADVANCED_ADMINISTRATOR,
            isSuchElseRequire = false,
            isHasElseUpper = true
    )})
    public ResponseEntity<J> listService(String uuid) {
        List<FriendLinkModel> list = friendLinkMapper.selectAll();
        list.forEach(this::resolveLogoUrl);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, list));
    }

    /** 管理端：新增友链（sort_order 取当前最大值 + 1） */
    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.ADVANCED_ADMINISTRATOR,
            isSuchElseRequire = false,
            isHasElseUpper = true
    )})
    public ResponseEntity<J> createService(String uuid, FriendLinkItem item) {
        String error = validate(item);
        if (error != null) {
            return ResponseEntity.ok(new J(1, error, null));
        }

        FriendLinkModel model = new FriendLinkModel();
        model.setId(new GenerateUUID().getUuid32u());
        model.setTitle(item.getTitle().trim());
        model.setUrl(item.getUrl().trim());
        model.setLogo_key(normalizeLogoKey(item.getLogo_key()));
        model.setIntro(normalizeIntro(item.getIntro()));
        model.setSort_order(friendLinkMapper.selectMaxSortOrder() + 1);
        model.setEnabled(true);
        model.setCreated_by(uuid);

        int rows = friendLinkMapper.insert(model);
        if (rows > 0) {
            resolveLogoUrl(model);
            return ResponseEntity.ok(new ResourceR().resourceSuch(true, model));
        }
        return ResponseEntity.ok(new J(1, "友链新增失败", null));
    }

    /**
     * 管理端：修改友链
     *
     * logo 变更时把旧对象交给 GC 队列异步清理（与背景图删除链路一致）。
     */
    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.ADVANCED_ADMINISTRATOR,
            isSuchElseRequire = false,
            isHasElseUpper = true
    )})
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<J> updateService(String uuid, String id, FriendLinkItem item) {
        FriendLinkModel existing = friendLinkMapper.selectById(id);
        if (existing == null) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        String error = validate(item);
        if (error != null) {
            return ResponseEntity.ok(new J(1, error, null));
        }

        String nextLogoKey = normalizeLogoKey(item.getLogo_key());
        String oldLogoKey = existing.getLogo_key();
        // 只有「原来有图」且「换成了另一个 key」时，旧对象才需要清理
        if (StringUtils.hasText(oldLogoKey) && !oldLogoKey.equals(nextLogoKey)) {
            cosGcService.enqueue(cosConfig.getImage().getBucketName(), oldLogoKey);
        }

        FriendLinkModel update = new FriendLinkModel();
        update.setId(id);
        update.setTitle(item.getTitle().trim());
        update.setUrl(item.getUrl().trim());
        update.setLogo_key(nextLogoKey);
        update.setIntro(normalizeIntro(item.getIntro()));

        int rows = friendLinkMapper.update(update);
        if (rows > 0) {
            // 回显最新的完整记录（sort_order / created_at 等由数据库维护，不能只回传表单字段）
            FriendLinkModel latest = friendLinkMapper.selectById(id);
            if (latest == null) {
                latest = update;
            }
            resolveLogoUrl(latest);
            return ResponseEntity.ok(new ResourceR().resourceSuch(true, latest));
        }
        return ResponseEntity.ok(new J(1, "友链修改失败", null));
    }

    /**
     * 管理端：上移 / 下移（同一事务内与相邻友链交换 sort_order）
     *
     * @param direction 只能是 up / down；已在首位上移或末位下移时返回失败提示
     */
    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.ADVANCED_ADMINISTRATOR,
            isSuchElseRequire = false,
            isHasElseUpper = true
    )})
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<J> moveService(String uuid, String id, String direction) {
        boolean up;
        if ("up".equalsIgnoreCase(direction)) {
            up = true;
        } else if ("down".equalsIgnoreCase(direction)) {
            up = false;
        } else {
            return ResponseEntity.ok(new J(1, "移动方向不合法: " + direction, null));
        }

        FriendLinkModel current = friendLinkMapper.selectById(id);
        if (current == null) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }

        int currentOrder = current.getSort_order() != null ? current.getSort_order() : 0;
        FriendLinkModel neighbor = up
                ? friendLinkMapper.selectPrevBySortOrder(currentOrder)
                : friendLinkMapper.selectNextBySortOrder(currentOrder);
        if (neighbor == null) {
            return ResponseEntity.ok(new J(1, up ? "该友链已经在最前" : "该友链已经在最后", null));
        }

        int neighborOrder = neighbor.getSort_order() != null ? neighbor.getSort_order() : 0;
        friendLinkMapper.updateSortOrder(current.getId(), neighborOrder);
        friendLinkMapper.updateSortOrder(neighbor.getId(), currentOrder);

        // 回传交换后的完整列表，前端直接替换本地状态，避免再发一次列表请求
        List<FriendLinkModel> list = friendLinkMapper.selectAll();
        list.forEach(this::resolveLogoUrl);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, list));
    }

    /** 管理端：删除友链（软删记录 + logo 对象交 GC 队列异步清理） */
    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.ADVANCED_ADMINISTRATOR,
            isSuchElseRequire = false,
            isHasElseUpper = true
    )})
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<J> deleteService(String uuid, String id) {
        FriendLinkModel existing = friendLinkMapper.selectById(id);
        if (existing == null) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        if (StringUtils.hasText(existing.getLogo_key())) {
            cosGcService.enqueue(cosConfig.getImage().getBucketName(), existing.getLogo_key());
        }
        int rows = friendLinkMapper.softDelete(id);
        return ResponseEntity.ok(new ResourceR().resourceSuch(rows > 0, null));
    }

    // ============ 公开接口（用户端，无需登录） ============

    /**
     * 用户端：全部友链（按 sort_order 升序，logo 已解析为签名 URL）
     *
     * 只返回用户端渲染所需的四项，不暴露 id / logo_key / created_by 等内部字段。
     */
    public J publicAllService() {
        List<FriendLinkModel> list = friendLinkMapper.selectAllForPublic();
        List<PublicItem> items = new ArrayList<>();
        for (FriendLinkModel model : list) {
            PublicItem item = new PublicItem();
            item.setTitle(model.getTitle());
            item.setUrl(model.getUrl());
            item.setIntro(model.getIntro());
            item.setLogo_url(imageUrlResolver.resolve(model.getLogo_key()));
            items.add(item);
        }
        return new ResourceR().resourceSuch(true, items);
    }

    // ============ 私有辅助 ============

    /**
     * 生成 logo 的 COS objectKey：friendlink/logo/{图片名}_{uuid第一段}_{秒级时间戳}.扩展名
     *
     * 说明：
     * 1、前缀不带前导斜杠 —— PutObjectRequest 的 key 不能以 / 开头，否则会多出一层空目录；
     * 2、「uuid 第一段」取 36 位 UUID 按 "-" 切分的第一段（8 位大写十六进制），语义与需求原话严格一致；
     * 3、扩展名缺失时不拼 "."（与 BackgroundImageService 的处理保持一致）。
     */
    private String buildLogoKey(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        String extension = StringUtils.getFilenameExtension(originalFilename);
        String baseName = sanitizeBaseName(originalFilename);
        String uuidSeg = new GenerateUUID().getUuid36l().split("-")[0].toUpperCase();
        long epochSec = System.currentTimeMillis() / 1000;
        return LOGO_PATH_PREFIX + baseName + "_" + uuidSeg + "_" + epochSec
                + (extension != null ? "." + extension : "");
    }

    /**
     * 清洗「图片名」：取原文件名去掉扩展名后，去掉路径分隔符与控制字符，
     * 只保留字母 / 数字 / 下划线 / 连字符 / 点 / 中日韩文字，其余一律折叠为下划线；
     * 清洗后为空则回落为 "logo"，并截断到 MAX_BASE_NAME_LENGTH。
     */
    private String sanitizeBaseName(String originalFilename) {
        if (originalFilename == null) {
            return "logo";
        }
        // 先去掉可能存在的目录部分（部分浏览器会带完整路径）
        String name = originalFilename.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        // 去掉扩展名（扩展名单独拼接到 key 末尾）
        String extension = StringUtils.getFilenameExtension(name);
        if (extension != null && name.length() > extension.length()) {
            name = name.substring(0, name.length() - extension.length() - 1);
        }
        // 白名单过滤 + 折叠连续下划线
        String cleaned = name.replaceAll("[^\\p{Alnum}\\p{IsHan}._-]+", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^[_.]+|[_.]+$", "");
        if (cleaned.isEmpty()) {
            return "logo";
        }
        return cleaned.length() > MAX_BASE_NAME_LENGTH ? cleaned.substring(0, MAX_BASE_NAME_LENGTH) : cleaned;
    }

    /** 表单校验：返回 null 表示通过，否则返回中文错误文案 */
    private String validate(FriendLinkItem item) {
        if (item == null) {
            return "友链内容为空";
        }
        if (!StringUtils.hasText(item.getTitle())) {
            return "请填写友链标题";
        }
        if (item.getTitle().trim().length() > MAX_TITLE_LENGTH) {
            return "友链标题长度不能超过 " + MAX_TITLE_LENGTH + " 个字符";
        }
        if (!StringUtils.hasText(item.getUrl())) {
            return "请填写友链链接";
        }
        String url = item.getUrl().trim();
        if (url.length() > MAX_URL_LENGTH) {
            return "友链链接长度不能超过 " + MAX_URL_LENGTH + " 个字符";
        }
        // 链接必须带 http:// 或 https:// 前缀（大小写不敏感），不做域名真实性探测
        String lower = url.toLowerCase();
        boolean prefixOk = false;
        for (String prefix : URL_PREFIXES) {
            if (lower.startsWith(prefix)) {
                prefixOk = true;
                break;
            }
        }
        if (!prefixOk) {
            return "友链链接必须以 http:// 或 https:// 开头";
        }
        String intro = item.getIntro();
        if (intro != null && intro.trim().length() > MAX_INTRO_LENGTH) {
            return "友链简介长度不能超过 " + MAX_INTRO_LENGTH + " 个字符";
        }
        String logoKey = normalizeLogoKey(item.getLogo_key());
        if (logoKey != null) {
            if (logoKey.length() > MAX_LOGO_KEY_LENGTH) {
                return "logo 路径长度不能超过 " + MAX_LOGO_KEY_LENGTH + " 个字符";
            }
            // 只接受本模块上传路径，防止把其它模块（背景图 / 头像 / 作品图）的对象写进来
            if (!logoKey.startsWith(LOGO_PATH_PREFIX)) {
                return "logo 路径不合法，必须以 " + LOGO_PATH_PREFIX + " 开头";
            }
        }
        return null;
    }

    /** logo_key 归一化：空白视为 null（未上传），否则 trim */
    private String normalizeLogoKey(String logoKey) {
        return StringUtils.hasText(logoKey) ? logoKey.trim() : null;
    }

    /** 简介归一化：空白视为 null，否则 trim */
    private String normalizeIntro(String intro) {
        return StringUtils.hasText(intro) ? intro.trim() : null;
    }

    /** 把 logo_key 签名成展示用 URL（key 为空时 logo_url 为 null） */
    private void resolveLogoUrl(FriendLinkModel model) {
        model.setLogo_url(imageUrlResolver.resolve(model.getLogo_key()));
    }

    /** 新增 / 修改的请求体 */
    @Getter
    @Setter
    public static class FriendLinkItem {
        private String title;
        private String url;
        /** COS objectKey（由 uploadService 返回），可空 */
        private String logo_key;
        private String intro;
    }

    /** 公开接口返回的单条友链（不含任何内部字段） */
    @Getter
    @Setter
    public static class PublicItem {
        private String title;
        private String url;
        private String intro;
        /** 签名后的完整 logo URL，未设置 logo 时为 null */
        private String logo_url;
    }
}
