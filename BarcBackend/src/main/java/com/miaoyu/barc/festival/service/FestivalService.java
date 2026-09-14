package com.miaoyu.barc.festival.service;

import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.background.mapper.BackgroundImageMapper;
import com.miaoyu.barc.festival.mapper.FestivalMapper;
import com.miaoyu.barc.festival.model.FestivalModel;
import com.miaoyu.barc.festival.utils.MonthDayRangeUtils;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.response.SuccessR;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.utils.GenerateUUID;
import com.miaoyu.barc.utils.J;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 节日管理服务（系统管理 → 节日管理）
 *
 * 权限：全部管理端接口统一使用「1匹配」鉴权（isHasElseUpper = true），
 * 目标权限为 ADVANCED_ADMINISTRATOR（馆长），即账号权限位中必须含馆长位。
 *
 * 核心规则：
 * 1、月份式 / 日期式统一落库为「某月某日 → 某月某日」；
 * 2、节日之间不允许重叠重合（关闭中的节日也占用区间，避免重新启用时出现冲突）；
 * 3、关闭（enabled = false）保留数据与背景图绑定，仅不参与用户端节日判定；
 * 4、删除节日时，绑定该节日的背景图自动划归「无节日」（festival = NULL），避免程序链路出错。
 */
@Service
public class FestivalService {

    /** 节日名称长度上限 */
    private static final int MAX_NAME_LENGTH = 64;

    @Autowired
    private FestivalMapper festivalMapper;
    @Autowired
    private BackgroundImageMapper backgroundImageMapper;

    // ============ 管理端（1匹配鉴权，馆长 ADVANCED_ADMINISTRATOR） ============

    /**
     * 管理端：节日列表（含已关闭），每项附带绑定背景图数量，便于删除前提示影响面
     */
    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.ADVANCED_ADMINISTRATOR,
            isSuchElseRequire = false,
            isHasElseUpper = true
    )})
    public ResponseEntity<J> listService(String uuid) {
        List<FestivalModel> list = festivalMapper.selectAll();
        // 逐项补上绑定数量：节日数量极少，逐条统计比写复杂聚合 SQL 更直观，也便于前端直接展示
        for (FestivalModel model : list) {
            model.setBound_backgrounds(backgroundImageMapper.countByFestival(model.getId()));
        }
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, list));
    }

    /**
     * 管理端：新增节日
     *
     * @param item 节日表单：name + 起始月日 + 结束月日
     */
    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.ADVANCED_ADMINISTRATOR,
            isSuchElseRequire = false,
            isHasElseUpper = true
    )})
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<J> createService(String uuid, FestivalItem item) {
        String nameError = validateName(item);
        if (nameError != null) {
            return ResponseEntity.ok(new J(1, nameError, null));
        }
        int[] range = normalizeRange(item);
        if (range == null) {
            return ResponseEntity.ok(new J(1, "节日日期不合法，请检查月份与日期", null));
        }
        // 名称唯一：避免管理端出现两个同名节日而无法区分
        if (festivalMapper.countByName(item.getName().trim(), null) > 0) {
            return ResponseEntity.ok(new J(1, "节日名称已存在: " + item.getName().trim(), null));
        }
        // 区间不得与任何其他节日重叠重合
        String overlapError = findOverlapError(null, range[0], range[1]);
        if (overlapError != null) {
            return ResponseEntity.ok(new J(1, overlapError, null));
        }

        FestivalModel model = new FestivalModel();
        model.setId(new GenerateUUID().getUuid32u());
        applyRange(model, item, range);
        model.setEnabled(true);
        model.setCreated_by(uuid);
        int rows = festivalMapper.insert(model);
        if (rows > 0) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(true, model));
        }
        return ResponseEntity.ok(new J(1, "节日新增失败", null));
    }

    /**
     * 管理端：修改节日（名称 + 日期区间）
     */
    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.ADVANCED_ADMINISTRATOR,
            isSuchElseRequire = false,
            isHasElseUpper = true
    )})
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<J> updateService(String uuid, String id, FestivalItem item) {
        FestivalModel existing = festivalMapper.selectById(id);
        if (existing == null) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        String nameError = validateName(item);
        if (nameError != null) {
            return ResponseEntity.ok(new J(1, nameError, null));
        }
        int[] range = normalizeRange(item);
        if (range == null) {
            return ResponseEntity.ok(new J(1, "节日日期不合法，请检查月份与日期", null));
        }
        if (festivalMapper.countByName(item.getName().trim(), id) > 0) {
            return ResponseEntity.ok(new J(1, "节日名称已存在: " + item.getName().trim(), null));
        }
        // 修改时排除自身后再做重叠校验
        String overlapError = findOverlapError(id, range[0], range[1]);
        if (overlapError != null) {
            return ResponseEntity.ok(new J(1, overlapError, null));
        }

        applyRange(existing, item, range);
        int rows = festivalMapper.update(existing);
        if (rows > 0) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(true, existing));
        }
        return ResponseEntity.ok(new J(1, "节日修改失败", null));
    }

    /**
     * 管理端：关闭 / 启用节日
     *
     * 关闭只影响用户端节日判定，不清空背景图上的节日绑定，重新启用即恢复原状。
     */
    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.ADVANCED_ADMINISTRATOR,
            isSuchElseRequire = false,
            isHasElseUpper = true
    )})
    public ResponseEntity<J> setEnabledService(String uuid, String id, boolean enabled) {
        FestivalModel existing = festivalMapper.selectById(id);
        if (existing == null) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        int rows = festivalMapper.updateEnabled(id, enabled);
        if (rows > 0) {
            existing.setEnabled(enabled);
            return ResponseEntity.ok(new ResourceR().resourceSuch(true, existing));
        }
        return ResponseEntity.ok(new J(1, "节日状态更新失败", null));
    }

    /**
     * 管理端：删除节日
     *
     * 同一个事务内先把绑定该节日的背景图划归「无节日」（festival = NULL），再逻辑删除节日，
     * 保证不会留下指向已删除节日的背景图，避免用户端节日取图链路出错。
     *
     * @return data 中返回 unboundBackgrounds，即本次被自动划归「无节日」的背景图数量
     */
    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.ADVANCED_ADMINISTRATOR,
            isSuchElseRequire = false,
            isHasElseUpper = true
    )})
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<J> deleteService(String uuid, String id) {
        FestivalModel existing = festivalMapper.selectById(id);
        if (existing == null) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        // 先解绑背景图，再删除节日：两步在同一事务内，避免中间态被读取到
        int unbound = backgroundImageMapper.clearFestival(id);
        int rows = festivalMapper.softDelete(id);
        if (rows > 0) {
            DeleteResult result = new DeleteResult();
            result.setId(id);
            result.setUnboundBackgrounds(unbound);
            return ResponseEntity.ok(new ResourceR().resourceSuch(true, result));
        }
        return ResponseEntity.ok(new J(1, "节日删除失败", null));
    }

    // ============ 公开接口（用户端背景图节日判定用，无需登录） ============

    /**
     * 用户端：启用中的节日列表（含日期区间），用于判定「当前时间属于哪个节日」
     */
    public J publicAllService() {
        return new ResourceR().resourceSuch(true, festivalMapper.selectAllEnabled());
    }

    // ============ 私有辅助 ============

    /** 名称校验，返回 null 表示通过 */
    private String validateName(FestivalItem item) {
        if (item == null || !StringUtils.hasText(item.getName())) {
            return "请填写节日名称";
        }
        if (item.getName().trim().length() > MAX_NAME_LENGTH) {
            return "节日名称不能超过 " + MAX_NAME_LENGTH + " 个字符";
        }
        return null;
    }

    /** 校验并规范化日期区间，返回 [startIndex, endIndex]，非法返回 null */
    private int[] normalizeRange(FestivalItem item) {
        if (item == null) {
            return null;
        }
        return MonthDayRangeUtils.normalizeRange(
                item.getStart_month(), item.getStart_day(), item.getEnd_month(), item.getEnd_day());
    }

    /** 把表单字段与派生索引写入模型 */
    private void applyRange(FestivalModel model, FestivalItem item, int[] range) {
        model.setName(item.getName().trim());
        model.setStart_month(item.getStart_month());
        model.setStart_day(item.getStart_day());
        model.setEnd_month(item.getEnd_month());
        model.setEnd_day(item.getEnd_day());
        model.setStart_index(range[0]);
        model.setEnd_index(range[1]);
    }

    /**
     * 查找与候选区间重叠的节日，返回中文错误信息；无重叠返回 null
     *
     * 判定采用逐日枚举（见 MonthDayRangeUtils），因此跨年区间（如新年 12-01 → 02-28）
     * 与年内区间（如元旦 01-01 → 01-02）的重叠也能被准确识别。
     *
     * 注意：已关闭的节日同样参与校验 —— 关闭只表示「暂不生效」，区间仍被占用，
     * 否则重新启用时会出现两个节日重叠的脏数据。
     */
    private String findOverlapError(String excludeId, int startIndex, int endIndex) {
        for (FestivalModel other : festivalMapper.selectAll()) {
            if (other == null || other.getId() == null) {
                continue;
            }
            if (excludeId != null && excludeId.equals(other.getId())) {
                continue;
            }
            if (other.getStart_index() == null || other.getEnd_index() == null) {
                continue;
            }
            if (MonthDayRangeUtils.isOverlapped(startIndex, endIndex, other.getStart_index(), other.getEnd_index())) {
                return "节日日期与「" + other.getName() + "」重叠重合，请调整日期区间";
            }
        }
        return null;
    }

    /** 管理端新增 / 修改节日的请求体：月份式与日期式共用同一套字段 */
    @Getter
    @Setter
    public static class FestivalItem {
        private String name;
        private Integer start_month;
        private Integer start_day;
        private Integer end_month;
        private Integer end_day;
    }

    /** 删除节日的结果：附带被自动划归「无节日」的背景图数量 */
    @Getter
    @Setter
    public static class DeleteResult {
        private String id;
        private Integer unboundBackgrounds;
    }
}
