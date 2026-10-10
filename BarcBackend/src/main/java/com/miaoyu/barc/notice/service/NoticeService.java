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

    /**
     * 获取最新公告（带天数范围的回退兜底）
     *
     * 变更说明（公告超期不再显示“暂无公告”）：
     * 原逻辑只按 day 天内的 updated_at 过滤，最新公告一旦超出 day 天就会被判定为“没有公告”，
     * 前端首页公告组件（传 day=30）因此显示“暂无数据”。
     * 现改为两级查询：
     * 1、先取 day 天内的最新一条公告（保持原语义，优先展示新公告）；
     * 2、若第 1 步没有结果，则回退取全表最近的一条历史公告（不限时间）。
     * 这样只要 notice 表中有公告，前端首页就永远有内容可展示；只有表中确实没有任何公告时才返回空列表。
     * 返回结构保持不变（仍为列表，0 或 1 条），双端前端无需改动。
     *
     * @param day 优先查询的日期范围（天）
     * @return 公告列表（命中或回退时为 1 条，表中无公告时为 0 条）
     */
    public ResponseEntity<J> latestNoticeService(Integer day) {
        // 第一级：按天数范围查询范围内最新的一条公告
        NoticeModel latest = noticeMapper.latest(day);
        if (latest == null) {
            // 第二级（回退兜底）：天数范围内没有公告时，取全表最近的一条历史公告，避免前端显示“暂无公告”
            latest = noticeMapper.latestWithoutDayLimit();
        }
        if (latest == null) {
            // 表中没有任何公告：仍返回空列表，由前端展示“暂无数据”
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
