package com.miaoyu.barc.api.work.service;

import com.miaoyu.barc.annotation.RequireSelfOrPermissionAnno;
import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.api.mapper.StudentMapper;
import com.miaoyu.barc.api.model.StudentModel;
import com.miaoyu.barc.api.work.enumeration.WorkStatusEnum;
import com.miaoyu.barc.api.work.mapper.WorkCategoryMapper;
import com.miaoyu.barc.api.work.mapper.WorkCoverImageMapper;
import com.miaoyu.barc.api.work.mapper.WorkImageMapper;
import com.miaoyu.barc.api.work.mapper.WorkMapper;
import com.miaoyu.barc.api.work.model.WorkCategoryModel;
import com.miaoyu.barc.api.work.model.WorkCoverImageModel;
import com.miaoyu.barc.api.work.model.WorkImageModel;
import com.miaoyu.barc.api.work.model.WorkModel;
import com.miaoyu.barc.api.work.model.entity.WorkEntity;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.response.ChangeR;
import com.miaoyu.barc.response.ErrorR;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.response.UserR;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.utils.GenerateUUID;
import com.miaoyu.barc.utils.J;
import com.miaoyu.barc.utils.dto.PageRequestDto;
import com.miaoyu.barc.utils.dto.PageResultDto;
import com.miaoyu.barc.utils.minio.MinioObjects;
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

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
public class WorkService {
    @Autowired
    private WorkMapper workMapper;
    @Autowired
    private UserArchiveMapper userArchiveMapper;
    @Autowired
    private StudentMapper studentMapper;
    @Autowired
    private MinioObjects minioObjects;
    @Autowired
    private WorkImageMapper workImageMapper;
    @Autowired
    private WorkCoverImageMapper workCoverImageMapper;
    @Autowired
    private CosService cosService;
    @Autowired
    private WorkCategoryMapper workCategoryMapper;

    public ResponseEntity<J> getNewWorkService(int day, boolean isStudentList) {
        List<WorkEntity> works = workMapper.selectByDay(day, WorkStatusEnum.PUBLIC);
        if (isStudentList) {
            List<StudentModel> students = works.stream()
                    .map(WorkEntity::getStudent)
                    .distinct()
                    .map(studentMapper::selectById)
                    .filter(Objects::nonNull)
                    .toList();
            return ResponseEntity.ok(new ResourceR().resourceSuch(true, students));
        }
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, works));
    }

    public ResponseEntity<J> getWorksAllService(WorkStatusEnum statusEnum) {
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, workMapper.selectAll(statusEnum)));
    }

    public ResponseEntity<J> getWorkByPageService(WorkStatusEnum statusEnum, PageRequestDto dto) {
        PageInitPojo pageInit = new PageInitPojo(dto);
        Integer pageNum = pageInit.getPageNum();
        Integer pageSize = pageInit.getPageSize();
        Integer offset = pageInit.getOffset(); // 偏移量
        List<WorkModel> models = workMapper.selectByPage(statusEnum, offset, pageSize, dto.getParams());
        Long total = workMapper.countByPage(statusEnum, dto.getParams());
        int totalPage = (int) Math.ceil((double) total / pageSize);
        // 循环所有作品获取封面图并签名
        List<WorkModel> signatureWorks = this.loopSignatureWorkCover(models);
        return ResponseEntity.ok(
                new ResourceR().resourceSuch(
                        true,
                        new PageResultDto<>(
                                total,
                                signatureWorks,
                                pageNum,
                                pageSize,
                                totalPage == 0 ? 1 : totalPage
                        )
                )
        );
    }

    public ResponseEntity<J> getWorksByCategoryService(String categoryId, WorkStatusEnum statusEnum, PageRequestDto dto) {
        PageInitPojo pageInit = new PageInitPojo(dto); // 初始化接收的分页dto参数
        Integer pageNum = pageInit.getPageNum(); // 页码
        Integer pageSize = pageInit.getPageSize(); // 本页实体数
        Integer offset = pageInit.getOffset(); // 偏移量
        List<WorkModel> works = workMapper.selectByPageOnCategory(categoryId, statusEnum, offset, pageSize, dto.getParams());
        Long total = workMapper.countByPageOnCategory(categoryId, statusEnum, dto.getParams());
        int totalPage = (int) Math.ceil((double) total / pageSize);
        // 循环所有作品获取封面图并签名
        List<WorkModel> signatureWorks = this.loopSignatureWorkCover(works);
        return ResponseEntity.ok(
                new ResourceR().resourceSuch(
                        true,
                        new PageResultDto<>(
                                total,
                                signatureWorks,
                                pageNum,
                                pageSize,
                                totalPage == 0 ? 1 : totalPage
                        )
                )
        );
    }

    public ResponseEntity<J> getWorksBySchoolService(String schoolId, WorkStatusEnum statusEnum) {
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, workMapper.selectBySchoolId(schoolId, statusEnum)));
    }

    public ResponseEntity<J> getWorksByClubService(String clubId, WorkStatusEnum statusEnum) {
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, workMapper.selectByClubId(clubId, statusEnum)));
    }

    public ResponseEntity<J> getWorksByStudentService(String studentId, WorkStatusEnum statusEnum) {
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, workMapper.selectByStudentId(studentId, statusEnum)));
    }
    public ResponseEntity<J> getWorksByMeService(String uuid, WorkStatusEnum statusEnum) {
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, workMapper.selectByUuid(uuid, statusEnum)));
    }
    public ResponseEntity<J> getWorksByUuidService(String uuid, WorkStatusEnum statusEnum) {
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, workMapper.selectByUuid(uuid, statusEnum)));
    }
    public ResponseEntity<J> getWorksByUsernameService(String username, WorkStatusEnum statusEnum) {
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, workMapper.selectByUsername(username, statusEnum)));
    }
    public ResponseEntity<J> getWorksByIdService(String workId) {
        WorkModel work = workMapper.selectById(workId);
        if (Objects.isNull(work)) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        return switch (work.getStatus()) {
            case PRIVATE -> ResponseEntity.ok(new ErrorR().normal("私有作品无法访问"));
            case BAN -> ResponseEntity.ok(new ErrorR().normal("作品已被封禁"));
            case OFF -> ResponseEntity.ok(new ErrorR().normal("作品已被下架"));
            default -> {
                // 预签名work封面图URL
                WorkCoverImageModel coverImageModel = workCoverImageMapper.selectByWorkId(workId);
                String signedCoverImageUrl = cosService.generateSignedUrl(coverImageModel.getObject_key(), new Date(System.currentTimeMillis() + 60 * 1000), CosBucketConfigEnum.image);
                work.setCover_image(signedCoverImageUrl);
                yield ResponseEntity.ok(new ResourceR().resourceSuch(true, work));
            }
        };
    }
    public ResponseEntity<J> getWorkByIdWithMeService(String uuid, String workId) {
        WorkModel work = workMapper.selectById(workId);
        if (Objects.isNull(work)) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        }
        if (work.getAuthor().equals(uuid) || work.getUploader().equals(uuid)) {
            return ResponseEntity.ok(new ResourceR().resourceSuch(true, work));
        }
        return ResponseEntity.ok(new UserR().uuidMismatch());
    }
    @Transactional
    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check()})
    public ResponseEntity<J> uploadWorkService(String uuid, String categoryId, WorkModel requestModel, MultipartFile coverImage, MultipartFile[] files) {
        WorkModel tempWork;
        // 不存在ID，需要自动生成ID
        if (requestModel.getId() == null || requestModel.getId().isEmpty()) {
            do {
                requestModel.setId(new GenerateUUID().getUuid36l());
                tempWork = workMapper.selectById(requestModel.getId());
            } while (tempWork != null);
        }
        // 存在用户自定义的ID
        else {
            tempWork = workMapper.selectById(requestModel.getId());
            if (tempWork != null) return ResponseEntity.ok(new ErrorR().normal("您的自定义ID号已经冲突！"));
        }
        // 作品是号主自有
        if (requestModel.getIs_claim()) requestModel.setAuthor(uuid);
        // 作品是搬运收录
        else {
            if (requestModel.getAuthor_nickname() == null)
                return ResponseEntity.ok(new ErrorR().normal("搬运收录的作品，请标注该作品作者在其他平台常用昵称！"));
            requestModel.setUploader(uuid);
            requestModel.setAuthor("707B0FBF6AAA35B788069B07AEFEA12B");
        }
        requestModel.setBanner_image("");
        requestModel.setCover_image("");
        boolean insert = workMapper.insert(requestModel);
        if (insert) {
            /*若分类参数存在时的判断*/
            if (categoryId != null) {
                WorkCategoryModel workCategory = new WorkCategoryModel();
                workCategory.setId(new GenerateUUID().getUuid36l());
                workCategory.setCategory_id(categoryId);
                workCategory.setWork_id(requestModel.getId());
                try {
                    workCategoryMapper.insert(workCategory);
                } catch (Exception e) {
                    log.error("作品分类写入失败！The category of work => Insert Error! 作品ID：{}", requestModel.getId());
                }
            }
            /*接入腾讯云COS对象存储的操作方法*/
            // 设置桶内Key路径
            final String KEY =  "/" + uuid + "/work_images/";
            // 向腾讯云COS存储对象
            J reJ = cosService.uploadFile(coverImage, KEY, CosBucketConfigEnum.image);
            // 若是向腾讯云COS存储失败时
            if (reJ == null || reJ.getCode() != 0) {
                // 删除掉已经上传的作品信息
                workMapper.delete(requestModel.getId());
                return ResponseEntity.ok(new ErrorR().normal("上传封面图时出现异常：From Server!"));
            }
            String coverKey = reJ.getData().toString();
            WorkCoverImageModel coverImageModel = new WorkCoverImageModel();
            coverImageModel.setId(new GenerateUUID().getUuid36l());
            coverImageModel.setWork_id(requestModel.getId());
            coverImageModel.setObject_key(coverKey);
            try {
                workCoverImageMapper.insert(coverImageModel);
            } catch (Exception e) {
                // 删除掉已经上传的作品信息
                workMapper.delete(requestModel.getId());
                // 删除腾讯云COS中记录的对象信息
                cosService.deleteFile(coverKey, CosBucketConfigEnum.image);
                return ResponseEntity.ok(new ErrorR().normal("记录封面图指针时出现异常：From Server!"));
            }
            // 循环上传作品内容图
            int count = 0;
            for (MultipartFile file : files) {
                J ReImgJ = cosService.uploadFile(file, KEY, CosBucketConfigEnum.image);
                if (ReImgJ == null || ReImgJ.getCode() != 0) {
                    return ResponseEntity.ok(new ErrorR().normal("上传作品图时出现异常：From Server!"));
                }
                String imageKey = ReImgJ.getData().toString();
                WorkImageModel imageModel = new WorkImageModel();
                imageModel.setId(new GenerateUUID().getUuid36l());
                imageModel.setWork_id(requestModel.getId());
                imageModel.setSort(count);
                imageModel.setObject_key(imageKey);
                try {
                    workImageMapper.insert(imageModel);
                } catch (Exception e) {
                    return ResponseEntity.ok(new ErrorR().normal("记录作品图指针时出现异常：From Server!"));
                }
                count++;
            }
            return ResponseEntity.ok(new ChangeR().udu(true, 1));
        }
        return ResponseEntity.ok(new ChangeR().udu(false, 1));
    }
    @RequireSelfOrPermissionAnno(identity = UserIdentityEnum.MANAGER, targetPermission = PermissionConst.SEC_MAINTAINER, isHasElseUpper = true)
    public ResponseEntity<J> updateWorkService(String uuid, WorkModel requestModel) {
        boolean b = workMapper.update(requestModel);
        if (b) {
            return ResponseEntity.ok(new ChangeR().udu(true, 3));
        }
        return ResponseEntity.ok(new ChangeR().udu(false, 3));
    }

    public ResponseEntity<J> deleteWorkService(String workId) {
        try {
            // 取出所有作品图指针逐一删除
            List<WorkImageModel> workImages = workImageMapper.selectByWorkId(workId);
            for (WorkImageModel workImage : workImages) {
                cosService.deleteFile(workImage.getObject_key(), CosBucketConfigEnum.image);
                workImageMapper.delete(workImage.getId());
            }
            // 取出封面图指针执行删除
            WorkCoverImageModel coverImage = workCoverImageMapper.selectByWorkId(workId);
            cosService.deleteFile(coverImage.getObject_key(), CosBucketConfigEnum.image);
            workCoverImageMapper.deleteByWorkId(workId);
            // 删除作品
            workMapper.delete(workId);
        } catch (Exception e) {
            return ResponseEntity.ok(new ChangeR().udu(false, 2));
        }
        return ResponseEntity.ok(new ChangeR().udu(true, 2));
    }

    private List<WorkModel> loopSignatureWorkCover(List<WorkModel> works) {
        // 循环所有作品获取封面图并签名
        for (WorkModel model : works) {
            WorkCoverImageModel coverImageModel = workCoverImageMapper.selectByWorkId(model.getId());
            if (coverImageModel != null) {
                String coverImageUrl = cosService.generateSignedUrl(coverImageModel.getObject_key(), new Date(System.currentTimeMillis() + 60 * 1000), CosBucketConfigEnum.image);
                model.setCover_image(coverImageUrl);
            }
        }
        return works;
    }
}
