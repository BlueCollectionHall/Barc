package com.miaoyu.barc.api.work.service;

import com.miaoyu.barc.api.work.mapper.WorkLikeMapper;
import com.miaoyu.barc.api.work.mapper.WorkMapper;
import com.miaoyu.barc.api.work.enumeration.WorkStatusEnum;
import com.miaoyu.barc.api.work.enumeration.WorkReviewStatusEnum;
import com.miaoyu.barc.api.work.model.WorkLikeModel;
import com.miaoyu.barc.api.work.model.WorkLikeToggleDto;
import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.response.ErrorR;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.utils.GenerateUUID;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.dto.PageRequestDto;
import com.miaoyu.barc.utils.dto.PageResultDto;
import com.miaoyu.barc.utils.pojo.PageInitPojo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class WorkLikeService {
    @Autowired
    private WorkLikeMapper workLikeMapper;

    @Autowired
    private WorkMapper workMapper;

    @Autowired
    private WorkService workService;

    @Transactional
    public ResponseEntity<J> toggleLike(String userUuid, WorkLikeToggleDto dto) {
        if (isBlank(userUuid)) {
            return ResponseEntity.ok(new ErrorR().normal("用户未登录"));
        }
        if (Objects.isNull(dto) || isBlank(dto.getWork_id())) {
            return ResponseEntity.ok(new ErrorR().normal("作品ID不能为空"));
        }

        String workId = dto.getWork_id().trim();
        String normalizedUserUuid = userUuid.trim();
        WorkModel work = workMapper.selectById(workId);
        if (Objects.isNull(work)) {
            return ResponseEntity.ok(new ErrorR().normal("作品不存在"));
        }
        if (!isLikeable(work)) {
            return ResponseEntity.ok(new ErrorR().normal("作品不可点赞"));
        }

        int currentCount = safeLikeCount(work);
        WorkLikeModel existingLike = workLikeMapper.selectByWorkIdAndUserUuid(workId, normalizedUserUuid);
        if (Objects.nonNull(existingLike)) {
            return unlike(normalizedUserUuid, workId, currentCount);
        }

        return like(normalizedUserUuid, workId, currentCount);
    }

    private ResponseEntity<J> like(String userUuid, String workId, int currentCount) {
        WorkLikeModel model = new WorkLikeModel();
        model.setId(new GenerateUUID().getUuid36l());
        model.setWork_id(workId);
        model.setUser_uuid(userUuid);

        try {
            int inserted = workLikeMapper.insert(model);
            if (inserted > 0) {
                int updated = workMapper.incrementLikeCount(workId);
                if (updated > 0) {
                    return latestResult(userUuid, workId);
                }
            }
            return latestResult(userUuid, workId);
        } catch (DuplicateKeyException e) {
            return latestResult(userUuid, workId);
        }
    }

    private ResponseEntity<J> unlike(String userUuid, String workId, int currentCount) {
        int deleted = workLikeMapper.deleteByWorkIdAndUserUuid(workId, userUuid);
        if (deleted > 0) {
            int updated = workMapper.decrementLikeCount(workId);
            if (updated > 0) {
                return latestResult(userUuid, workId);
            }
        }
        return latestResult(userUuid, workId);
    }

    private ResponseEntity<J> latestResult(String userUuid, String workId) {
        WorkLikeModel currentLike = workLikeMapper.selectByWorkIdAndUserUuid(workId, userUuid);
        WorkModel latestWork = workMapper.selectById(workId);
        return likeResult(Objects.nonNull(currentLike), safeLikeCount(latestWork));
    }

    public ResponseEntity<J> listLikedWorksByUsername(String username, PageRequestDto dto) {
        if (isBlank(username)) {
            return ResponseEntity.ok(new ErrorR().normal("用户名不能为空"));
        }

        PageInitPojo pageInit = new PageInitPojo(Objects.isNull(dto) ? new PageRequestDto() : dto);
        Integer pageNum = pageInit.getPageNum();
        Integer pageSize = pageInit.getPageSize();
        Integer offset = pageInit.getOffset();
        String normalizedUsername = username.trim();

        List<WorkModel> works = workLikeMapper.selectPublicLikedWorksByUsername(normalizedUsername, WorkStatusEnum.PUBLIC, offset, pageSize);
        Long total = workLikeMapper.countPublicLikedWorksByUsername(normalizedUsername, WorkStatusEnum.PUBLIC);
        long safeTotal = Objects.isNull(total) ? 0L : total;
        List<WorkModel> signatureWorks = works == null || works.isEmpty()
                ? List.of()
                : workService.loopSignatureWorkCover(works);
        int totalPage = (int) Math.ceil((double) safeTotal / pageSize);

        return ResponseEntity.ok(
                new ResourceR().resourceSuch(
                        true,
                        new PageResultDto<>(
                                safeTotal,
                                signatureWorks,
                                pageNum,
                                pageSize,
                                totalPage == 0 ? 1 : totalPage
                        )
                )
        );
    }

    private boolean isLikeable(WorkModel work) {
        return WorkStatusEnum.PUBLIC.equals(work.getStatus())
                && WorkReviewStatusEnum.APPROVED.equals(work.getReview_status());
    }

    private boolean isBlank(String value) {
        return Objects.isNull(value) || value.trim().isEmpty();
    }

    private int safeLikeCount(WorkModel work) {
        if (Objects.isNull(work)) return 0;
        return Objects.isNull(work.getLike_count()) ? 0 : Math.max(work.getLike_count(), 0);
    }

    private ResponseEntity<J> likeResult(boolean liked, int likeCount) {
        Map<String, Object> data = new HashMap<>();
        data.put("liked", liked);
        data.put("like_count", Math.max(likeCount, 0));
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, data));
    }
}
