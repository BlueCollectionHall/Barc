package com.miaoyu.barc.api.work.service;

import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.api.mapper.ClubMapper;
import com.miaoyu.barc.api.mapper.SchoolMapper;
import com.miaoyu.barc.api.mapper.StudentMapper;
import com.miaoyu.barc.api.model.SchoolClubModel;
import com.miaoyu.barc.api.model.SchoolModel;
import com.miaoyu.barc.api.model.StudentModel;
import com.miaoyu.barc.api.work.enumeration.WorkStatusEnum;
import com.miaoyu.barc.api.work.enumeration.WorkReviewStatusEnum;
import com.miaoyu.barc.api.work.mapper.WorkCategoryMapper;
import com.miaoyu.barc.api.work.mapper.WorkCoverImageMapper;
import com.miaoyu.barc.api.work.mapper.WorkImageMapper;
import com.miaoyu.barc.api.work.mapper.WorkLikeMapper;
import com.miaoyu.barc.api.work.mapper.WorkMapper;
import com.miaoyu.barc.api.work.mapper.WorkReviewMapper;
import com.miaoyu.barc.api.work.model.WorkCategoryModel;
import com.miaoyu.barc.api.work.model.WorkCoverImageModel;
import com.miaoyu.barc.api.work.model.WorkEditDetailDto;
import com.miaoyu.barc.api.work.model.WorkImageModel;
import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.api.work.model.PublicWorkView;
import com.miaoyu.barc.api.work.model.WorkReviewModel;
import com.miaoyu.barc.api.work.model.entity.WorkEntity;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.response.ChangeR;
import com.miaoyu.barc.response.ErrorR;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.response.UserR;
import com.miaoyu.barc.user.enumeration.OfficialAccountEnum;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.user.service.OfficialAccountService;
import com.miaoyu.barc.utils.GenerateUUID;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.JwtService;
import com.miaoyu.barc.utils.dto.PageRequestDto;
import com.miaoyu.barc.utils.dto.PageResultDto;
import com.miaoyu.barc.utils.pojo.PageInitPojo;
import com.miaoyu.barc.utils.tencent.cos.CosBucketConfigEnum;
import com.miaoyu.barc.utils.tencent.cos.CosService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.formula.functions.T;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class WorkService {
    @Autowired private com.miaoyu.barc.api.service.StudentService studentService;
    @Autowired private com.miaoyu.barc.api.file.service.ObjectStorageService objectStorage;
    @Autowired private com.miaoyu.barc.api.service.CosGcService garbage;
    @Autowired private com.miaoyu.barc.utils.tencent.cos.CosConfig cosConfig;
    @Autowired private com.miaoyu.barc.api.mapper.CategoryMapper categoryMapper;

    @Autowired
    private WorkMapper workMapper;
    @Autowired
    private WorkAttributionService workAttributionService;
    @Autowired
    private OfficialAccountService officialAccountService;
    @Autowired
    private StudentMapper studentMapper;
    @Autowired
    private SchoolMapper schoolMapper;
    @Autowired
    private ClubMapper clubMapper;
    @Autowired
    private WorkImageMapper workImageMapper;
    @Autowired
    private WorkCoverImageMapper workCoverImageMapper;
    @Autowired
    private CosService cosService;
    @Autowired
    private WorkCategoryMapper workCategoryMapper;
    @Autowired
    private WorkLikeMapper workLikeMapper;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private WorkViewService workViewService;
    @Autowired
    private WorkReviewMapper workReviewMapper;

    public ResponseEntity<J> getNewWorkService(int day, boolean isStudentList) {
        if (day < 1 || day > 365) throw failure("最新天数须在1到365之间");
        if (isStudentList) {
            List<StudentModel> students = studentMapper.selectByRecentWork(day);
            students.forEach(studentService::resolveImages);
            return ResponseEntity.ok(new ResourceR().resourceSuch(true, students));
        }
        return getPublicLegacyList(WorkStatusEnum.PUBLIC, Map.of("day", day), 1, 100);
    }

    /** 公共作品列表只维护一套筛选与投影；旧数组入口保留分页参数，完整遍历使用分页入口。 */
    public ResponseEntity<J> getPublicLegacyList(WorkStatusEnum status, Map<String, Object> filters, Integer page, Integer size) {
        if (status != WorkStatusEnum.PUBLIC) return publicListStatusError();
        PageInitPojo p = new PageInitPojo(page, size);
        List<WorkModel> rows = workMapper.selectByPage(WorkStatusEnum.PUBLIC, p.getOffset(), p.getPageSize(), publicConditions(filters));
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, loopSignatureWorkCover(rows).stream().map(PublicWorkView::from).toList()));
    }

    public ResponseEntity<J> getWorksAllService(WorkStatusEnum status) { return getPublicLegacyList(status, Map.of(), 1, 100); }

    public ResponseEntity<J> getWorkByPageService(WorkStatusEnum status, PageRequestDto dto) {
        if (status != WorkStatusEnum.PUBLIC) return publicListStatusError();
        if (dto == null) dto = new PageRequestDto();
        PageInitPojo p = new PageInitPojo(dto);
        Map<String, Object> filters = publicConditions(dto.getParams());
        List<WorkModel> rows = workMapper.selectByPage(WorkStatusEnum.PUBLIC, p.getOffset(), p.getPageSize(), filters);
        long total = workMapper.countByPage(WorkStatusEnum.PUBLIC, filters);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, new PageResultDto<>(total,
            loopSignatureWorkCover(rows).stream().map(PublicWorkView::from).toList(), p.getPageNum(), p.getPageSize(),
            Math.max(1, (int) Math.ceil((double) total / p.getPageSize())))));
    }

    public ResponseEntity<J> getWorksByCategoryService(String categoryId, WorkStatusEnum status, PageRequestDto dto) {
        if (dto == null) dto = new PageRequestDto();
        Map<String, Object> filters = dto.getParams() == null ? new HashMap<>() : new HashMap<>(dto.getParams());
        filters.put("category_id", categoryId); dto.setParams(filters);
        return getWorkByPageService(status, dto);
    }
    public ResponseEntity<J> getWorksBySchoolService(String id, WorkStatusEnum status) { return getPublicLegacyList(status, Map.of("school_id", id), 1, 100); }
    public ResponseEntity<J> getWorksByClubService(String id, WorkStatusEnum status) { return getPublicLegacyList(status, Map.of("club_id", id), 1, 100); }
    public ResponseEntity<J> getWorksByStudentService(String id, WorkStatusEnum status) { return getPublicLegacyList(status, Map.of("student_id", id), 1, 100); }

    public ResponseEntity<J> getWorksByMeService(String uuid, WorkStatusEnum status) { return getWorksByMeService(uuid, status, null, null); }
    public ResponseEntity<J> getWorksByMeService(String uuid, WorkStatusEnum status, Map<String, Object> filters) { return getWorksByMeService(uuid, status, null, filters); }
    public ResponseEntity<J> getWorksByMeService(String uuid, WorkStatusEnum status, WorkReviewStatusEnum review, Map<String, Object> filters) {
        Map<String, Object> condition = ownerConditions(uuid, filters, new PageInitPojo(1, 100));
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, loopSignatureWorkEntityCover(workMapper.selectByUuidWithFilters(uuid, status, review, condition))));
    }

    /** 本人可见范围固定来自认证身份；客户端筛选不能替换作者UUID。 */
    public ResponseEntity<J> getMyWorkPage(String uuid, WorkStatusEnum status, WorkReviewStatusEnum review, PageRequestDto dto) {
        if (dto == null) dto = new PageRequestDto();
        PageInitPojo p = new PageInitPojo(dto);
        Map<String, Object> condition = ownerConditions(uuid, dto.getParams(), p);
        long total = workMapper.countByUuidWithFilters(uuid, status, review, condition);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, new PageResultDto<>(total,
            loopSignatureWorkEntityCover(workMapper.selectByUuidWithFilters(uuid, status, review, condition)), p.getPageNum(), p.getPageSize(),
            Math.max(1, (int) Math.ceil((double) total / p.getPageSize())))));
    }

    public ResponseEntity<J> getWorksByUuidService(String uuid, WorkStatusEnum status) { return getWorksByUuidService(uuid, status, null); }
    public ResponseEntity<J> getWorksByUuidService(String uuid, WorkStatusEnum status, Map<String, Object> filters) {
        Map<String, Object> condition = filters == null ? new HashMap<>() : new HashMap<>(filters);
        condition.put("author_uuid", uuid);
        return getPublicLegacyList(status, condition, 1, 100);
    }
    public ResponseEntity<J> getWorksByUsernameService(String username, WorkStatusEnum status) { return getPublicLegacyList(status, Map.of("author_username", username), 1, 100); }

    private Map<String, Object> ownerConditions(String uuid, Map<String, Object> filters, PageInitPojo page) {
        Map<String, Object> condition = publicConditions(filters);
        condition.put("author_uuid", uuid); condition.put("offset", page.getOffset()); condition.put("limit", page.getPageSize());
        return condition;
    }

    private Map<String, Object> publicConditions(Map<String, Object> input) {
        Map<String, Object> result = new HashMap<>();
        if (input == null) return result;
        for (String key : List.of("keyword", "school", "club", "student", "school_id", "club_id", "student_id", "category_id", "author_uuid", "uploader_uuid", "author_username", "uploader_username")) {
            Object value = input.get(key);
            if (value == null) continue;
            if (!(value instanceof String text) || text.length() > 100) throw failure("作品筛选字段无效");
            if (!text.isBlank()) result.put(key, text.trim());
        }
        if (input.get("day") != null) {
            try { int day = Integer.parseInt(input.get("day").toString()); if (day < 1 || day > 365) throw failure("天数须在1到365之间"); result.put("day", day); }
            catch (NumberFormatException invalid) { throw failure("天数格式无效"); }
        }
        return result;
    }

    private ResponseEntity<J> publicListStatusError() {
        return ResponseEntity.ok(new ErrorR().normal("公开接口只能查询已审核通过的公开作品"));
    }
    public ResponseEntity<J> getWorksByIdService(String workId) {
        return getWorksByIdService(null, workId);
    }

    public ResponseEntity<J> getWorksByIdService(HttpServletRequest request, String workId) {
        WorkModel work = workMapper.selectById(workId);
        if (Objects.isNull(work)) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        return switch (work.getStatus()) {
            case PRIVATE -> ResponseEntity.ok(new ErrorR().normal("私有作品无法访问"));
            case BAN -> ResponseEntity.ok(new ErrorR().normal("作品已被封禁"));
            case OFF -> ResponseEntity.ok(new ErrorR().normal("作品已被下架"));
            case DELETED -> ResponseEntity.ok(new ErrorR().normal("作品已被删除"));
            default -> {
                if (work.getReview_status() != WorkReviewStatusEnum.APPROVED) {
                    yield ResponseEntity.ok(new ErrorR().normal("作品尚未通过审核"));
                }
                // 预签名work封面图URL
                WorkCoverImageModel coverImageModel = workCoverImageMapper.selectByWorkId(workId);
                if (Objects.isNull(coverImageModel)) {
                    yield ResponseEntity.ok(new ErrorR().normal("作品封面图不存在"));
                }
                 String signedCoverImageUrl = cosService.generateSignedUrl(coverImageModel.getObject_key(), new Date(System.currentTimeMillis() + 60 * 1000), CosBucketConfigEnum.image);
                 work.setCover_image(signedCoverImageUrl);
                 work.setLiked_by_current_user(isLikedByCurrentUser(request, workId));
                 try {
                     workViewService.recordViewIfNeeded(request, work);
                 } catch (Exception e) {
                     log.error("Unexpected work view recording failure for work_id={}", workId, e);
                 }
                 yield ResponseEntity.ok(new ResourceR().resourceSuch(true, PublicWorkView.from(work)));
             }
         };
    }

    private boolean isLikedByCurrentUser(HttpServletRequest request, String workId) {
        if (request == null) return false;
        String token = request.getHeader("Authorization");
        if (token == null || token.isBlank()) return false;
        try {
            J jwtResult = jwtService.jwtParser(token);
            if (jwtResult == null || jwtResult.getCode() != 0 || jwtResult.getData() == null) return false;
            return workLikeMapper.selectByWorkIdAndUserUuid(workId, jwtResult.getData().toString()) != null;
        } catch (Exception e) {
            log.debug("Optional work detail token parsing failed; treating request as anonymous.", e);
            return false;
        }
    }
    public ResponseEntity<J> getWorkByIdWithMeService(String uuid, String workId) {
        WorkModel work = workMapper.selectById(workId);
        if (Objects.isNull(work)) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        if (isWorkOwner(uuid, work)) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(true, work));
        }
        return ResponseEntity.ok(new UserR().uuidMismatch());
    }

    /** 获取作者侧编辑详情：复用管理端图片语义，但只能作品作者/收录者读取 */
    public ResponseEntity<J> getOwnerWorkEditDetail(String uuid, String workId) {
        WorkModel work = workMapper.selectById(workId);
        if (Objects.isNull(work)) return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        if (!isWorkOwner(uuid, work)) return ResponseEntity.ok(new UserR().uuidMismatch());
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, buildWorkEditDetailDto(work)));
    }

    /** 作者侧纯文字保存：图片由独立表维护，禁止回写 legacy work.cover_image 字段 */
    @Transactional
    public ResponseEntity<J> updateOwnerWorkContent(String uuid, WorkModel requestModel) {
        WorkModel work = workMapper.selectByIdForUpdate(requestModel.getId());
        if (Objects.isNull(work)) return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        if (!isWorkOwner(uuid, work)) return ResponseEntity.ok(new UserR().uuidMismatch());
        validateContentUpdate(work, requestModel);
        if (!workMapper.updateText(requestModel)) throw failure("作品保存失败");
        submitForReview(work.getId());
        return ResponseEntity.ok(new ChangeR().udu(true, 3));
    }

    /**
     * 驳回作品由作者确认修改完成后显式再次提审。
     * 仅允许当前作者或收录者操作，并通过 REJECTED 条件更新避免重复提审。
     */
    @Transactional
    public ResponseEntity<J> resubmitRejectedWork(String uuid, String workId) {
        WorkModel work = workMapper.selectByIdForUpdate(workId);
        if (Objects.isNull(work)) return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        if (!isWorkOwner(uuid, work)) return ResponseEntity.ok(new UserR().uuidMismatch());

        WorkReviewModel review = workReviewMapper.selectByWorkId(workId);
        if (review == null) return ResponseEntity.ok(new ErrorR().normal("作品审核记录不存在"));
        if (review.getStatus() != WorkReviewStatusEnum.REJECTED) {
            return ResponseEntity.ok(new ErrorR().normal("只有审核未通过的作品可以再次提审"));
        }
        if (!workReviewMapper.resubmitRejected(workId)) {
            return ResponseEntity.ok(new ErrorR().normal("审核状态已变化，请刷新后重试"));
        }
        return ResponseEntity.ok(new ChangeR().udu(true, 3));
    }

    /**
     * 作者侧可见性切换只负责 PUBLIC/PRIVATE。BAN/OFF/DELETED/恢复必须走管理端审核路径，避免作者越权恢复处罚状态。
     */
    @Transactional
    public ResponseEntity<J> updateOwnerWorkVisibility(String uuid, String workId, WorkStatusEnum requestedStatus) {
        WorkModel work = workMapper.selectByIdForUpdate(workId);
        if (Objects.isNull(work)) return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        if (!isWorkOwner(uuid, work)) return ResponseEntity.ok(new UserR().uuidMismatch());
        if (requestedStatus != WorkStatusEnum.PUBLIC && requestedStatus != WorkStatusEnum.PRIVATE) {
            return ResponseEntity.ok(new ErrorR().normal("作者只能切换公开或私有状态"));
        }
        if (work.getStatus() != WorkStatusEnum.PUBLIC && work.getStatus() != WorkStatusEnum.PRIVATE) {
            return ResponseEntity.ok(new ErrorR().normal("作品处于管理状态，不能由作者切换公开/私有"));
        }
        work.setStatus(requestedStatus);
        if (!workMapper.updateStatus(workId, requestedStatus, null)) throw failure("可见性保存失败");
        return ResponseEntity.ok(new ChangeR().udu(true, 3));
    }

    /** 作者侧替换封面：只更新 work_cover_image 指针，不再把图片地址写回 work 表 */
    @Transactional
    public ResponseEntity<J> replaceOwnerWorkCover(String uuid, String workId, MultipartFile coverImage) {
        WorkModel work = workMapper.selectByIdForUpdate(workId);
        if (Objects.isNull(work)) return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        if (!isWorkOwner(uuid, work)) return ResponseEntity.ok(new UserR().uuidMismatch());

        String newObjectKey = objectStorage.upload(uuid, coverImage, "uploads/" + uuid + "/work_images/", 10L * 1024 * 1024);
        objectStorage.bind(uuid, newObjectKey);
        WorkCoverImageModel oldCover = workCoverImageMapper.selectByWorkId(workId);
        boolean changed;
        if (oldCover == null) {
            WorkCoverImageModel cover = new WorkCoverImageModel();
            cover.setId(new GenerateUUID().getUuid36l());
            cover.setWork_id(workId);
            cover.setObject_key(newObjectKey);
            changed = workCoverImageMapper.insert(cover);
        } else {
            String oldObjectKey = oldCover.getObject_key();
            oldCover.setObject_key(newObjectKey);
            changed = workCoverImageMapper.update(oldCover);
            if (changed) garbage.enqueue(cosConfig.getImage().getBucketName(), oldObjectKey);
        }
        if (!changed) {
            throw failure("封面记录保存失败");
        }
        submitForReview(workId);
        return ResponseEntity.ok(new ChangeR().udu(true, 3));
    }

    private boolean isWorkOwner(String uuid, WorkModel work) {
        return Objects.equals(work.getAuthor(), uuid) || Objects.equals(work.getUploader(), uuid);
    }

    /** 两端共用编辑详情构造，读取权限由各入口独立校验。 */
    WorkEditDetailDto buildWorkEditDetailDto(WorkModel work) {
        String workId = work.getId();
        String coverUrl = "";
        WorkCoverImageModel cover = workCoverImageMapper.selectByWorkId(workId);
        if (cover != null) {
            coverUrl = cosService.generateSignedUrl(cover.getObject_key(),
                    new Date(System.currentTimeMillis() + 60 * 1000), CosBucketConfigEnum.image);
        }

        List<WorkImageModel> images = workImageMapper.selectByWorkId(workId);
        List<String> contentUrls = List.of();
        List<WorkEditDetailDto.ContentImageDto> contentImages = List.of();
        if (!images.isEmpty()) {
            // 按 sort 生成 key 列表，确保批量签名返回值与编辑页画廊顺序一致。
            List<WorkImageModel> sortedImages = images.stream().sorted(Comparator.comparing(WorkImageModel::getSort)).toList();
            List<String> keys = sortedImages.stream().map(WorkImageModel::getObject_key).toList();
            contentUrls = cosService.generateBatchSignedUrl(keys,
                    new Date(System.currentTimeMillis() + 60 * 1000), CosBucketConfigEnum.image);
            List<String> signedUrls = contentUrls;
            contentImages = new ArrayList<>();
            for (int i = 0; i < sortedImages.size(); i++) {
                WorkImageModel image = sortedImages.get(i);
                WorkEditDetailDto.ContentImageDto dto = new WorkEditDetailDto.ContentImageDto();
                dto.setId(image.getId());
                dto.setSort(image.getSort());
                dto.setUrl(signedUrls.get(i));
                contentImages.add(dto);
            }
        }

        String uploaderNickname = workAttributionService.resolveUploaderNickname(work);
        // author 始终是平台内归属账号；未认领作品会解析为当前绑定的官方账号
        // （OfficialAccountEnum.COLLECTION_ASSISTANT），而不是站外原作者署名。
        String authorDisplay = workAttributionService.resolvePlatformOwnerNickname(work);

        String schoolName = "", clubName = "", studentName = "";
        if (work.getStudent() != null && !work.getStudent().isEmpty()) {
            StudentModel student = studentMapper.selectById(work.getStudent());
            if (student != null) {
                studentName = student.getCn_name();
                if (student.getSchool() != null) {
                    SchoolModel school = schoolMapper.selectById(student.getSchool());
                    if (school != null) schoolName = school.getCn_name();
                }
                if (student.getClub() != null) {
                    SchoolClubModel club = clubMapper.selectById(student.getClub());
                    if (club != null) clubName = club.getCn_name();
                }
            }
        }

        WorkEditDetailDto dto = new WorkEditDetailDto();
        dto.setWork(work);
        dto.setCover_image_url(coverUrl);
        dto.setContent_image_urls(contentUrls);
        dto.setContent_images(contentImages);
        dto.setUploader_nickname(uploaderNickname);
        dto.setAuthor_display(authorDisplay);
        dto.setSchool_name(schoolName);
        dto.setClub_name(clubName);
        dto.setStudent_name(studentName);
        return dto;
    }

    @Transactional
    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check()})
    public ResponseEntity<J> uploadWorkService(String uuid, String categoryId, WorkModel requestModel, MultipartFile coverImage, MultipartFile[] files) {

        if (requestModel == null || requestModel.getTitle() == null || requestModel.getTitle().isBlank()
                || requestModel.getTitle().length() > 255 || requestModel.getIs_claim() == null
                || (requestModel.getContent() != null && requestModel.getContent().length() > 100000)
                || (requestModel.getDescription() != null && requestModel.getDescription().length() > 5000)) {
            throw failure("作品标题、认领类型或内容长度无效");
        }
        if (requestModel.getStudent() == null || studentMapper.selectById(requestModel.getStudent()) == null) throw failure("所属学生不存在");
        if (categoryId != null && !categoryId.isBlank() && categoryMapper.selectById(categoryId) == null) throw failure("作品分类不存在");
        if (files == null) files = new MultipartFile[0];
        if (files.length > 20) throw failure("一次最多上传 20 张内容图");
        // 在创建任何业务记录前校验全部文件，避免后续图片格式错误造成部分提交。
        com.miaoyu.barc.api.file.service.ImageInput.extension(coverImage, 10L * 1024 * 1024);
        for (MultipartFile file : files) com.miaoyu.barc.api.file.service.ImageInput.extension(file, 10L * 1024 * 1024);
        if (requestModel.getId() == null || requestModel.getId().isBlank()) requestModel.setId(new GenerateUUID().getUuid36l());
        if (!requestModel.getId().matches("[A-Za-z0-9_-]{1,100}") || workMapper.selectByIdForUpdate(requestModel.getId()) != null) throw failure("作品 ID 无效或已被占用");
        // 作者和收录者只由当前身份及受控官方账号决定，忽略请求中的协作者 UUID。
        requestModel.setUploader(uuid);
        if (Boolean.TRUE.equals(requestModel.getIs_claim())) requestModel.setAuthor(uuid);
        else {
            if (requestModel.getAuthor_nickname() == null || requestModel.getAuthor_nickname().isBlank()) throw failure("收录作品请标注站外原作者昵称");
            String official = officialAccountService.findBoundUuid(OfficialAccountEnum.COLLECTION_ASSISTANT);
            if (official == null) throw failure("官方收录账号尚未配置，请联系馆长");
            requestModel.setAuthor(official);
        }
        requestModel.setBanner_image(""); requestModel.setCover_image("");
        requestModel.setStatus(requestModel.getStatus() == WorkStatusEnum.PRIVATE ? WorkStatusEnum.PRIVATE : WorkStatusEnum.PUBLIC);
        if (!workMapper.insert(requestModel) || !workReviewMapper.insertPending(requestModel.getId())) throw failure("作品或审核记录保存失败");
        if (categoryId != null && !categoryId.isBlank()) {
            WorkCategoryModel category = new WorkCategoryModel(); category.setId(new GenerateUUID().getUuid36l());
            category.setWork_id(requestModel.getId()); category.setCategory_id(categoryId);
            if (!workCategoryMapper.insert(category)) throw failure("作品分类保存失败");
        }
        String prefix = "uploads/" + uuid + "/work_images/";
        String coverKey = objectStorage.upload(uuid, coverImage, prefix, 10L * 1024 * 1024);
        objectStorage.bind(uuid, coverKey);
        WorkCoverImageModel cover = new WorkCoverImageModel(); cover.setId(new GenerateUUID().getUuid36l());
        cover.setWork_id(requestModel.getId()); cover.setObject_key(coverKey);
        if (!workCoverImageMapper.insert(cover)) throw failure("封面记录保存失败");
        for (int index = 0; index < files.length; index++) {
            String key = objectStorage.upload(uuid, files[index], prefix, 10L * 1024 * 1024);
            objectStorage.bind(uuid, key);
            WorkImageModel image = new WorkImageModel(); image.setId(new GenerateUUID().getUuid36l());
            image.setWork_id(requestModel.getId()); image.setSort(index); image.setObject_key(key);
            if (!workImageMapper.insert(image)) throw failure("内容图片记录保存失败");
        }
        // 任何异常均向外抛出触发事务回滚；已上传对象有独立持久化记录，按未引用对象回收。
        return ResponseEntity.ok(new ChangeR().udu(true, 1));
    }

    private com.miaoyu.barc.exception.ApiException failure(String message) {
        return new com.miaoyu.barc.exception.ApiException(new ErrorR().normal(message));
    }
    // 旧全字段更新与物理删除已收敛至 WorkCompatibilityService / WorkDeletionService，
    // 避免其他服务继续误用绕过提审、审计或真实归属校验的实现。

    /** 批量签名作品封面图，供本包内复用 */
    List<WorkModel> loopSignatureWorkCover(List<WorkModel> works) {
        if (works == null || works.isEmpty()) return works;
        // 1. 批量查询所有作品的封面图
        List<String> workIds = works.stream().map(WorkModel::getId).toList();
        List<WorkCoverImageModel> coverImages = workCoverImageMapper.selectByWorkIds(workIds);
        
        // 2. 收集所有 object_key 用于批量签名
        Map<String, String> workIdToObjectKey = coverImages.stream()
                .collect(Collectors.toMap(
                        WorkCoverImageModel::getWork_id,
                        WorkCoverImageModel::getObject_key
                ));
        
        List<String> objectKeys = new ArrayList<>(workIdToObjectKey.values());
        
        // 3. 批量生成签名 URL（使用并行流优化）
        Date expiration = new Date(System.currentTimeMillis() + 60 * 1000);
        List<String> signedUrls = cosService.generateBatchSignedUrl(objectKeys, expiration, CosBucketConfigEnum.image);
        
        // 4. 建立 objectKey -> signedUrl 的映射
        Map<String, String> objectKeyToSignedUrl = new HashMap<>();
        for (int i = 0; i < objectKeys.size(); i++) {
            objectKeyToSignedUrl.put(objectKeys.get(i), signedUrls.get(i));
        }
        
        // 5. 为每个作品设置签名后的封面图 URL
        for (WorkModel model : works) {
            String objectKey = workIdToObjectKey.get(model.getId());
            if (objectKey != null) {
                model.setCover_image(objectKeyToSignedUrl.get(objectKey));
            }
        }
        return works;
    }

    /**
     * 展示内容变化后的审核处理：新作品补建记录，已通过/审核中作品转入或刷新待审。
     * 已驳回作品保持 REJECTED 和拒绝原因，等待作者显式点击“再次提审”。
     */
    @Transactional
    public void submitForReview(String workId) {
        // 所有调用方在改动前已锁定父作品；版本和审核状态作为一个事务提交。
        if (!workMapper.touchContent(workId)) throw failure("作品版本保存失败");
        WorkReviewModel review = workReviewMapper.selectByWorkId(workId);
        if (review == null) {
            if (!workReviewMapper.insertPending(workId)) throw failure("审核记录保存失败");
        } else if (review.getStatus() != WorkReviewStatusEnum.REJECTED) {
            if (!workReviewMapper.submitForReview(workId)) throw failure("提审保存失败");
        }
    }

    /** 兼容服务调用也执行字段校验；认领/身份字段不进入正文 SQL。 */
    void validateContentUpdate(WorkModel existing, WorkModel request) {
        com.miaoyu.barc.utils.RequestInput.required(request.getTitle(), "标题", 255);
        com.miaoyu.barc.utils.RequestInput.optional(request.getContent(), "正文", 100000);
        com.miaoyu.barc.utils.RequestInput.optional(request.getDescription(), "简介", 5000);
        com.miaoyu.barc.utils.RequestInput.optional(request.getAuthor_nickname(), "作者署名", 255);
        if (request.getExpected_revision() != null && !request.getExpected_revision().equals(existing.getContent_revision())) {
            throw failure("作品已被修改，请刷新后重试");
        }
        if (request.getStudent() == null) request.setStudent(existing.getStudent());
        else if (studentMapper.selectById(request.getStudent()) == null) throw failure("所属学生不存在");
    }

    /** 作者列表使用 WorkEntity，单独签名以避免改动 mapper 返回契约 */
    private List<WorkEntity> loopSignatureWorkEntityCover(List<WorkEntity> works) {
        if (works == null || works.isEmpty()) return works;
        List<String> workIds = works.stream().map(WorkEntity::getId).toList();
        List<WorkCoverImageModel> coverImages = workCoverImageMapper.selectByWorkIds(workIds);
        Map<String, String> workIdToObjectKey = coverImages.stream()
                .collect(Collectors.toMap(WorkCoverImageModel::getWork_id, WorkCoverImageModel::getObject_key));
        List<String> objectKeys = new ArrayList<>(workIdToObjectKey.values());
        List<String> signedUrls = cosService.generateBatchSignedUrl(objectKeys,
                new Date(System.currentTimeMillis() + 60 * 1000), CosBucketConfigEnum.image);
        Map<String, String> objectKeyToSignedUrl = new HashMap<>();
        for (int i = 0; i < objectKeys.size(); i++) {
            objectKeyToSignedUrl.put(objectKeys.get(i), signedUrls.get(i));
        }
        for (WorkEntity work : works) {
            String objectKey = workIdToObjectKey.get(work.getId());
            if (objectKey != null) work.setCover_image(objectKeyToSignedUrl.get(objectKey));
        }
        return works;
    }
}
