package com.miaoyu.barc.api.mapper;

import com.miaoyu.barc.api.model.CategoryManageItem;
import com.miaoyu.barc.api.pojo.CategoryPojo;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 作品分类数据访问层
 *
 * <p>说明：</p>
 * 1、用户端（公开接口）只使用 {@link #selectAll()} 与 {@link #selectAllTop()} 两个只读查询，
 *    本次新增的写方法全部只服务管理端「作品分类管理」，两条链路互不影响；
 * 2、表结构事实（2026-09-19 只读核对开发库 ba）：{@code category.id varchar(50)} 为语义化主键、
 *    {@code name varchar(20)}、{@code parent_id} 自关联且外键为 {@code ON DELETE CASCADE}；
 *    {@code work_category.id varchar(36)}、{@code category_id} 外键同样 {@code ON DELETE CASCADE}；
 * 3、已退役的 schema.sql 把 {@code work_category.id} 写成 {@code BIGINT AUTO_INCREMENT}，
 *    与真实库不一致，任何人不得据此判断结构（详见 docs/superpowers/specs 的分类管理设计文档）；
 * 4、本项目的 MyBatis 配置为 {@code map-underscore-to-camel-case: false}，
 *    因此列名与实体属性名必须保持同一种下划线写法。
 */
public interface CategoryMapper {

    // ==================== 用户端（公开，只读） ====================

    @Select("SELECT * FROM category")
    List<CategoryPojo> selectAll();

    @Select("SELECT c.id, c.name, c.sort, c.icon FROM category c WHERE level = 1")
    List<CategoryPojo> selectAllTop();

    // ==================== 管理端「作品分类管理」 ====================

    /**
     * 按主键查询单个分类
     */
    @Select("SELECT * FROM category WHERE id = #{id}")
    CategoryPojo selectById(@Param("id") String id);

    /**
     * 管理端列表：一次性取出全部分类，并附带「直接挂在本分类的作品数」与「直接子分类数」。
     *
     * <p>两个统计值用于：1）列表展示；2）删除前的影响面判定（有子分类或有作品关联时禁止删除）。
     * 由于分类总量极小（当前 13 条），用相关子查询比写聚合 + 分组更直观。</p>
     */
    @Select("SELECT c.id, c.parent_id, c.name, c.level, c.sort, " +
            "(SELECT COUNT(1) FROM work_category wc WHERE wc.category_id = c.id) AS bound_works, " +
            "(SELECT COUNT(1) FROM category ch WHERE ch.parent_id = c.id) AS children_count " +
            "FROM category c ORDER BY c.level ASC, c.sort ASC, c.id ASC")
    List<CategoryManageItem> selectManageList();

    /**
     * 查询某父级下的直接子分类（按 sort 升序）。
     *
     * <p>parentId 传 null 表示查询顶级分类：SQL 中必须用 {@code IS NULL}，
     * 不能写成 {@code parent_id = NULL}（后者永远不成立）。</p>
     */
    @Select("<script>" +
            "SELECT * FROM category " +
            "<choose>" +
            "<when test='parentId != null'>WHERE parent_id = #{parentId} </when>" +
            "<otherwise>WHERE parent_id IS NULL </otherwise>" +
            "</choose>" +
            "ORDER BY sort ASC, id ASC" +
            "</script>")
    List<CategoryPojo> selectByParentId(@Param("parentId") String parentId);

    /**
     * 统计直接子分类数量（删除校验用）
     */
    @Select("<script>" +
            "SELECT COUNT(1) FROM category " +
            "<choose>" +
            "<when test='parentId != null'>WHERE parent_id = #{parentId}</when>" +
            "<otherwise>WHERE parent_id IS NULL</otherwise>" +
            "</choose>" +
            "</script>")
    int countChildren(@Param("parentId") String parentId);

    /**
     * 统计挂在某分类下的作品关联数量（删除校验与列表展示用）
     */
    @Select("SELECT COUNT(1) FROM work_category WHERE category_id = #{categoryId}")
    int countWorks(@Param("categoryId") String categoryId);

    /**
     * 取某分类及其全部后代（递归 CTE）。
     *
     * <p>用于「移动分类」时按层级差 {@code delta} 级联修正整棵子树的 level；
     * 子树规模为个位数，取回后在服务层逐条更新，比写 CTE UPDATE 更可控、更易单测。</p>
     */
    @Select("WITH RECURSIVE category_subtree AS (" +
            "SELECT * FROM category WHERE id = #{id} " +
            "UNION ALL " +
            "SELECT c.* FROM category c JOIN category_subtree s ON c.parent_id = s.id) " +
            "SELECT * FROM category_subtree")
    List<CategoryPojo> selectSubtree(@Param("id") String id);

    /**
     * 判断「同一父级下」是否已存在同名分类（新增与修改共用）。
     *
     * <p>excludeId 用于修改场景排除自身；parentId 为 null 表示顶级分类。
     * 不同父级下允许同名（例如两个不同的子分类都可以叫「其他」）。</p>
     */
    @Select("<script>" +
            "SELECT COUNT(1) FROM category WHERE name = #{name} " +
            "<choose>" +
            "<when test='parentId != null'>AND parent_id = #{parentId} </when>" +
            "<otherwise>AND parent_id IS NULL </otherwise>" +
            "</choose>" +
            "<if test='excludeId != null'>AND id != #{excludeId} </if>" +
            "</script>")
    int countSameNameUnderParent(@Param("name") String name,
                                 @Param("parentId") String parentId,
                                 @Param("excludeId") String excludeId);

    /**
     * 新增分类。
     *
     * <p>id 由管理端手填（语义化英文小写，与存量 13 条风格一致）；
     * is_enabled 固定写入 TRUE —— 本项目 is_enabled 目前没有任何消费方，
     * 不做「停用」语义（详见设计文档 Q7）。</p>
     */
    @Insert("INSERT INTO category(id, parent_id, name, level, sort, is_enabled) " +
            "VALUES(#{id}, #{parent_id}, #{name}, #{level}, #{sort}, TRUE)")
    int insert(CategoryPojo category);

    /**
     * 修改分类（名称 / 父级 / 层级 / 排序）
     */
    @Update("UPDATE category SET name = #{name}, parent_id = #{parent_id}, " +
            "level = #{level}, sort = #{sort} WHERE id = #{id}")
    int update(CategoryPojo category);

    /**
     * 只更新排序（同级上移 / 下移、同级规范化重排使用）
     */
    @Update("UPDATE category SET sort = #{sort} WHERE id = #{id}")
    int updateSort(@Param("id") String id, @Param("sort") Integer sort);

    /**
     * 只更新层级（移动分类后的级联修正使用）
     */
    @Update("UPDATE category SET level = #{level} WHERE id = #{id}")
    int updateLevel(@Param("id") String id, @Param("level") Integer level);

    /**
     * 物理删除分类。
     *
     * <p>调用前必须已在服务层完成「无子分类、无作品关联」校验；
     * 该删除会触发 work_category 的外键级联，但服务层的校验保证此处不会有作品关联被牵连。</p>
     */
    @Delete("DELETE FROM category WHERE id = #{id}")
    int deleteById(@Param("id") String id);
}
