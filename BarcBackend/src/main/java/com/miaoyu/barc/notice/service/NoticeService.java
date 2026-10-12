package com.miaoyu.barc.notice.service;

import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.notice.mapper.NoticeMapper;
import com.miaoyu.barc.notice.model.NoticeModel;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.permission.PermissionPolicy;
import com.miaoyu.barc.response.UserR;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.model.UserArchiveModel;
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
    @Autowired
    private UserArchiveMapper userArchiveMapper;

    /**
     * 最近范围命中或历史兜底的结果始终是全表最新一条，只需一次查询。
     * day 保留为兼容参数，不再改变“有公告就显示”的既定业务语义。
     */
    public ResponseEntity<J> latestNoticeService(Integer day) {
        NoticeModel latest = noticeMapper.latestWithoutDayLimit();
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, latest == null ? List.of() : List.of(latest)));
    }

    @Transactional(readOnly = true)
    public ResponseEntity<J> noticesByPageService(PageRequestDto dto) {
        if (dto == null) dto = new PageRequestDto();
        if (dto.getParams() != null && dto.getParams().get("title") != null)
            com.miaoyu.barc.utils.RequestInput.optional(dto.getParams().get("title").toString(), "公告搜索词", 100);
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
        validateContent(model);
        model.setId(new GenerateUUID().getUuid36l());
        model.setAuthor(uuid);
        boolean insert = noticeMapper.insert(model);
        if (insert) {
            return ResponseEntity.ok(new ChangeR().udu(true, 1));
        }
        return ResponseEntity.ok(new ChangeR().udu(false, 1));
    }

    /** 公告更新只信任数据库作者，保留真实作者与副馆长及以上的编辑能力。 */
    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check()})
    public ResponseEntity<J> updateNoticeService(String uuid, NoticeModel model) {
        NoticeModel stored = model == null ? null : noticeMapper.selectById(model.getId());
        if (stored == null) return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        UserArchiveModel operator = userArchiveMapper.selectByUuid(uuid);
        if (operator == null || (!uuid.equals(stored.getAuthor())
                && !PermissionPolicy.matches(operator, UserIdentityEnum.MANAGER, PermissionConst.ADMINISTRATOR, false))) {
            return ResponseEntity.ok(new UserR().insufficientAccountPermission());
        }
        validateContent(model);
        // 显式写入内容字段，客户端作者和时间字段不会影响持久化记录。
        NoticeModel update = new NoticeModel();
        update.setId(stored.getId());
        update.setTitle(model.getTitle());
        update.setContent(model.getContent());
        try {
            return ResponseEntity.ok(new ChangeR().udu(noticeMapper.update(update), 3));
        } catch (RuntimeException e) {
            log.error("修改公告失败{}", e.getMessage());
            return ResponseEntity.ok(new ChangeR().udu(false, 3));
        }
    }

    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check(isSuchElseRequire = false, identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.ADMINISTRATOR)})
    public ResponseEntity<J> deleteNoticeService(String uuid, String noticeId) {
        try {
            return ResponseEntity.ok(new ChangeR().udu(noticeMapper.deleteById(noticeId), 2));
        } catch (RuntimeException e) {
            log.error("删除公告失败{}", e.getMessage());
            return ResponseEntity.ok(new ChangeR().udu(false, 2));
        }
    }

    /** 写入前校验允许的正文与标题，空白和超长输入不进入 Mapper。 */
    private void validateContent(NoticeModel model) {
        if (model == null) throw com.miaoyu.barc.utils.RequestInput.invalid("公告不能为空");
        model.setTitle(com.miaoyu.barc.utils.RequestInput.required(model.getTitle(), "公告标题", 100));
        model.setContent(com.miaoyu.barc.utils.RequestInput.required(model.getContent(), "公告正文", 100000));
    }
}
