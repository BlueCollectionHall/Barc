package com.miaoyu.barc.api.work.service;

import com.miaoyu.barc.api.work.mapper.WorkCategoryMapper;
import com.miaoyu.barc.api.work.mapper.WorkMapper;
import com.miaoyu.barc.api.work.model.WorkCategoryModel;
import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.response.ChangeR;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.response.UserR;
import com.miaoyu.barc.utils.GenerateUUID;
import com.miaoyu.barc.utils.J;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
public class WorkCategoryService {

    private final WorkMapper workMapper;
    private final WorkCategoryMapper workCategoryMapper;

    public WorkCategoryService(WorkMapper workMapper, WorkCategoryMapper workCategoryMapper) {
        this.workMapper = workMapper;
        this.workCategoryMapper = workCategoryMapper;
    }

    public ResponseEntity<J> recordWorkAndCategoryService(String uuid, WorkCategoryModel requestModel) {
        WorkModel work = workMapper.selectById(requestModel.getWork_id());
        if (work == null) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        if (!work.getAuthor().equals(uuid)) {
            return ResponseEntity.ok(new UserR().uuidMismatch());
        }
        requestModel.setId(new GenerateUUID().getUuid36l());
        boolean insert = workCategoryMapper.insert(requestModel);
        if (insert) {
            return ResponseEntity.ok(new ChangeR().udu(true,  1));
        }
        return ResponseEntity.ok(new ChangeR().udu(false, 1));
    }

    public ResponseEntity<J> getCategoriesByWorkIdService(String workId) {
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, workCategoryMapper.selectByWorkId(workId)));
    }

    public ResponseEntity<J> deleteWorkCategoryService(String uuid, String id) {
        String workAuthor = workCategoryMapper.selectWorkAuthorById(id);
        if (!workAuthor.equals(uuid)) {
            return ResponseEntity.ok(new UserR().uuidMismatch());
        }
        boolean delete = workCategoryMapper.delete(id);
        if (delete) {
            return ResponseEntity.ok(new ChangeR().udu(true,  2));
        }
        return ResponseEntity.ok(new ChangeR().udu(false, 2));
    }
}
