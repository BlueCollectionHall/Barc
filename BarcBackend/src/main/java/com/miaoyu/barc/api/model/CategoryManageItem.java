package com.miaoyu.barc.api.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理端「作品分类管理」列表项（树形结构）
 *
 * <p>与面向用户端的 {@link com.miaoyu.barc.api.pojo.CategoryPojo} 分开的原因：
 * 管理端列表需要额外的两个统计字段（直接作品数、直接子分类数），
 * 用于删除前的影响面提示与「禁止删除有子分类 / 有作品关联的分类」这条约束的判定；
 * 若直接给 CategoryPojo 加字段，会顺带改变公开接口 {@code /api/category/all} 的返回结构。</p>
 *
 * <p>注意：本项目未开启 MyBatis 驼峰映射（{@code map-underscore-to-camel-case: false}），
 * 因此字段名必须与 SQL 查询列名保持一致的写法。</p>
 */
@Getter
@Setter
public class CategoryManageItem {

    /** 分类主键（手填的语义化 id，创建后不可修改） */
    private String id;

    /** 父分类 id，顶级分类为 null */
    private String parent_id;

    /** 分类名称（同一父级下唯一） */
    private String name;

    /** 层级：顶级为 1，由服务端按 parent_id 派生 */
    private Integer level;

    /** 排序值：同级内升序 */
    private Integer sort;

    /** 直接挂在本分类下的作品数（不含子分类） */
    private Integer bound_works;

    /** 直接子分类数量 */
    private Integer children_count;

    /** 子分类列表（服务端组装成树后返回） */
    private List<CategoryManageItem> children;

    /**
     * 追加一个子分类；children 按需初始化，避免前端收到 null
     */
    public void addChild(CategoryManageItem child) {
        if (children == null) {
            children = new ArrayList<>();
        }
        children.add(child);
    }
}
