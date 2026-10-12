package com.miaoyu.barc.api.service;

import static org.mockito.Mockito.lenient;

import com.miaoyu.barc.api.mapper.CategoryMapper;
import com.miaoyu.barc.api.model.CategoryManageItem;
import com.miaoyu.barc.api.pojo.CategoryPojo;
import com.miaoyu.barc.utils.J;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 作品分类管理服务单元测试
 *
 * <p>覆盖：树形列表（含统计与父级缺失兜底）、新增（id / 名称 / 父级 / 重名 / 排序缺省）、
 * 修改（名称、移动父分类的防环与子树层级级联、重名排除自身）、同级上移下移（含存量 sort 重复的规范化）、
 * 删除（有子分类 / 有作品关联时拒绝）。</p>
 */
@ExtendWith(MockitoExtension.class)
class CategoryManageServiceTest {

    @Mock
    private CategoryMapper categoryMapper;

    @Mock private com.miaoyu.barc.api.mapper.CatalogMutationMapper catalogMutationMapper;

    @org.junit.jupiter.api.BeforeEach void successfulNestedWrites() {
        lenient().when(categoryMapper.updateLevel(anyString(), any())).thenReturn(1);
        lenient().when(categoryMapper.updateSort(anyString(), any())).thenReturn(1);
    }

    @InjectMocks
    private CategoryManageService categoryManageService;

    // ==================== 辅助构造 ====================

    private CategoryPojo pojo(String id, String parentId, String name, int level, int sort) {
        CategoryPojo pojo = new CategoryPojo();
        pojo.setId(id);
        pojo.setParent_id(parentId);
        pojo.setName(name);
        pojo.setLevel(level);
        pojo.setSort(sort);
        return pojo;
    }

    private CategoryManageItem item(String id, String parentId, String name, int level, int sort,
                                    int boundWorks, int childrenCount) {
        CategoryManageItem item = new CategoryManageItem();
        item.setId(id);
        item.setParent_id(parentId);
        item.setName(name);
        item.setLevel(level);
        item.setSort(sort);
        item.setBound_works(boundWorks);
        item.setChildren_count(childrenCount);
        return item;
    }

    private CategoryManageService.CategoryForm form(String id, String name, String parentId, Integer sort) {
        CategoryManageService.CategoryForm form = new CategoryManageService.CategoryForm();
        form.setId(id);
        form.setName(name);
        form.setParent_id(parentId);
        form.setSort(sort);
        return form;
    }

    @SuppressWarnings("unchecked")
    private List<CategoryManageItem> treeOf(ResponseEntity<J> response) {
        return (List<CategoryManageItem>) response.getBody().getData();
    }

    // ==================== 列表 ====================

    @Test
    @DisplayName("列表 - 扁平数据组装为层级树并保留统计字段")
    void list_buildsTree() {
        when(categoryMapper.selectManageList()).thenReturn(List.of(
                item("game", null, "游戏", 1, 4, 2, 1),
                item("mod", "game", "模组", 2, 1, 0, 0)));

        ResponseEntity<J> response = categoryManageService.listService("admin-uuid");

        assertEquals(0, response.getBody().getCode());
        List<CategoryManageItem> roots = treeOf(response);
        assertEquals(1, roots.size());
        CategoryManageItem root = roots.get(0);
        assertEquals("game", root.getId());
        assertEquals(2, root.getBound_works());
        assertEquals(1, root.getChildren_count());
        assertEquals(1, root.getChildren().size());
        assertEquals("mod", root.getChildren().get(0).getId());
    }

    @Test
    @DisplayName("列表 - 父级缺失时兜底为根节点，避免分类在管理端消失")
    void list_missingParentFallsBackToRoot() {
        when(categoryMapper.selectManageList()).thenReturn(List.of(
                item("orphan", "not_exists", "孤儿分类", 2, 1, 0, 0)));

        List<CategoryManageItem> roots = treeOf(categoryManageService.listService("admin-uuid"));

        assertEquals(1, roots.size());
        assertEquals("orphan", roots.get(0).getId());
        assertNull(roots.get(0).getChildren());
    }

    @Test
    @DisplayName("列表 - 空结果返回空树")
    void list_empty() {
        when(categoryMapper.selectManageList()).thenReturn(Collections.emptyList());

        List<CategoryManageItem> roots = treeOf(categoryManageService.listService("admin-uuid"));

        assertTrue(roots.isEmpty());
    }

    // ==================== 新增 ====================

    @Test
    @DisplayName("新增 - 顶级分类：level 派生为 1，sort 缺省排到同级末尾")
    void create_topCategory_appendsSort() {
        when(categoryMapper.selectById("new_top")).thenReturn(null);
        when(categoryMapper.countSameNameUnderParent("新分类", null, null)).thenReturn(0);
        when(categoryMapper.selectByParentId(null)).thenReturn(List.of(pojo("game", null, "游戏", 1, 7)));
        when(categoryMapper.insert(any(CategoryPojo.class))).thenReturn(1);

        ResponseEntity<J> response =
                categoryManageService.createService("admin-uuid", form("new_top", "新分类", null, null));

        assertEquals(0, response.getBody().getCode());
        CategoryManageItem saved = (CategoryManageItem) response.getBody().getData();
        assertEquals("new_top", saved.getId());
        assertEquals(1, saved.getLevel());
        assertEquals(8, saved.getSort());
        assertNull(saved.getParent_id());
        assertEquals(0, saved.getBound_works());
        assertEquals(0, saved.getChildren_count());
    }

    @Test
    @DisplayName("新增 - 子分类：level = 父级 + 1，sort 用显式值")
    void create_childCategory_usesParentLevel() {
        when(categoryMapper.selectById("new_child")).thenReturn(null);
        when(categoryMapper.selectById("beautify")).thenReturn(pojo("beautify", null, "美化", 1, 2));
        when(categoryMapper.countSameNameUnderParent("新子类", "beautify", null)).thenReturn(0);
        when(categoryMapper.insert(any(CategoryPojo.class))).thenReturn(1);

        ResponseEntity<J> response =
                categoryManageService.createService("admin-uuid", form("new_child", "新子类", "beautify", 5));

        assertEquals(0, response.getBody().getCode());
        CategoryManageItem saved = (CategoryManageItem) response.getBody().getData();
        assertEquals(2, saved.getLevel());
        assertEquals(5, saved.getSort());
        assertEquals("beautify", saved.getParent_id());
    }

    @Test
    @DisplayName("新增 - 空表单被拒绝")
    void create_nullForm() {
        ResponseEntity<J> response = categoryManageService.createService("admin-uuid", null);

        assertEquals(1, response.getBody().getCode());
        assertEquals("请填写分类信息", response.getBody().getMsg());
    }

    @Test
    @DisplayName("新增 - id 为空 / 格式非法 / 已存在均被拒绝")
    void create_invalidId() {
        ResponseEntity<J> blank = categoryManageService.createService("admin-uuid", form("  ", "名称", null, null));
        assertEquals(1, blank.getBody().getCode());
        assertEquals("请填写分类 ID", blank.getBody().getMsg());

        ResponseEntity<J> illegal =
                categoryManageService.createService("admin-uuid", form("Bad-ID", "名称", null, null));
        assertEquals(1, illegal.getBody().getCode());
        assertTrue(illegal.getBody().getMsg().contains("小写英文"));

        when(categoryMapper.selectById("3d_model")).thenReturn(pojo("3d_model", null, "模型", 1, 1));
        ResponseEntity<J> duplicated =
                categoryManageService.createService("admin-uuid", form("3d_model", "名称", null, null));
        assertEquals(1, duplicated.getBody().getCode());
        assertTrue(duplicated.getBody().getMsg().contains("分类 ID 已存在"));
    }

    @Test
    @DisplayName("新增 - 名称必填且不超过 20 字")
    void create_invalidName() {
        when(categoryMapper.selectById("ok_id")).thenReturn(null);
        ResponseEntity<J> blank = categoryManageService.createService("admin-uuid", form("ok_id", "   ", null, null));
        assertEquals(1, blank.getBody().getCode());
        assertEquals("请填写分类名称", blank.getBody().getMsg());

        String tooLong = "一".repeat(21);
        ResponseEntity<J> longName =
                categoryManageService.createService("admin-uuid", form("ok_id", tooLong, null, null));
        assertEquals(1, longName.getBody().getCode());
        assertTrue(longName.getBody().getMsg().contains("不能超过 20"));
    }

    @Test
    @DisplayName("新增 - 父分类不存在被拒绝")
    void create_parentMissing() {
        when(categoryMapper.selectById("new_child")).thenReturn(null);
        when(categoryMapper.selectById("ghost")).thenReturn(null);

        ResponseEntity<J> response =
                categoryManageService.createService("admin-uuid", form("new_child", "名称", "ghost", 1));

        assertEquals(1, response.getBody().getCode());
        assertTrue(response.getBody().getMsg().contains("父分类不存在"));
    }

    @Test
    @DisplayName("新增 - 同一父级下重名被拒绝")
    void create_duplicateName() {
        when(categoryMapper.selectById("new_child")).thenReturn(null);
        when(categoryMapper.selectById("game")).thenReturn(pojo("game", null, "游戏", 1, 4));
        when(categoryMapper.countSameNameUnderParent("模组", "game", null)).thenReturn(1);

        ResponseEntity<J> response =
                categoryManageService.createService("admin-uuid", form("new_child", "模组", "game", 1));

        assertEquals(1, response.getBody().getCode());
        assertTrue(response.getBody().getMsg().contains("已存在同名分类"));
        verify(categoryMapper, never()).insert(any(CategoryPojo.class));
    }

    @Test
    @DisplayName("新增 - 落库失败返回错误")
    void create_insertFails() {
        when(categoryMapper.selectById("new_top")).thenReturn(null);
        when(categoryMapper.countSameNameUnderParent("新分类", null, null)).thenReturn(0);
        when(categoryMapper.selectByParentId(null)).thenReturn(new ArrayList<>());
        when(categoryMapper.insert(any(CategoryPojo.class))).thenReturn(0);

        ResponseEntity<J> response =
                categoryManageService.createService("admin-uuid", form("new_top", "新分类", null, null));

        assertEquals(1, response.getBody().getCode());
        assertEquals("分类新增失败", response.getBody().getMsg());
    }

    // ==================== 修改 ====================

    @Test
    @DisplayName("修改 - 分类不存在返回资源失败")
    void update_missing() {
        when(categoryMapper.selectById("ghost")).thenReturn(null);

        ResponseEntity<J> response = categoryManageService.updateService("admin-uuid", "ghost", form(null, "名称", null, null));

        assertEquals(1, response.getBody().getCode());
        verify(categoryMapper, never()).update(any(CategoryPojo.class));
    }

    @Test
    @DisplayName("修改 - 空表单被拒绝")
    void update_nullForm() {
        when(categoryMapper.selectById("3d_model")).thenReturn(pojo("3d_model", null, "模型", 1, 1));

        ResponseEntity<J> response = categoryManageService.updateService("admin-uuid", "3d_model", null);

        assertEquals(1, response.getBody().getCode());
        assertEquals("请填写分类信息", response.getBody().getMsg());
    }

    @Test
    @DisplayName("修改 - 只改名称：不动父级、不做移动，返回最新统计")
    void update_renameOnly() {
        CategoryPojo existing = pojo("q_model", "3d_model", "Q版模型", 2, 1);
        when(categoryMapper.selectById("q_model")).thenReturn(existing);
        // 父级未变化时服务端仍会查一次父级用于推导层级，因此这里必须给出父级
        when(categoryMapper.selectById("3d_model")).thenReturn(pojo("3d_model", null, "模型", 1, 1));
        when(categoryMapper.countSameNameUnderParent("Q版手办", "3d_model", "q_model")).thenReturn(0);
        when(categoryMapper.update(any(CategoryPojo.class))).thenReturn(1);
        when(categoryMapper.countWorks("q_model")).thenReturn(4);
        when(categoryMapper.countChildren("q_model")).thenReturn(0);

        ResponseEntity<J> response =
                categoryManageService.updateService("admin-uuid", "q_model", form(null, "Q版手办", "3d_model", null));

        assertEquals(0, response.getBody().getCode());
        CategoryManageItem updated = (CategoryManageItem) response.getBody().getData();
        assertEquals("Q版手办", updated.getName());
        assertEquals(2, updated.getLevel());
        assertEquals("3d_model", updated.getParent_id());
        assertEquals(4, updated.getBound_works());
        verify(categoryMapper, never()).updateLevel(anyString(), any());
    }

    @Test
    @DisplayName("修改 - 不能移动到自身或自己的后代下（防环）")
    void update_rejectsCycle() {
        CategoryPojo existing = pojo("beautify", null, "美化", 1, 2);
        when(categoryMapper.selectById("beautify")).thenReturn(existing);

        ResponseEntity<J> self = categoryManageService.updateService(
                "admin-uuid", "beautify", form(null, "美化", "beautify", null));
        assertEquals(1, self.getBody().getCode());
        assertTrue(self.getBody().getMsg().contains("不能把分类移动到自己下面"));

        when(categoryMapper.selectById("mobile_theme")).thenReturn(pojo("mobile_theme", "beautify", "手机主题", 2, 1));
        when(categoryMapper.selectSubtree("beautify")).thenReturn(List.of(
                pojo("beautify", null, "美化", 1, 2),
                pojo("mobile_theme", "beautify", "手机主题", 2, 1)));
        ResponseEntity<J> descendant = categoryManageService.updateService(
                "admin-uuid", "beautify", form(null, "美化", "mobile_theme", null));
        assertEquals(1, descendant.getBody().getCode());
        assertTrue(descendant.getBody().getMsg().contains("子分类下面"));
        verify(categoryMapper, never()).update(any(CategoryPojo.class));
    }

    @Test
    @DisplayName("修改 - 移动父分类：级联修正整棵子树的 level")
    void update_moveRecalculatesSubtreeLevel() {
        CategoryPojo existing = pojo("moved", null, "待移动", 1, 1);
        when(categoryMapper.selectById("moved")).thenReturn(existing);
        when(categoryMapper.selectById("beautify")).thenReturn(pojo("beautify", null, "美化", 1, 2));
        when(categoryMapper.selectSubtree("moved")).thenReturn(List.of(
                pojo("moved", null, "待移动", 1, 1),
                pojo("child_of_moved", "moved", "子项", 2, 1)));
        when(categoryMapper.countSameNameUnderParent("待移动", "beautify", "moved")).thenReturn(0);
        when(categoryMapper.selectByParentId("beautify")).thenReturn(List.of(pojo("beautify", null, "美化", 1, 2)));
        when(categoryMapper.update(any(CategoryPojo.class))).thenReturn(1);
        when(categoryMapper.countWorks("moved")).thenReturn(0);
        when(categoryMapper.countChildren("moved")).thenReturn(1);

        ResponseEntity<J> response =
                categoryManageService.updateService("admin-uuid", "moved", form(null, "待移动", "beautify", null));

        assertEquals(0, response.getBody().getCode());
        CategoryManageItem updated = (CategoryManageItem) response.getBody().getData();
        assertEquals(2, updated.getLevel());
        assertEquals(3, updated.getSort());
        // 子树中除自身外的节点 level 由 2 修正为 3（delta = +1）
        verify(categoryMapper).updateLevel("child_of_moved", 3);
    }

    @Test
    @DisplayName("修改 - 落库失败返回错误")
    void update_persistFails() {
        when(categoryMapper.selectById("q_model")).thenReturn(pojo("q_model", "3d_model", "Q版模型", 2, 1));
        when(categoryMapper.selectById("3d_model")).thenReturn(pojo("3d_model", null, "模型", 1, 1));
        when(categoryMapper.countSameNameUnderParent("Q版手办", "3d_model", "q_model")).thenReturn(0);
        when(categoryMapper.update(any(CategoryPojo.class))).thenReturn(0);

        ResponseEntity<J> response =
                categoryManageService.updateService("admin-uuid", "q_model", form(null, "Q版手办", "3d_model", null));

        assertEquals(1, response.getBody().getCode());
        assertEquals("分类修改失败", response.getBody().getMsg());
    }

    // ==================== 上移 / 下移 ====================

    @Test
    @DisplayName("排序 - 分类不存在返回资源失败")
    void moveSort_missing() {
        when(categoryMapper.selectById("ghost")).thenReturn(null);

        ResponseEntity<J> response = categoryManageService.moveSortService("admin-uuid", "ghost", "up");

        assertEquals(1, response.getBody().getCode());
    }

    @Test
    @DisplayName("排序 - 方向非法被拒绝")
    void moveSort_invalidDirection() {
        when(categoryMapper.selectById("a")).thenReturn(pojo("a", null, "甲", 1, 1));

        ResponseEntity<J> response = categoryManageService.moveSortService("admin-uuid", "a", "left");

        assertEquals(1, response.getBody().getCode());
        assertTrue(response.getBody().getMsg().contains("排序方向不合法"));
    }

    @Test
    @DisplayName("排序 - 同级列表为空被拒绝")
    void moveSort_emptySiblings() {
        when(categoryMapper.selectById("a")).thenReturn(pojo("a", null, "甲", 1, 1));
        when(categoryMapper.selectByParentId(null)).thenReturn(new ArrayList<>());

        ResponseEntity<J> response = categoryManageService.moveSortService("admin-uuid", "a", "up");

        assertEquals(1, response.getBody().getCode());
        assertTrue(response.getBody().getMsg().contains("同级分类列表为空"));
    }

    @Test
    @DisplayName("排序 - 同级 sort 重复时先规范化再上移")
    void moveSort_upNormalizesAndSwaps() {
        CategoryPojo first = pojo("a", null, "甲", 1, 1);
        CategoryPojo second = pojo("b", null, "乙", 1, 1);
        when(categoryMapper.selectById("b")).thenReturn(second);
        when(categoryMapper.selectByParentId(null)).thenReturn(new ArrayList<>(List.of(first, second)));

        ResponseEntity<J> response = categoryManageService.moveSortService("admin-uuid", "b", "up");

        assertEquals(0, response.getBody().getCode());
        // 规范化：b 的 sort 由 1 纠正为 2
        verify(categoryMapper).updateSort("b", 2);
        // 交换：b 取 1，a 取 2
        verify(categoryMapper).updateSort("b", 1);
        verify(categoryMapper).updateSort("a", 2);
    }

    @Test
    @DisplayName("排序 - 已是同级第一个时不允许上移")
    void moveSort_alreadyFirst() {
        CategoryPojo first = pojo("a", null, "甲", 1, 1);
        CategoryPojo second = pojo("b", null, "乙", 1, 2);
        when(categoryMapper.selectById("a")).thenReturn(first);
        when(categoryMapper.selectByParentId(null)).thenReturn(new ArrayList<>(List.of(first, second)));

        ResponseEntity<J> response = categoryManageService.moveSortService("admin-uuid", "a", "up");

        assertEquals(1, response.getBody().getCode());
        assertTrue(response.getBody().getMsg().contains("第一个"));
    }

    @Test
    @DisplayName("排序 - 已是同级最后一个时不允许下移")
    void moveSort_alreadyLast() {
        CategoryPojo first = pojo("a", null, "甲", 1, 1);
        CategoryPojo second = pojo("b", null, "乙", 1, 2);
        when(categoryMapper.selectById("b")).thenReturn(second);
        when(categoryMapper.selectByParentId(null)).thenReturn(new ArrayList<>(List.of(first, second)));

        ResponseEntity<J> response = categoryManageService.moveSortService("admin-uuid", "b", "down");

        assertEquals(1, response.getBody().getCode());
        assertTrue(response.getBody().getMsg().contains("最后一个"));
    }

    @Test
    @DisplayName("排序 - 同级中找不到自身被拒绝")
    void moveSort_notInSiblings() {
        CategoryPojo self = pojo("x", "game", "孤儿", 2, 1);
        when(categoryMapper.selectById("x")).thenReturn(self);
        when(categoryMapper.selectByParentId("game")).thenReturn(new ArrayList<>(List.of(pojo("y", "game", "别项", 2, 1))));

        ResponseEntity<J> response = categoryManageService.moveSortService("admin-uuid", "x", "up");

        assertEquals(1, response.getBody().getCode());
        assertTrue(response.getBody().getMsg().contains("未在所属层级中找到"));
    }

    // ==================== 删除 ====================

    @Test
    @DisplayName("删除 - 分类不存在返回资源失败")
    void delete_missing() {
        when(categoryMapper.selectByIdForUpdate("ghost")).thenReturn(null);

        ResponseEntity<J> response = categoryManageService.deleteService("admin-uuid", "ghost");

        assertEquals(1, response.getBody().getCode());
    }

    @Test
    @DisplayName("删除 - 有子分类时拒绝，并提示数量")
    void delete_rejectedWhenHasChildren() {
        when(categoryMapper.selectByIdForUpdate("beautify")).thenReturn(pojo("beautify", null, "美化", 1, 2));
        when(categoryMapper.countChildren("beautify")).thenReturn(2);

        ResponseEntity<J> response = categoryManageService.deleteService("admin-uuid", "beautify");

        assertEquals(1, response.getBody().getCode());
        assertTrue(response.getBody().getMsg().contains("2 个子分类"));
        verify(categoryMapper, never()).deleteById(anyString());
    }

    @Test
    @DisplayName("删除 - 有作品关联时拒绝，并提示数量")
    void delete_rejectedWhenHasWorks() {
        when(categoryMapper.selectByIdForUpdate("game")).thenReturn(pojo("game", null, "游戏", 1, 4));
        when(categoryMapper.countChildren("game")).thenReturn(0);
        when(categoryMapper.countWorks("game")).thenReturn(3);

        ResponseEntity<J> response = categoryManageService.deleteService("admin-uuid", "game");

        assertEquals(1, response.getBody().getCode());
        assertTrue(response.getBody().getMsg().contains("3 个作品"));
        verify(categoryMapper, never()).deleteById(anyString());
    }

    @Test
    @DisplayName("删除 - 无关联分类可删除")
    void delete_success() {
        when(categoryMapper.selectByIdForUpdate("mouse_theme")).thenReturn(pojo("mouse_theme", "beautify", "鼠标指针", 2, 1));
        when(categoryMapper.countChildren("mouse_theme")).thenReturn(0);
        when(categoryMapper.countWorks("mouse_theme")).thenReturn(0);
        when(categoryMapper.deleteById("mouse_theme")).thenReturn(1);

        ResponseEntity<J> response = categoryManageService.deleteService("admin-uuid", "mouse_theme");

        assertEquals(0, response.getBody().getCode());
        assertEquals("mouse_theme", response.getBody().getData());
        verify(categoryMapper).deleteById("mouse_theme");
    }

    @Test
    @DisplayName("删除 - 落库未生效时返回错误")
    void delete_persistFails() {
        when(categoryMapper.selectByIdForUpdate("mouse_theme")).thenReturn(pojo("mouse_theme", "beautify", "鼠标指针", 2, 1));
        when(categoryMapper.countChildren("mouse_theme")).thenReturn(0);
        when(categoryMapper.countWorks("mouse_theme")).thenReturn(0);
        when(categoryMapper.deleteById("mouse_theme")).thenReturn(0);

        ResponseEntity<J> response = categoryManageService.deleteService("admin-uuid", "mouse_theme");

        assertEquals(1, response.getBody().getCode());
        assertEquals("分类删除失败", response.getBody().getMsg());
    }

    // ==================== 边角：isNull 匹配的显式用例 ====================

    @Test
    @DisplayName("新增 - 顶级分类的同级重名校验使用 parent_id IS NULL")
    void create_topDuplicateCheckUsesNullParent() {
        when(categoryMapper.selectById("new_top")).thenReturn(null);
        when(categoryMapper.countSameNameUnderParent(eq("游戏"), isNull(), isNull())).thenReturn(1);

        ResponseEntity<J> response =
                categoryManageService.createService("admin-uuid", form("new_top", "游戏", null, null));

        assertEquals(1, response.getBody().getCode());
        assertNotNull(response.getBody().getMsg());
        assertTrue(response.getBody().getMsg().contains("已存在同名分类"));
    }
}
