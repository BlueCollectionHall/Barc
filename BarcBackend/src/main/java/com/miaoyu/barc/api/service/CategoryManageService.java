package com.miaoyu.barc.api.service;

import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.api.mapper.CategoryMapper;
import com.miaoyu.barc.api.model.CategoryManageItem;
import com.miaoyu.barc.api.pojo.CategoryPojo;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.utils.J;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 作品分类管理服务（管理端「作品管理 → 作品分类管理」）
 *
 * <p>权限：与作品管理组其余接口完全一致 —— {@code MANAGER} 身份 + {@code SEC_MAINTAINER}
 * 的「1匹配」鉴权（{@code isHasElseUpper = true}）。</p>
 *
 * <p>已拍板的核心规则（详见 docs/superpowers/specs/2026-09-19-work-category-management-design.md）：</p>
 * 1、支持多层级维护；{@code level} 一律由服务端按 {@code parent_id} 派生，不接受前端传入；
 * 2、{@code id} 由管理员手填（{@code ^[a-z0-9_]{1,50}$}）且创建后不可修改
 *    —— work_category.category_id 的外键没有 ON UPDATE CASCADE，改主键会留下坏关联；
 * 3、编辑允许「移动到其他父分类」：同一事务内级联修正整棵子树的 level，并做防环校验；
 * 4、排序用上移 / 下移按钮：先把同级 sort 规范化为 1..n（存量数据存在大量重复 sort），再交换相邻两项；
 * 5、删除受约束：**有子分类或有作品关联的分类禁止删除**（外键是 ON DELETE CASCADE，
 *    放开会连坐删除整棵子树与作品分类关联）；
 * 6、不做 icon 与 is_enabled 的「停用」语义（这两个字段当前在用户端零消费）；
 * 7、不新增审计表，写操作仅记录服务端日志（操作者 uuid + 变更内容）。</p>
 */
@Service
public class CategoryManageService {

    private static final Logger log = LoggerFactory.getLogger(CategoryManageService.class);

    /** 分类名称长度上限：与列宽 category.name varchar(20) 对齐 */
    private static final int MAX_NAME_LENGTH = 20;

    /** 分类 id 长度上限：与列宽 category.id varchar(50) 对齐 */
    private static final int MAX_ID_LENGTH = 50;

    /** 分类 id 规范：存量 13 条全部是小写英文 + 数字 + 下划线（如 mobile_theme_huawei） */
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9_]{1," + MAX_ID_LENGTH + "}$");

    @Autowired
    private CategoryMapper categoryMapper;

    // ==================== 查询 ====================

    /**
     * 管理端：分类树列表
     *
     * <p>返回树形结构（children 嵌套），每个节点附带「直接作品数」与「直接子分类数」，
     * 供页面展示与删除按钮的可用性判定。</p>
     */
    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.SEC_MAINTAINER,
            isSuchElseRequire = false,
            isHasElseUpper = true
    )})
    public ResponseEntity<J> listService(String uuid) {
        List<CategoryManageItem> flat = categoryMapper.selectManageList();
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, buildTree(flat)));
    }

    // ==================== 新增 ====================

    /**
     * 管理端：新增分类
     *
     * @param uuid 操作者 uuid（由鉴权切面校验，同时用于服务端日志）
     * @param form 表单：id（手填）、name、parent_id（可空）、sort（可空，缺省排到同级末尾）
     */
    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.SEC_MAINTAINER,
            isSuchElseRequire = false,
            isHasElseUpper = true
    )})
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<J> createService(String uuid, CategoryForm form) {
        if (form == null) {
            return businessError("请填写分类信息");
        }
        String id = form.getId() == null ? null : form.getId().trim();
        String idError = validateId(id);
        if (idError != null) {
            return businessError(idError);
        }
        String name = form.getName() == null ? "" : form.getName().trim();
        String nameError = validateName(name);
        if (nameError != null) {
            return businessError(nameError);
        }
        String parentId = normalizeParentId(form.getParent_id());
        CategoryPojo parent = null;
        if (parentId != null) {
            parent = categoryMapper.selectById(parentId);
            if (parent == null) {
                return businessError("父分类不存在：" + parentId);
            }
        }
        // 同一父级下不允许重名（不同父级允许同名）
        if (categoryMapper.countSameNameUnderParent(name, parentId, null) > 0) {
            return businessError("同一层级下已存在同名分类：" + name);
        }

        CategoryPojo entity = new CategoryPojo();
        entity.setId(id);
        entity.setParent_id(parentId);
        entity.setName(name);
        // level 由服务端派生：顶级为 1，其余为父级层级 + 1
        entity.setLevel(parent == null ? 1 : parent.getLevel() + 1);
        entity.setSort(resolveSortForAppend(parentId, form.getSort()));

        if (categoryMapper.insert(entity) <= 0) {
            return businessError("分类新增失败");
        }
        log.info("作品分类新增：operator={}, id={}, name={}, parent_id={}, level={}, sort={}",
                uuid, entity.getId(), entity.getName(), entity.getParent_id(), entity.getLevel(), entity.getSort());
        // 新增的分类必然没有子分类与作品关联
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, toItem(entity, 0, 0)));
    }

    // ==================== 修改（含移动父分类） ====================

    /**
     * 管理端：修改分类
     *
     * <p>可改名称、排序、父分类。父分类变化即视为「移动」，会在同一事务内
     * 按层级差级联修正整棵子树的 level；并拒绝把分类挂到自身或自身后代下（防环）。</p>
     */
    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.SEC_MAINTAINER,
            isSuchElseRequire = false,
            isHasElseUpper = true
    )})
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<J> updateService(String uuid, String id, CategoryForm form) {
        CategoryPojo existing = id == null ? null : categoryMapper.selectById(id);
        if (existing == null) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        if (form == null) {
            return businessError("请填写分类信息");
        }
        String name = form.getName() == null ? "" : form.getName().trim();
        String nameError = validateName(name);
        if (nameError != null) {
            return businessError(nameError);
        }
        String parentId = normalizeParentId(form.getParent_id());
        boolean parentChanged = !equalsNullable(existing.getParent_id(), parentId);

        CategoryPojo newParent = null;
        if (parentId != null) {
            newParent = categoryMapper.selectById(parentId);
            if (newParent == null) {
                return businessError("父分类不存在：" + parentId);
            }
        }
        // 防环：不能把分类挂到自身或自身后代下，否则会形成数据库层面的自引用环，用户端建树将无限递归
        if (parentChanged && parentId != null) {
            if (parentId.equals(existing.getId())) {
                return businessError("不能把分类移动到自己下面");
            }
            for (CategoryPojo descendant : categoryMapper.selectSubtree(existing.getId())) {
                if (descendant.getId() != null && descendant.getId().equals(parentId)) {
                    return businessError("不能把分类移动到它自己的子分类下面");
                }
            }
        }
        // 同一父级下不允许重名（排除自身）
        if (categoryMapper.countSameNameUnderParent(name, parentId, existing.getId()) > 0) {
            return businessError("同一层级下已存在同名分类：" + name);
        }

        Integer oldLevel = existing.getLevel() == null ? 1 : existing.getLevel();
        Integer newLevel = parentId == null
                ? 1
                : (newParent.getLevel() == null ? 1 : newParent.getLevel() + 1);

        existing.setName(name);
        existing.setParent_id(parentId);
        existing.setLevel(newLevel);
        if (parentChanged) {
            // 移动后 append 到新父级末尾，避免沿用旧层级留下的 sort 值造成排序混乱
            existing.setSort(resolveSortForAppend(parentId, form.getSort()));
        } else if (form.getSort() != null) {
            existing.setSort(form.getSort());
        }

        if (categoryMapper.update(existing) <= 0) {
            return businessError("分类修改失败");
        }

        // 移动后按层级差修正整棵子树（子树为个位数，逐条更新更直观）
        if (parentChanged && !oldLevel.equals(newLevel)) {
            int delta = newLevel - oldLevel;
            List<CategoryPojo> subtree = categoryMapper.selectSubtree(existing.getId());
            for (CategoryPojo node : subtree) {
                if (node.getId() == null || node.getId().equals(existing.getId())) {
                    continue;
                }
                int nodeLevel = node.getLevel() == null ? newLevel : node.getLevel();
                categoryMapper.updateLevel(node.getId(), nodeLevel + delta);
            }
        }
        log.info("作品分类修改：operator={}, id={}, name={}, parent_id={}, level={}, sort={}, moved={}",
                uuid, existing.getId(), existing.getName(), existing.getParent_id(),
                existing.getLevel(), existing.getSort(), parentChanged);
        int boundWorks = categoryMapper.countWorks(existing.getId());
        int children = categoryMapper.countChildren(existing.getId());
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, toItem(existing, boundWorks, children)));
    }

    // ==================== 同级上移 / 下移 ====================

    /**
     * 管理端：同级上移 / 下移
     *
     * <p>存量数据的同级 sort 存在大量重复（例如 level 2 全部为 1），因此不能简单地
     * 「与相邻项交换 sort 值」——那样交换后两者相等，顺序仍然不确定。
     * 这里先按当前展示顺序把同级 sort 规范化为 1..n，再交换目标两项。</p>
     *
     * @param direction 仅接受 up / down
     */
    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.SEC_MAINTAINER,
            isSuchElseRequire = false,
            isHasElseUpper = true
    )})
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<J> moveSortService(String uuid, String id, String direction) {
        CategoryPojo existing = id == null ? null : categoryMapper.selectById(id);
        if (existing == null) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        boolean up;
        if ("up".equalsIgnoreCase(direction)) {
            up = true;
        } else if ("down".equalsIgnoreCase(direction)) {
            up = false;
        } else {
            return businessError("排序方向不合法，仅支持 up / down");
        }

        List<CategoryPojo> siblings = categoryMapper.selectByParentId(existing.getParent_id());
        if (siblings.isEmpty()) {
            return businessError("同级分类列表为空，无法排序");
        }
        // 第一步：规范化同级 sort 为 1..n（只在值发生变化时写库，减少无意义更新）
        for (int i = 0; i < siblings.size(); i++) {
            CategoryPojo sibling = siblings.get(i);
            int normalized = i + 1;
            if (sibling.getSort() == null || sibling.getSort() != normalized) {
                categoryMapper.updateSort(sibling.getId(), normalized);
            }
            sibling.setSort(normalized);
        }
        // 第二步：定位自身并找到相邻项
        int index = -1;
        for (int i = 0; i < siblings.size(); i++) {
            if (existing.getId().equals(siblings.get(i).getId())) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return businessError("未在所属层级中找到该分类：" + id);
        }
        int target = up ? index - 1 : index + 1;
        if (target < 0) {
            return businessError("该分类已经是同级的第一个，无法上移");
        }
        if (target >= siblings.size()) {
            return businessError("该分类已经是同级的最后一个，无法下移");
        }
        CategoryPojo neighbour = siblings.get(target);
        categoryMapper.updateSort(existing.getId(), neighbour.getSort());
        categoryMapper.updateSort(neighbour.getId(), index + 1);
        log.info("作品分类排序：operator={}, id={}, direction={}, from={}, to={}",
                uuid, existing.getId(), up ? "up" : "down", index + 1, target + 1);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, categoryMapper.selectByParentId(existing.getParent_id())));
    }

    // ==================== 删除 ====================

    /**
     * 管理端：删除分类
     *
     * <p>严格遵守已拍板约束：**有子分类或有作品关联时禁止删除**。
     * 原因是 category.parent_id 与 work_category.category_id 的外键均为 ON DELETE CASCADE，
     * 一旦放开，删除一个父分类会连坐删除整棵子树及其全部作品分类关联。</p>
     */
    @RequireUserAndPermissionAnno({ @RequireUserAndPermissionAnno.Check(
            identity = UserIdentityEnum.MANAGER,
            targetPermission = PermissionConst.SEC_MAINTAINER,
            isSuchElseRequire = false,
            isHasElseUpper = true
    )})
    @Transactional(rollbackFor = Exception.class)
    public ResponseEntity<J> deleteService(String uuid, String id) {
        CategoryPojo existing = id == null ? null : categoryMapper.selectById(id);
        if (existing == null) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        int children = categoryMapper.countChildren(id);
        if (children > 0) {
            return businessError("该分类下仍有 " + children + " 个子分类，请先删除或移动这些子分类");
        }
        int boundWorks = categoryMapper.countWorks(id);
        if (boundWorks > 0) {
            return businessError("该分类下仍有 " + boundWorks + " 个作品，请先调整这些作品的分类");
        }
        if (categoryMapper.deleteById(id) <= 0) {
            return businessError("分类删除失败");
        }
        log.info("作品分类删除：operator={}, id={}, name={}", uuid, existing.getId(), existing.getName());
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, existing.getId()));
    }

    // ==================== 私有辅助 ====================

    /** 业务错误统一返回：code = 1（与 FestivalService 等管理端服务保持一致的约定） */
    private ResponseEntity<J> businessError(String message) {
        return ResponseEntity.ok(new J(1, message, null));
    }

    /** 分类 id 校验，返回 null 表示通过 */
    private String validateId(String id) {
        if (!StringUtils.hasText(id)) {
            return "请填写分类 ID";
        }
        if (!ID_PATTERN.matcher(id).matches()) {
            return "分类 ID 只能使用小写英文、数字与下划线，长度不超过 " + MAX_ID_LENGTH + " 个字符";
        }
        if (categoryMapper.selectById(id) != null) {
            return "分类 ID 已存在：" + id;
        }
        return null;
    }

    /** 分类名称校验，返回 null 表示通过 */
    private String validateName(String name) {
        if (!StringUtils.hasText(name)) {
            return "请填写分类名称";
        }
        if (name.length() > MAX_NAME_LENGTH) {
            return "分类名称不能超过 " + MAX_NAME_LENGTH + " 个字符";
        }
        return null;
    }

    /** 空串与 null 统一视为「顶级分类」 */
    private String normalizeParentId(String parentId) {
        if (!StringUtils.hasText(parentId)) {
            return null;
        }
        return parentId.trim();
    }

    /** null 安全的字符串比较 */
    private boolean equalsNullable(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    /**
     * 决定新增 / 移动后的 sort：
     * 前端显式传了非负 sort 就用它，否则排到同级末尾（同级最大 sort + 1）。
     */
    private Integer resolveSortForAppend(String parentId, Integer requested) {
        if (requested != null && requested >= 0) {
            return requested;
        }
        int max = 0;
        for (CategoryPojo sibling : categoryMapper.selectByParentId(parentId)) {
            if (sibling.getSort() != null && sibling.getSort() > max) {
                max = sibling.getSort();
            }
        }
        return max + 1;
    }

    /**
     * 把扁平列表组装成树。
     *
     * <p>数据异常（父级不存在）时把该节点兜底为根节点，避免它在管理端「消失」而无法修复。
     * 输入已按 level / sort / id 升序，因此组装后同级顺序即为展示顺序。</p>
     */
    private List<CategoryManageItem> buildTree(List<CategoryManageItem> flat) {
        List<CategoryManageItem> roots = new ArrayList<>();
        if (flat == null || flat.isEmpty()) {
            return roots;
        }
        Map<String, CategoryManageItem> index = new LinkedHashMap<>();
        for (CategoryManageItem item : flat) {
            if (item != null && item.getId() != null) {
                index.put(item.getId(), item);
            }
        }
        for (CategoryManageItem item : flat) {
            if (item == null || item.getId() == null) {
                continue;
            }
            String parentId = item.getParent_id();
            CategoryManageItem parent = (parentId == null || parentId.isEmpty()) ? null : index.get(parentId);
            if (parent == null) {
                roots.add(item);
            } else {
                parent.addChild(item);
            }
        }
        return roots;
    }

    /** 实体 → 管理端列表项 */
    private CategoryManageItem toItem(CategoryPojo entity, int boundWorks, int childrenCount) {
        CategoryManageItem item = new CategoryManageItem();
        item.setId(entity.getId());
        item.setParent_id(entity.getParent_id());
        item.setName(entity.getName());
        item.setLevel(entity.getLevel());
        item.setSort(entity.getSort());
        item.setBound_works(boundWorks);
        item.setChildren_count(childrenCount);
        return item;
    }

    /**
     * 管理端新增 / 修改分类的请求体
     *
     * <p>id 只在新增时使用：分类主键创建后不可修改（外键没有 ON UPDATE CASCADE）。
     * parent_id 传 null 或空串表示顶级分类。</p>
     */
    @Getter
    @Setter
    public static class CategoryForm {
        private String id;
        private String name;
        private String parent_id;
        private Integer sort;
    }
}
