package com.miaoyu.barc.api.work.service;

import com.miaoyu.barc.api.mapper.CategoryMapper;
import com.miaoyu.barc.api.work.enumeration.*;
import com.miaoyu.barc.api.work.mapper.*;
import com.miaoyu.barc.api.work.model.*;
import com.miaoyu.barc.response.*;
import com.miaoyu.barc.utils.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

/** 分类关联属于作品内容，修改与正文/图片使用相同的归属校验和提审版本。 */
@Service
public class WorkCategoryService {
    @Autowired private WorkMapper workMapper;
    @Autowired private WorkCategoryMapper workCategoryMapper;
    @Autowired private CategoryMapper categoryMapper;
    @Autowired private WorkService workService;

    // 父作品锁串行化关联修改，锁后的关联复查读取最新提交数据。
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public ResponseEntity<J> recordWorkAndCategoryService(String uuid, WorkCategoryModel request) {
        String workId = RequestInput.required(request.getWork_id(), "作品ID", 100);
        String categoryId = RequestInput.required(request.getCategory_id(), "分类ID", 100);
        WorkModel work = requireOwner(uuid, workId);
        if (categoryMapper.selectById(categoryId) == null) throw RequestInput.invalid("分类不存在");
        if (workCategoryMapper.selectByWorkId(workId).stream().anyMatch(c -> categoryId.equals(c.getCategory_id()))) {
            return ResponseEntity.ok(new ChangeR().udu(true, 1));
        }
        WorkCategoryModel model = new WorkCategoryModel(); model.setId(new GenerateUUID().getUuid36l());
        model.setWork_id(work.getId()); model.setCategory_id(categoryId);
        if (!workCategoryMapper.insert(model)) throw RequestInput.invalid("分类保存失败");
        workService.submitForReview(workId);
        return ResponseEntity.ok(new ChangeR().udu(true, 1));
    }

    public ResponseEntity<J> getCategoriesByWorkIdService(String workId) {
        WorkModel work = workMapper.selectById(workId);
        if (work == null || work.getStatus() != WorkStatusEnum.PUBLIC || work.getReview_status() != WorkReviewStatusEnum.APPROVED) {
            return ResponseEntity.ok(new ErrorR().normal("作品不可访问"));
        }
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, workCategoryMapper.selectByWorkId(workId)));
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public ResponseEntity<J> deleteWorkCategoryService(String uuid, String id) {
        WorkCategoryModel initial = workCategoryMapper.selectById(id);
        if (initial == null) return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        requireOwner(uuid, initial.getWork_id());
        // 独立锁定查询绕过前一次普通查询缓存，等待期间已删除的关联按不存在处理。
        if (workCategoryMapper.selectByIdForUpdate(id) == null) return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        if (!workCategoryMapper.delete(id)) throw RequestInput.invalid("分类删除失败");
        workService.submitForReview(initial.getWork_id());
        return ResponseEntity.ok(new ChangeR().udu(true, 2));
    }

    private WorkModel requireOwner(String uuid, String workId) {
        WorkModel work = workMapper.selectByIdForUpdate(workId);
        if (work == null || work.getStatus() == WorkStatusEnum.DELETED) throw RequestInput.invalid("作品不存在或已删除");
        if (uuid == null || (!Objects.equals(work.getAuthor(), uuid) && !Objects.equals(work.getUploader(), uuid))) {
            throw new com.miaoyu.barc.exception.ApiException(new UserR().uuidMismatch());
        }
        return work;
    }
}
