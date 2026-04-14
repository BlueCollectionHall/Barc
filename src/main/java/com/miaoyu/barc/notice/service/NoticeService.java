package com.miaoyu.barc.notice.service;

import com.miaoyu.barc.annotation.RequireSelfOrPermissionAnno;
import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.notice.mapper.NoticeMapper;
import com.miaoyu.barc.notice.model.NoticeModel;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.response.ChangeR;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.response.SuccessR;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.utils.GenerateUUID;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.dto.PageRequestDto;
import com.miaoyu.barc.utils.dto.PageResultDto;
import com.miaoyu.barc.utils.pojo.PageInitPojo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class NoticeService {
    @Autowired
    private NoticeMapper noticeMapper;

    public ResponseEntity<J> latestNoticeService(Integer day) {
        NoticeModel latest = noticeMapper.latest(day);
        if (latest == null) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(true, new ArrayList<>()));
        }
        List<NoticeModel> list = new ArrayList<>();
        list.add(latest);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, list));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<J> noticesByPageService(PageRequestDto dto) {
        PageInitPojo init = new PageInitPojo(dto);
        // 查询数据
        List<NoticeModel> models = noticeMapper.selectByPage(init.getOffset(), init.getPageSize(), dto.getParams());
        // 查询总数
        Long total = noticeMapper.countByPage(dto.getParams());
        // 计算总页数
        int mathTotalPage = (int) Math.ceil((double) total / init.getPageSize());
        Integer totalPage = mathTotalPage == 0 ? 1 : mathTotalPage;
        // 数据封装并返回
        return ResponseEntity.ok(
                new SuccessR().normal(
                        new PageResultDto<>(
                                total,
                                models,
                                init.getPageNum(),
                                init.getPageSize(),
                                totalPage)));
    }

    public ResponseEntity<J> getNoticeOnlyService(String noticeId) {
        NoticeModel model = noticeMapper.selectById(noticeId);
        if (model == null) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, model));
    }

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(isSuchElseRequire = false, identity = UserIdentityEnum.MANAGER)})
    public ResponseEntity<J> uploadNoticeService(String uuid, NoticeModel model) {
        model.setId(new GenerateUUID().getUuid36l());
        model.setAuthor(uuid);
        boolean insert = noticeMapper.insert(model);
        if (insert) {
            return ResponseEntity.ok(new ChangeR().udu(true, 1));
        }
        return ResponseEntity.ok(new ChangeR().udu(false, 1));
    }

    @RequireSelfOrPermissionAnno(identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.ADMINISTRATOR)
    public ResponseEntity<J> updateNoticeService(String uuid, String authorUuid, NoticeModel model) {
        try {
            noticeMapper.update(model);
        } catch (RuntimeException e) {
            log.error("修改公告失败{}", e.getMessage());
            return ResponseEntity.ok(new ChangeR().udu(false, 3));
        }
        return ResponseEntity.ok(new ChangeR().udu(true, 3));
    }

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(isSuchElseRequire = false, identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.ADMINISTRATOR)})
    public ResponseEntity<J> deleteNoticeService(String uuid, String noticeId) {
        try {
            noticeMapper.deleteById(noticeId);
        } catch (RuntimeException e) {
            log.error("删除公告失败{}", e.getMessage());
            return ResponseEntity.ok(new ChangeR().udu(false, 2));
        }
        return ResponseEntity.ok(new ChangeR().udu(true, 2));
    }
}
