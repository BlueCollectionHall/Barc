package com.miaoyu.barc.trend.service;

import com.miaoyu.barc.api.file.service.ObjectStorageService;
import com.miaoyu.barc.api.service.CosGcService;
import com.miaoyu.barc.exception.ApiException;
import com.miaoyu.barc.trend.mapper.*;
import com.miaoyu.barc.trend.model.*;
import com.miaoyu.barc.user.mapper.UserArchiveMapper;
import com.miaoyu.barc.utils.tencent.cos.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 新 COS 图片和旧 MinIO 图片分开退役，首张图片与失败分支均不依赖外部服务。 */
@ExtendWith(MockitoExtension.class)
class TrendStorageFlowTest {
    @Mock TrendSelectMapper trends;
    @Mock TrendImageMapper images;
    @Mock TrendChangeMapper changes;
    @Mock UserArchiveMapper users;
    @Mock ObjectStorageService storage;
    @Mock CosGcService garbage;
    @InjectMocks TrendImageService service;
    @InjectMocks TrendService trendService;

    @BeforeEach void config() {
        CosConfig config = new CosConfig(); var bucket = new CosConfig.CosBucketPojo(); bucket.setBucketName("test-image"); config.setImage(bucket);
        ReflectionTestUtils.setField(service, "config", config);
    }

    @Test void firstImageStartsAtZeroAndStoredValueIsKey() {
        owned(); when(storage.upload(anyString(), any(), anyString(), anyLong())).thenReturn("uploads/owner/trend_images/random.png"); when(images.insert(any())).thenReturn(true);
        assertEquals(0, service.uploadTrendImageService("owner", "trend", new MockMultipartFile("file", new byte[]{1})).getBody().getCode());
        verify(images).insert(argThat(image -> image.getSort() == 0 && image.getImage_url() == null && image.getObject_key().endsWith("random.png")));
        verify(storage).bind("owner", "uploads/owner/trend_images/random.png");
    }

    @Test void rejectedOwnerCannotUploadOrDeleteAndInsertFailurePropagates() {
        owned(); var image = new TrendImageModel(); image.setId("image"); image.setTrend_id("trend"); when(images.selectById("image")).thenReturn(image);
        assertThrows(ApiException.class, () -> service.uploadTrendImageService("other", "trend", null));
        assertThrows(ApiException.class, () -> service.deleteTrendImageService("other", "image")); verifyNoInteractions(storage, garbage);
        when(storage.upload(anyString(), any(), anyString(), anyLong())).thenReturn("new.png");
        assertThrows(ApiException.class, () -> service.uploadTrendImageService("owner", "trend", new MockMultipartFile("file", new byte[]{1})));
    }

    @Test void deletionQueuesNewCosObjectsAndPreservesLegacyObjectForReview() {
        owned(); var modern = new TrendImageModel(); modern.setId("new"); modern.setTrend_id("trend"); modern.setSort(0); modern.setObject_key("new.png");
        when(images.selectById("new")).thenReturn(modern); when(images.delete("new")).thenReturn(true);
        service.deleteTrendImageService("owner", "new"); verify(garbage).enqueue("test-image", "new.png");
        var legacy = new TrendImageModel(); legacy.setId("old"); legacy.setTrend_id("trend"); legacy.setImage_name("legacy.png");
        when(images.selectByTrendId("trend")).thenReturn(List.of(legacy)); when(images.delete("old")).thenReturn(true);
        service.retireAll("trend"); verify(images).retireLegacy("uploads/legacy.png");
    }

    @Test void missingDetailReturnsBusinessNotFoundAndCreateFailureIsNotSwallowed() {
        assertEquals(1, trendService.getTrendOnlyService("missing").getBody().getCode());
        var trend = new TrendModel(); trend.setTitle("标题"); trend.setContent("内容");
        assertThrows(ApiException.class, () -> trendService.uploadTrendService("owner", trend, null));
        verify(changes).insert(argThat(model -> "owner".equals(model.getAuthor())));
    }

    private void owned() { var trend = new TrendModel(); trend.setId("trend"); trend.setAuthor("owner"); when(trends.selectByIdForUpdate("trend")).thenReturn(trend); }
}
