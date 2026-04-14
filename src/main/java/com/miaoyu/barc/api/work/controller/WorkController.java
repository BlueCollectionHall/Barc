package com.miaoyu.barc.api.work.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.barc.annotation.IgnoreAuth;
import com.miaoyu.barc.annotation.SuchWorkAnno;
import com.miaoyu.barc.api.work.enumeration.WorkStatusEnum;
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
    public ResponseEntity<J> getWorksByMeControl(HttpServletRequest request, @RequestParam("status") WorkStatusEnum statusEnum) {
        return workService.getWorksByMeService(request.getAttribute("uuid").toString(), statusEnum);
    }
    /**根据已知UUID获取符合发布者条件的work实体
     * @param uuid uuid
     * @return List类型中包含所有符合条件的work实体*/
    @IgnoreAuth
    @GetMapping("/works_by_uuid")
    public ResponseEntity<J> getWorksByUuidControl(
            @RequestParam("status") WorkStatusEnum statusEnum,
            @RequestParam("uuid") String uuid
    ) {
        return workService.getWorksByMeService(uuid, statusEnum);
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
    @SuchWorkAnno(selectType = "id", index = 0)
    public ResponseEntity<J> getWorkOnlyControl(
            @RequestParam("work_id") String workId
    ) {
        return workService.getWorksByIdService(workId);
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
