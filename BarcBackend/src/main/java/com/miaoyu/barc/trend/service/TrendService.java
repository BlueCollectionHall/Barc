package com.miaoyu.barc.trend.service;

import com.miaoyu.barc.annotation.RequireUserAndPermissionAnno;
import com.miaoyu.barc.permission.PermissionPolicy;
import com.miaoyu.barc.response.UserR;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.user.model.UserArchiveModel;
import com.miaoyu.barc.permission.PermissionConst;
import com.miaoyu.barc.response.ChangeR;
import com.miaoyu.barc.response.ErrorR;
import com.miaoyu.barc.response.ResourceR;
import com.miaoyu.barc.trend.mapper.TrendChangeMapper;
import com.miaoyu.barc.trend.mapper.TrendImageMapper;
import com.miaoyu.barc.trend.mapper.TrendSelectMapper;
import com.miaoyu.barc.trend.model.TrendImageModel;
import com.miaoyu.barc.trend.model.TrendModel;
import com.miaoyu.barc.trend.pojo.TrendPojo;
import com.miaoyu.barc.user.enumeration.UserIdentityEnum;
import com.miaoyu.barc.utils.GenerateUUID;
import com.miaoyu.barc.utils.J;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
public class TrendService {
    @Autowired private TrendImageService imageService;
    @Autowired private com.miaoyu.barc.utils.tencent.cos.ImageUrlResolver imageUrls;
    private static final Logger log = LoggerFactory.getLogger(TrendService.class);
    @Autowired
    private TrendSelectMapper trendSelectMapper;
    @Autowired
    private TrendChangeMapper trendChangeMapper;
    @Autowired
    private TrendImageMapper trendImageMapper;
    @Autowired
    private UserArchiveMapper userArchiveMapper;

    public ResponseEntity<J> getAllTrendService() {
        return getList(null, null, 1, 100);
    }

    public ResponseEntity<J> getNewTrendService(int count) {
        return getList(null, null, 1, count);
    }

    public ResponseEntity<J> getTrendsByUuidService(String uuid) {
        return getList(uuid, null, 1, 100);
    }

    /** 历史数组接口统一有界查询，支持补充的 page/size 参数继续读取。 */
    public ResponseEntity<J> getList(String author, String keyword, Integer page, Integer size) {
        var init = new com.miaoyu.barc.utils.pojo.PageInitPojo(page, size);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, trendSelectMapper.selectPage(
                normalize(author, "作者", 64), normalize(keyword, "搜索词", 100), init.getOffset(), init.getPageSize())));
    }

    /** 新列表返回总数与页信息，全部列表入口共用同一 Mapper。 */
    public ResponseEntity<J> getPage(String author, String keyword, Integer page, Integer size) {
        var init = new com.miaoyu.barc.utils.pojo.PageInitPojo(page, size);
        author = normalize(author, "作者", 64); keyword = normalize(keyword, "搜索词", 100);
        long total = trendSelectMapper.countPage(author, keyword);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, new com.miaoyu.barc.utils.dto.PageResultDto<>(total,
                trendSelectMapper.selectPage(author, keyword, init.getOffset(), init.getPageSize()),
                init.getPageNum(), init.getPageSize(), Math.max(1, (int) Math.ceil((double) total / init.getPageSize())))));
    }

    /** 最新入口返回单条详情；空库返回统一的资源不存在响应。 */
    public ResponseEntity<J> getLatest() {
        var list = trendSelectMapper.selectPage(null, null, 0, 1);
        return list.isEmpty() ? ResponseEntity.ok(new ResourceR().resourceSuch(false, null)) : getTrendOnlyService(list.get(0).getId());
    }

    private String normalize(String input, String label, int max) {
        return input == null || input.isBlank() ? null : com.miaoyu.barc.utils.RequestInput.required(input.trim(), label, max);
    }

    public ResponseEntity<J> getTrendOnlyService(String trendId) {
        TrendModel trend = trendSelectMapper.selectById(trendId);
        if (trend == null) return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        TrendPojo trendPojo = new TrendPojo();
        List<TrendImageModel> imageList = trendImageMapper.selectByTrendId(trendId);
        if (!imageList.isEmpty()) {
            imageList.sort(Comparator.comparingInt(TrendImageModel::getSort));
        }
        BeanUtils.copyProperties(trend, trendPojo);
        imageList.forEach(image -> { if (image.getObject_key() != null) image.setImage_url(imageUrls.resolve(image.getObject_key())); });
        trendPojo.setImages(imageList);
        return ResponseEntity.ok(new ResourceR().resourceSuch(true, trendPojo));
    }

    @Transactional(rollbackFor = RuntimeException.class)
    public ResponseEntity<J> uploadTrendService(
            String userUuid, TrendModel trendModel, MultipartFile[] files
    ) {

        if (trendModel == null || trendModel.getTitle() == null || trendModel.getTitle().isBlank() || trendModel.getTitle().length()>255
                || trendModel.getContent() == null || trendModel.getContent().isBlank() || trendModel.getContent().length()>10000) {
            throw new com.miaoyu.barc.exception.ApiException(new ErrorR().normal("动态标题和正文必填或超过长度限制"));
        }
        if (files == null) files = new MultipartFile[0];
        if (files.length > 20) throw new com.miaoyu.barc.exception.ApiException(new ErrorR().normal("每条动态最多 20 张图片"));
        for (MultipartFile file : files) com.miaoyu.barc.api.file.service.ImageInput.extension(file, 10L * 1024 * 1024);
        trendModel.setAuthor(userUuid); trendModel.setId(java.util.UUID.randomUUID().toString());
        if (!trendChangeMapper.insert(trendModel)) throw new com.miaoyu.barc.exception.ApiException(new ErrorR().normal("动态创建失败"));
        for (MultipartFile file : files) imageService.uploadTrendImageService(userUuid, trendModel.getId(), file);
        return ResponseEntity.ok(new ChangeR().udu(true, 1));
    }

    @Transactional
    @RequireUserAndPermissionAnno({@RequireUserAndPermissionAnno.Check()})
    public ResponseEntity<J> deleteTrendService(String uuid, String trendId) {
        // 先读取真实归属再做任何数据库或对象存储操作，不能拿操作者自己充当作者。
        TrendModel trend = trendSelectMapper.selectByIdForUpdate(trendId);
        if (trend == null) return ResponseEntity.ok(new ResourceR().resourceSuch(false, null));
        UserArchiveModel operator = userArchiveMapper.selectByUuid(uuid);
        if (operator == null || (!uuid.equals(trend.getAuthor())
                && !PermissionPolicy.matches(operator, UserIdentityEnum.MANAGER, PermissionConst.ADMINISTRATOR, false))) {
            return ResponseEntity.ok(new UserR().insufficientAccountPermission());
        }
        imageService.retireAll(trendId);
        boolean delete = trendChangeMapper.delete(trendId);
        if (delete) {
            return ResponseEntity.ok().body(new ChangeR().udu(true, 2));
        }
        throw new com.miaoyu.barc.exception.ApiException(new ErrorR().normal("动态删除失败"));
    }
}
