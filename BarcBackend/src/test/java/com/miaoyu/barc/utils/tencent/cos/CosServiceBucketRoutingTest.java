package com.miaoyu.barc.utils.tencent.cos;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.PutObjectRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * COS 服务层「按桶类型取配置」单元测试
 *
 * <p>本测试锁定的核心链路：环境切换最终只体现为 {@code bucket-name} 与 {@code custom_domain}
 * 两个键的不同取值，而 {@link CosService} 与 {@link CosClient} 必须<b>始终读取同一份配置</b>。
 * 因此用开发桶配置构造服务层，断言上传与签名确实落在开发桶上。</p>
 *
 * <p>不依赖真实 COS：{@link CosClient} 与 {@link COSClient} 均为 Mock。</p>
 */
@ExtendWith(MockitoExtension.class)
class CosServiceBucketRoutingTest {

    @Mock
    private CosClient cosClient;
    @Mock
    private COSClient sdkClient;

    private CosConfig devCosConfig;
    private CosService cosService;

    /**
     * 构造一份「开发环境」形态的配置（开发桶 + 空自定义域名）。
     *
     * @return 开发环境配置对象
     */
    private static CosConfig developmentConfig() {
        CosConfig config = new CosConfig();
        config.setSecretId("test-secret-id");
        config.setSecretKey("test-secret-key");
        config.setRegion("ap-shanghai");
        config.setBucketName("test-default-1309572720");
        config.setCustomDomain("https://cos.barc.work");
        config.setTest(bucket("test-default-1309572720", "https://cos.barc.work"));
        config.setImage(bucket("dev-miaoyu-barc-image-1309572720", ""));
        config.setAvatar(bucket("dev-miaoyu-barc-avatar-1309572720", ""));
        return config;
    }

    /**
     * 构造桶配置节点。
     *
     * @param bucketName 桶名
     * @param customDomain 自定义域名，空串表示不配置
     * @return 桶配置对象
     */
    private static CosConfig.CosBucketPojo bucket(String bucketName, String customDomain) {
        CosConfig.CosBucketPojo pojo = new CosConfig.CosBucketPojo();
        pojo.setBucketName(bucketName);
        pojo.setCustomDomain(customDomain);
        return pojo;
    }

    /**
     * 初始化服务层：image / avatar 用 Mock 返回同一个 SDK 客户端，便于统一验证调用参数。
     */
    private void setUpService() {
        devCosConfig = developmentConfig();
        cosService = new CosService();
        ReflectionTestUtils.setField(cosService, "cosClient", cosClient);
        ReflectionTestUtils.setField(cosService, "cosConfig", devCosConfig);
    }

    @Test
    @DisplayName("上传：image 桶解析为开发桶并在该桶上 putObject")
    void uploadShouldTargetDevelopmentImageBucket() {
        setUpService();
        when(cosClient.cosClient(CosBucketConfigEnum.image)).thenReturn(sdkClient);
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1, 2, 3});

        var result = cosService.uploadFile(file, "/user-1/", CosBucketConfigEnum.image);

        assertThat(result.getCode()).isZero();
        // 关键断言：请求打在开发桶上（生产桶名绝不能出现在这里）
        verify(sdkClient).putObject(ArgumentMatchers.<PutObjectRequest>argThat(request ->
                "dev-miaoyu-barc-image-1309572720".equals(request.getBucketName())));
    }

    @Test
    @DisplayName("上传（指定完整 key）：同样落在开发桶")
    void uploadWithKeyShouldTargetDevelopmentImageBucket() {
        setUpService();
        when(cosClient.cosClient(CosBucketConfigEnum.image)).thenReturn(sdkClient);
        MockMultipartFile file = new MockMultipartFile("file", "bg.png", "image/png", new byte[]{1});

        var result = cosService.uploadFileWithKey(file, "client/bg/home/A-1.png", CosBucketConfigEnum.image);

        assertThat(result.getCode()).isZero();
        assertThat(result.getData()).isEqualTo("client/bg/home/A-1.png");
        verify(sdkClient).putObject(ArgumentMatchers.<PutObjectRequest>argThat(request ->
                "dev-miaoyu-barc-image-1309572720".equals(request.getBucketName())
                        && "client/bg/home/A-1.png".equals(request.getKey())));
    }

    @Test
    @DisplayName("删除：使用当前环境配置的 image 桶名")
    void deleteShouldUseConfiguredImageBucket() {
        setUpService();
        when(cosClient.cosClient(CosBucketConfigEnum.image)).thenReturn(sdkClient);

        cosService.deleteFile("client/bg/home/A-1.png", CosBucketConfigEnum.image);

        verify(sdkClient).deleteObject(eq("dev-miaoyu-barc-image-1309572720"), eq("client/bg/home/A-1.png"));
    }

    @Test
    @DisplayName("签名：image 桶使用当前环境配置的桶名生成预签名 URL")
    void generateSignedUrlShouldUseConfiguredImageBucket() {
        setUpService();
        // generatePresignedUrl 需要真实的客户端配置，这里只验证「传了正确的桶名」
        when(cosClient.cosClient(CosBucketConfigEnum.image)).thenReturn(sdkClient);
        when(sdkClient.generatePresignedUrl(ArgumentMatchers.<GeneratePresignedUrlRequest>any())).thenReturn(null);

        cosService.generateSignedUrl("client/bg/home/A-1.png", new Date(), CosBucketConfigEnum.image);

        verify(sdkClient).generatePresignedUrl(ArgumentMatchers.<GeneratePresignedUrlRequest>argThat(request ->
                "dev-miaoyu-barc-image-1309572720".equals(request.getBucketName())
                        && "client/bg/home/A-1.png".equals(request.getKey())));
    }

    @Test
    @DisplayName("桶配置缺失：上传被捕获为失败结果，不向上抛出异常")
    void shouldReturnFailureWhenBucketConfigMissing() {
        setUpService();
        devCosConfig.setImage(null);
        MockMultipartFile file = new MockMultipartFile("file", "photo.png", "image/png", new byte[]{1});

        // image 节点缺失时解析桶名会抛异常，被 CosService 捕获后返回失败结果（不向上抛出）
        var result = cosService.uploadFile(file, "/user-1/", CosBucketConfigEnum.image);

        assertThat(result.getCode()).isEqualTo(1);
        // 解析桶名阶段就失败了，绝不会去取客户端、更不会发起上传
        verify(cosClient, never()).cosClient(any());
        verify(sdkClient, never()).putObject(ArgumentMatchers.<PutObjectRequest>any());
    }
}
