package com.miaoyu.barc.api.work.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.annotation.SuchWorkAnno;
import com.miaoyu.barc.api.work.enumeration.WorkStatusEnum;
import com.miaoyu.barc.api.work.enumeration.WorkReviewStatusEnum;
import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.api.work.service.WorkService;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.dto.PageRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/work")
public class WorkController {
    @Autowired
    private WorkService workService;

    /**获取最新作品信息
     * @param day 最新天数
     * @param isStudentList 是否将作品信息改成去重学生信息
     * @return List类型中要么是Work实体要么是Student实体*/
    @IgnoreAuth
    @GetMapping("/new")
    public ResponseEntity<J> getNewWorkControl(
            @RequestParam("day") Integer day,
            @RequestParam("is_student_list") Boolean isStudentList
    ) {
        return workService.getNewWorkService(day, isStudentList);
    }
    /**获取全部Work实体
     * @return List类型中包含所有Work实体*/
    @IgnoreAuth
    @GetMapping("/all")
    public ResponseEntity<J> getWorksAllControl(
            @RequestParam("status") WorkStatusEnum statusEnum
    ) {
        return workService.getWorksAllService(statusEnum);
    }
    /**
     * 分页获取符合条件的work实体
     * @param statusEnum 作品状态
     * @param pageRequestDto 分页信息以及检索条件
     * @return Page分页信息中包含List类型Work简易信息实体
     * */
    @IgnoreAuth
    @PostMapping("/works_by_page")
    public ResponseEntity<J> getWorksByPageControl(
            @RequestParam("status") WorkStatusEnum statusEnum,
            @RequestBody PageRequestDto pageRequestDto
    ) {
        return workService.getWorkByPageService(statusEnum, pageRequestDto);
    }
    /**根据层级分类id获取所有符合条件的work实体
     * @param categoryId 层级分类id
     * @return List类型中包含所有work实体*/
    @IgnoreAuth
    @PostMapping("/works_by_category")
    public ResponseEntity<J> getWorksByCategoryControl(
            @RequestParam("status") WorkStatusEnum statusEnum,
            @RequestParam("category_id") String categoryId,
            @RequestBody PageRequestDto pageRequestDto
    ) {
        return workService.getWorksByCategoryService(categoryId, statusEnum, pageRequestDto);
    }
    /**根据school_id获取所有符合条件的work实体
     * @param schoolId school_id
     * @return List类型中包含所有符合要求的work实体*/
    @IgnoreAuth
    @GetMapping("/works_by_school")
    public ResponseEntity<J> getWorksBySchoolControl(
            @RequestParam("status") WorkStatusEnum statusEnum,
            @RequestParam("school_id") String schoolId
    ) {
        return workService.getWorksBySchoolService(schoolId, statusEnum);
    }
    /**根据club_id获取所有符合条件的work实体
     * @param clubId club_id
     * @return List类型中包含所有符合要求的work实体*/
    @IgnoreAuth
    @GetMapping("/works_by_club")
    public ResponseEntity<J> getWorksByClubControl(
            @RequestParam("status") WorkStatusEnum statusEnum,
            @RequestParam("club_id") String clubId
    ) {
        return workService.getWorksByClubService(clubId, statusEnum);
    }
    /**根据student_id获取所有符合条件的work实体
     * @param studentId student_id
     * @return List类型中包含所有符合要求的work实体*/
    @IgnoreAuth
    @GetMapping("/works_by_student")
    public ResponseEntity<J> getWorksByStudentControl(
            @RequestParam("status") WorkStatusEnum statusEnum,
            @RequestParam("student_id") String studentId
    ) {
        return workService.getWorksByStudentService(studentId, statusEnum);
    }
    /**根据已登录的用户UUID获取符合发布者条件的work实体
     * @return List类型中包含所有符合条件的work实体*/
    @GetMapping("/works_by_me")
    public ResponseEntity<J> getWorksByMeControl(
            HttpServletRequest request,
            @RequestParam(value = "status", required = false) WorkStatusEnum statusEnum,
            @RequestParam(value = "review_status", required = false) WorkReviewStatusEnum reviewStatus,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "school", required = false) String school,
            @RequestParam(value = "club", required = false) String club,
            @RequestParam(value = "student", required = false) String student
    ) {
        return workService.getWorksByMeService(request.getAttribute("uuid").toString(), statusEnum, reviewStatus, buildOwnerListFilters(keyword, school, club, student));
    }
    /**根据已知UUID获取符合发布者条件的work实体
     * @param uuid uuid
     * @return List类型中包含所有符合条件的work实体*/
    @IgnoreAuth
    @GetMapping("/works_by_uuid")
    public ResponseEntity<J> getWorksByUuidControl(
            @RequestParam("status") WorkStatusEnum statusEnum,
            @RequestParam("uuid") String uuid,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "school", required = false) String school,
            @RequestParam(value = "club", required = false) String club,
            @RequestParam(value = "student", required = false) String student
    ) {
        return workService.getWorksByUuidService(uuid, statusEnum, buildOwnerListFilters(keyword, school, club, student));
    }

    private Map<String, Object> buildOwnerListFilters(String keyword, String school, String club, String student) {
        Map<String, Object> filters = new HashMap<>();
        filters.put("keyword", keyword);
        filters.put("school", school);
        filters.put("club", club);
        filters.put("student", student);
        return filters;
    }
    /**根据已知Username获取符合发布者条件的work实体
     * @return List类型中包含所有符合条件的work实体*/
    @IgnoreAuth
    @PostMapping("/works_by_username")
    public ResponseEntity<J> getWorksByUsernameControl(
            @RequestParam("status") WorkStatusEnum statusEnum,
//            @RequestParam("username") String username,
            @RequestBody PageRequestDto pageRequestDto
    ) {
        return workService.getWorkByPageService(statusEnum, pageRequestDto);
    }
    /**根据work_id获取唯一符合条件的work实体
     * @param workId work_id
     * @return 唯一符合条件的work实体*/
    @IgnoreAuth
    @GetMapping("/only")
    @SuchWorkAnno(selectType = "id", index = 1)
    public ResponseEntity<J> getWorkOnlyControl(
            HttpServletRequest request,
            @RequestParam("work_id") String workId
    ) {
        return workService.getWorksByIdService(request, workId);
    }
    /**根据work_id和用户的登录信息强制获取work实体
     * @param workId work_id
     * @return 唯一符合条件的work实体*/
    @GetMapping("/only_by_me")
    @SuchWorkAnno(selectType = "id")
    public ResponseEntity<J> getWorkByIdWithMeControl(
            HttpServletRequest request,
            @RequestParam("work_id") String workId
    ) {
        return workService.getWorkByIdWithMeService(request.getAttribute("uuid").toString(), workId);
    }

    /** 作者侧编辑详情：返回签名图片和展示元数据，不影响管理端 edit-detail 合约 */
    @GetMapping("/edit-detail")
    public ResponseEntity<J> getOwnerWorkEditDetailControl(
            HttpServletRequest request,
            @RequestParam("work_id") String workId
    ) {
        return workService.getOwnerWorkEditDetail(request.getAttribute("uuid").toString(), workId);
    }

    /** 作者侧纯文字更新：图片走独立接口，避免误用 legacy 全量更新覆盖图片字段 */
    @PutMapping("/edit-update")
    public ResponseEntity<J> updateOwnerWorkContentControl(
            HttpServletRequest request,
            @RequestBody WorkModel requestModel
    ) {
        return workService.updateOwnerWorkContent(request.getAttribute("uuid").toString(), requestModel);
    }

    /** 审核未通过的作品由作者修改完成后显式再次提审。 */
    @PostMapping("/review/resubmit")
    public ResponseEntity<J> resubmitRejectedWorkControl(
            HttpServletRequest request,
            @RequestBody Map<String, String> body
    ) {
        return workService.resubmitRejectedWork(
                request.getAttribute("uuid").toString(),
                body.get("work_id"));
    }

    /**
     * 作者侧公开/私有切换：仅允许 PUBLIC <-> PRIVATE，禁止复用管理端状态接口处理封禁/下架/删除。
     */
    @PutMapping("/visibility")
    public ResponseEntity<J> updateOwnerWorkVisibilityControl(
            HttpServletRequest request,
            @RequestBody Map<String, String> body
    ) {
        return workService.updateOwnerWorkVisibility(
                request.getAttribute("uuid").toString(),
                body.get("work_id"),
                WorkStatusEnum.valueOf(body.get("status")));
    }

    /** 作者侧封面替换：只更新 work_cover_image 表 */
    @PutMapping(value = "/cover/replace", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<J> replaceOwnerWorkCoverControl(
            HttpServletRequest request,
            @RequestParam("work_id") String workId,
            @RequestPart("cover_image") MultipartFile coverImage
    ) {
        return workService.replaceOwnerWorkCover(request.getAttribute("uuid").toString(), workId, coverImage);
    }
    /**上传作品
     * @return 上传是否成功*/

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<J> uploadWorkControl(
            HttpServletRequest request,
            @RequestParam("form") String formJson,
            @RequestParam(value = "category_id", required = false) String categoryId,
            @RequestPart("cover_image") MultipartFile coverImage,
            @RequestPart(value = "files", required = false) MultipartFile[] files
            ) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        WorkModel requestModel = objectMapper.readValue(formJson, WorkModel.class);
        if (files == null || files.length == 0) files = new MultipartFile[0];
        return workService.uploadWorkService(request.getAttribute("uuid").toString(), categoryId, requestModel, coverImage, files);
    }
    /**修改作品（兼容维护者及以上维护性修改）
     * @param requestModel 作品的实体
     * @return 上传是否成功*/
    @PutMapping("/update")
    @SuchWorkAnno(selectType = "model")
    public ResponseEntity<J> updateWorkControl(
            HttpServletRequest request,
            @RequestBody WorkModel requestModel
    ) {
        return workService.updateWorkService(request.getAttribute("uuid").toString(), requestModel);
    }

    /**删除作品
     * @param workId 作品ID
     * @return 删除是否成功*/
    @DeleteMapping("/delete")
    @SuchWorkAnno(selectType = "id", index = 0)
    public ResponseEntity<J> deleteWorkControl(@RequestParam("work_id") String workId) {
        return workService.deleteWorkService(workId);
    }
}
