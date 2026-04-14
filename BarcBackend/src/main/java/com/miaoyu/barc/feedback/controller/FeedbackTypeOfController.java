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
