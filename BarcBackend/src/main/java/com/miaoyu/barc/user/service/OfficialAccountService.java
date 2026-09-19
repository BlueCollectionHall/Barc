package com.miaoyu.barc.user.service;

import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.response.SuccessR;
import com.miaoyu.barc.response.UserR;
import com.miaoyu.barc.user.enumeration.OfficialAccountEnum;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.user.mapper.OfficialAccountMapper;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.model.OfficialAccountModel;
import com.miaoyu.barc.user.model.vo.OfficialAccountItemVo;
import com.miaoyu.barc.user.model.vo.UserInfoVo;
import com.miaoyu.barc.utils.J;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 官方业务指定账号服务（管理端「用户 → 官方账号」）。
 *
 * 本模块解决的问题：业务归属账号原先被写死为编译期常量，换账号必须改代码发版。
 * 现在改为「业务键 → 绑定账号 UUID」的可配置映射，后端按业务键动态取账号：
 *
 * 1、一个业务只允许绑定一个账号（business_key 为主键）；
 * 2、一个账号只能绑定一个业务（user_uuid 唯一索引 + 绑定前显式校验）；
 * 3、不提供「解绑」，只能「更换」；
 * 4、更换时在**同一事务**内把该业务此前的未认领作品一并迁到新账号；
 * 5、绑定行存在但账号已被删除时，读取链路返回 null（等同未配置），写链路据此阻断，
 *    从而保证 work.author 永远不会写入悬空 UUID。
 */
@Service
public class OfficialAccountService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private OfficialAccountMapper officialAccountMapper;
    @Autowired
    private UserArchiveMapper userArchiveMapper;

    /**
     * 读取某业务被指定的官方账号 UUID。
     *
     * 返回 null 有且仅有两种成因，调用方必须据此阻断写操作并提示管理员，
     * 不允许回落到任何硬编码 UUID：
     * ① 业务从未绑定；② 绑定账号已被删除。
     *
     * 本方法供作品归属链路内部调用，不对外暴露接口，因此不做鉴权。
     *
     * @param business 业务键枚举
     * @return 绑定账号 UUID；未配置时为 null
     */
    public String findBoundUuid(OfficialAccountEnum business) {
        if (business == null) {
            return null;
        }
        return officialAccountMapper.selectBoundUuid(business.name());
    }

    /**
     * 管理端：全部业务项的绑定状态列表（含未绑定项）。
     * 列表由「枚举全量」× 「绑定记录」合并而成，因此未绑定的业务也会出现，页面可强提示「未配置」。
     */
    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.ADVANCED_ADMINISTRATOR,
            isHasElseUpper = true,
            isSuchElseRequire = false)})
    public ResponseEntity<J> listService(String uuid) {
        Map<String, OfficialAccountModel> boundByKey = officialAccountMapper.selectAllWithAccount().stream()
                .collect(Collectors.toMap(OfficialAccountModel::getBusiness_key, Function.identity(), (a, b) -> a));

        List<OfficialAccountItemVo> items = new ArrayList<>();
        for (OfficialAccountEnum business : OfficialAccountEnum.values()) {
            items.add(toItem(business, boundByKey.get(business.name())));
        }
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, items));
    }

    /**
     * 管理端：绑定 / 更换某业务的官方账号。
     *
     * 事务边界覆盖「写绑定」与「换绑迁移」两步，避免出现「绑定已改、历史作品没迁」的中间态。
     *
     * @param uuid        操作者（馆长）UUID，由切面校验
     * @param businessKey 业务键，必须是 OfficialAccountEnum 的 name()
     * @param userUuid    要绑定的账号 UUID
     * @param remark      绑定备注，可为空
     * @return data 中返回 business_key / user_uuid / replaced_uuid / migrated_works
     */
    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.ADVANCED_ADMINISTRATOR,
            isHasElseUpper = true,
            isSuchElseRequire = false)})
    @Transactional
    public ResponseEntity<J> bindService(String uuid, String businessKey, String userUuid, String remark) {
        OfficialAccountEnum business = OfficialAccountEnum.fromName(businessKey);
        if (business == null) {
            return ResponseEntity.ok(new J(1, "未知的官方业务键：" + businessKey, null));
        }
        if (userUuid == null || userUuid.isBlank()) {
            return ResponseEntity.ok(new J(1, "请选择要绑定的账号", null));
        }
        String targetUuid = userUuid.trim();

        // 绑定对象必须是平台内真实存在的账号，否则等于把归属指向空号
        if (userArchiveMapper.selectByUuid(targetUuid) == null) {
            return ResponseEntity.ok(new UserR().noSuchUser());
        }
        // 一个账号只能绑定一个业务（与唯一索引双重保证，这里先给出可读提示）
        if (officialAccountMapper.countByUserUuid(targetUuid, business.name()) > 0) {
            return ResponseEntity.ok(new J(1, "该账号已被其它官方业务绑定，请更换其它账号", null));
        }

        // 取旧绑定：可能是不存在的账号（悬空绑定），此时仍要按旧 UUID 迁移历史作品
        OfficialAccountModel previous = officialAccountMapper.selectByBusinessKey(business.name());

        OfficialAccountModel model = new OfficialAccountModel();
        model.setBusiness_key(business.name());
        model.setUser_uuid(targetUuid);
        model.setRemark(remark);
        model.setUpdated_by(uuid);
        if (officialAccountMapper.upsert(model) <= 0) {
            return ResponseEntity.ok(new J(1, "官方账号绑定失败，请稍后重试", null));
        }

        int migratedWorks = 0;
        String replacedUuid = null;
        if (previous != null && !previous.getUser_uuid().equals(targetUuid)) {
            replacedUuid = previous.getUser_uuid();
            migratedWorks = officialAccountMapper.migrateUnclaimedAuthor(replacedUuid, targetUuid);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("business_key", business.name());
        result.put("user_uuid", targetUuid);
        result.put("replaced_uuid", replacedUuid);
        result.put("migrated_works", migratedWorks);
        return ResponseEntity.ok(new SuccessR().normal(result));
    }

    /**
     * 管理端：账号选择器搜索（UUID / 用户名 / 昵称，最多 15 条）。
     * 搜索词为空时直接返回空列表，避免 LIKE '%%' 拉出任意账号。
     */
    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.ADVANCED_ADMINISTRATOR,
            isHasElseUpper = true,
            isSuchElseRequire = false)})
    public ResponseEntity<J> searchUserService(String uuid, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(true, List.of()));
        }
        List<UserInfoVo> users = userArchiveMapper.searchByKeyword(keyword.trim());
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, users));
    }

    /**
     * 组装列表项：绑定行缺失 → 未配置；绑定行存在但账号缺失 → 悬空绑定。
     */
    private OfficialAccountItemVo toItem(OfficialAccountEnum business, OfficialAccountModel bound) {
        OfficialAccountItemVo item = new OfficialAccountItemVo();
        item.setBusiness_key(business.name());
        item.setDisplay_name(business.getDisplayName());
        item.setDescription(business.getDescription());

        boolean hasRow = bound != null;
        boolean dangling = hasRow && Boolean.TRUE.equals(bound.getDangling());
        item.setDangling(dangling);
        item.setBound(hasRow && !dangling);
        item.setUpdated_at(hasRow && bound.getUpdated_at() != null
                ? bound.getUpdated_at().format(TIME_FORMATTER) : "");
        if (hasRow) {
            item.setUser_uuid(bound.getUser_uuid());
            item.setNickname(bound.getNickname());
            item.setUsername(bound.getUsername());
            item.setAvatar(bound.getAvatar());
            item.setRemark(bound.getRemark());
            item.setUpdated_by_nickname(bound.getUpdated_by_nickname());
        }
        return item;
    }
}
