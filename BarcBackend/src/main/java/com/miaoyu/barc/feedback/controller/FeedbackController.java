package com.miaoyu.barc.feedback.controller;

import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.feedback.enumeration.FeedbackStatusEnum;
import com.miaoyu.barc.feedback.enumeration.FeedbackTypeEnum;
import com.miaoyu.barc.feedback.model.FeedbackFormModel;
import com.miaoyu.barc.feedback.model.FeedbackCreateRequest;
import com.miaoyu.barc.feedback.model.FeedbackUpdateRequest;
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
     * @param count 获取数量，默认及零值最多 100 条；完整记录通过 page 入口读取
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
        return feedbackService.getLegacyList(request.getAttribute("uuid").toString(), typeEnum, Boolean.TRUE.equals(isManager), count);
    }

    /** 新分页入口兼顾本人记录和分类管理；服务层再次检查治理权限。 */
    @GetMapping("/page")
    public ResponseEntity<J> getPage(HttpServletRequest request, @RequestParam FeedbackTypeEnum type,
            @RequestParam(required = false) FeedbackStatusEnum status, @RequestParam(defaultValue = "false") boolean is_manager,
            @RequestParam(defaultValue = "1") Integer page, @RequestParam(defaultValue = "20") Integer size) {
        return feedbackService.getPage(request.getAttribute("uuid").toString(), type, status, is_manager, page, size);
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
    public ResponseEntity<J> uploadFeedbackControl(HttpServletRequest request, @RequestBody FeedbackCreateRequest requestModel) {
        // 认证拦截器支持公开接口的可选身份；连接地址不采信客户端伪造的转发头。
        return feedbackService.uploadFeedbackService((String) request.getAttribute("uuid"), request.getRemoteAddr(), requestModel);
    }

    /**修改投诉表单
     * @param request 管理员登录信息
     * @param requestModel Feedback实体
     * @return 修改是否成功*/
    @PutMapping("/update")
    public ResponseEntity<J> updateFeedbackControl(
            HttpServletRequest request,
            @RequestBody FeedbackUpdateRequest requestModel) {
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
