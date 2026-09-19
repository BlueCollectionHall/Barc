package com.miaoyu.barc.feedback.controller;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.feedback.mapper.FeedbackMapper;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.utils.J;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 【已废弃】投诉反馈分类查询接口。
 *
 * <p>反馈分类已不再由数据库驱动：后端使用 {@code FeedbackTypeEnum}，
 * 前端使用 {@code src/utils/feedbackType.ts} 的本地映射，
 * 两个前端均不再调用 {@code /feedback/all_types} 与 {@code /feedback/options_by_type}。
 * 对应的 {@code feedback_type} 与 {@code feedback_option} 表已标记废弃，禁止新增使用。</p>
 *
 * @deprecated 保留仅为兼容历史调用，请勿在新功能中依赖本控制器
 */
@Deprecated
@IgnoreAuth
@RestController
@RequestMapping("/feedback")
public class FeedbackTypeOfController {
    private final FeedbackMapper feedbackMapper;

    public FeedbackTypeOfController(FeedbackMapper feedbackMapper) {
        this.feedbackMapper = feedbackMapper;
    }

    /**获取全部投诉反馈分类
     * @return List类型中包含全部投诉反馈分类*/
    @GetMapping("/all_types")
    public ResponseEntity<J> getFeedbackAllTypesControl() {
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, feedbackMapper.selectAllTypes()));
    }

    /**根据投诉反馈分类ID获取其下属的投诉反馈选项
     * @param typeId 投诉反馈分类ID
     * @return List类型中包含符合条件的投诉反馈选项*/
    @GetMapping("/options_by_type")
    public ResponseEntity<J> getFeedbackOptionsByTypeControl(
            @RequestParam("type_id") String typeId
    ) {
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, feedbackMapper.selectOptionsByParentId(typeId)));
    }
}
