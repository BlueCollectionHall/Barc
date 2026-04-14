package com.miaoyu.barc.feedback.controller;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.feedback.enumeration.FeedbackStatusEnum;
import com.miaoyu.barc.feedback.enumeration.FeedbackTypeEnum;
import com.miaoyu.barc.feedback.model.FeedbackFormModel;
import com.miaoyu.barc.feedback.service.FeedbackService;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.utils.J;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/feedback")
public class FeedbackController {
    @Autowired
    private FeedbackService feedbackService;

    /**获取全部投诉反馈的类型
     * @return List类型中包含键值对*/
    @IgnoreAuth
    @GetMapping("/types")
    public ResponseEntity<J> getFeedbackTypesControl() {
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, FeedbackTypeEnum.getOptions()));
    }

    /**获取全部投诉反馈的状态
     * @return List类型中包含键值对*/
    @IgnoreAuth
    @GetMapping("/status")
    public ResponseEntity<J> getFeedbackStatusControl() {
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, FeedbackStatusEnum.getOptions()));
    }

    /**根据类型获取全部投诉反馈信息
     * @param request 用户登录信息
     * @param count 获取数量，0为全部
     * @param typeEnum 类型
     * @param isManager 是否是管理员请求接口
     * @return List类型中包含指定数量符合条件的Feedback实体*/
    @GetMapping("/feedbacks_by_type")
    public ResponseEntity<J> getFeedbacksByTypeControl(
            HttpServletRequest request,
            @RequestParam(value = "count", required = false) Integer count,
            @RequestParam("type") FeedbackTypeEnum typeEnum,
            @RequestParam("is_manager") Boolean isManager
    ) {
        if (isManager) {
            return feedbackService.getFeedbacksByTypeWithManagerService(request.getAttribute("uuid").toString(), typeEnum);
        }
        return feedbackService.getFeedbacksByTypeWithMeService(request.getAttribute("uuid").toString(), typeEnum);
    }

    /**根据Feedback的ID获取唯一符合要求的Feedback实体（管理员用）
     * @param request 管理员登录信息
     * @param feedbackId Feedback的ID
     * @return 唯一符合条件的Feedback实体*/
    @GetMapping("/only_with_manager")
    public ResponseEntity<J> getFeedbackOnlyWithManagerControl(
            HttpServletRequest request,
            @RequestParam("feedback_id") String feedbackId
    ) {
        return feedbackService.getFeedbackOnlyWithManagerService(request.getAttribute("uuid").toString(), feedbackId);
    }

    /**根据Feedback的ID获取唯一符合要求的Feedback实体（个人用）
     * @param request 管理员登录信息
     * @param feedbackId Feedback的ID
     * @return 唯一符合条件的Feedback实体*/
    @GetMapping("/only_with_me")
    public ResponseEntity<J> getFeedbackOnlyWithMeControl(
            HttpServletRequest request,
            @RequestParam("feedback_id") String feedbackId
    ) {
        return feedbackService.getFeedbackOnlyWithMeService(request.getAttribute("uuid").toString(), feedbackId);
    }

    /**上传投诉表单
     * @param requestModel Feedback实体
     * @return 上传是否成功*/
    @IgnoreAuth
    @PostMapping("/upload")
    public ResponseEntity<J> uploadFeedbackControl(@RequestBody FeedbackFormModel requestModel) {
        return feedbackService.uploadFeedbackService(requestModel);
    }

    /**修改投诉表单
     * @param request 管理员登录信息
     * @param requestModel Feedback实体
     * @return 修改是否成功*/
    @PutMapping("/update")
    public ResponseEntity<J> updateFeedbackControl(
            HttpServletRequest request,
            @RequestBody FeedbackFormModel requestModel) {
        return feedbackService.updateFeedbackService(request.getAttribute("uuid").toString(), requestModel);
    }

    /**删除投诉表单
     * @param request 管理员登录信息
     * @param feedbackId Feedback的ID
     * @return 删除是否成功*/
    @DeleteMapping("/delete")
    public ResponseEntity<J> deleteFeedbackControl(
            HttpServletRequest request,
            @RequestParam("feedback_id") String feedbackId
    ) {
         return feedbackService.deleteFeedbackService(request.getAttribute("uuid").toString(), feedbackId);
    }
}
