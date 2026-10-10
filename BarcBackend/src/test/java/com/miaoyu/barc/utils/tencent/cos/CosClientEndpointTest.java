package com.miaoyu.barc.utils.tencent.cos;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.endpoint.RegionEndpointBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * COS 客户端端点（域名）选择单元测试
 *
 * <p>本测试只构造客户端对象并检查其端点配置，<b>不发起任何网络请求</b>，
 * 因此不需要真实密钥，也不会触碰任何真实桶。</p>
 *
 * <p>注意：COS SDK 的 {@link COSClient} 不实现 {@code AutoCloseable}，
 * 因此这里统一用 {@code try/finally + shutdown()} 释放其内部线程池。</p>
 *
 * <p>覆盖重点：自定义域名（custom_domain）是「用哪个域名」的唯一开关——</p>
 * <ol>
 *   <li>未配置（开发环境）→ SDK 客户端不设置自定义端点，走腾讯云官方端点；</li>
 *   <li>已配置（生产环境）→ 客户端端点被替换为自定义域名主机；</li>
 *   <li>自定义域名非法（无法解析为 URL）→ 忽略该配置且不抛异常，保持可用；</li>
 *   <li>四个桶分支（test / avatar / image / 默认）都能正常构建客户端。</li>
 * </ol>
 */
class CosClientEndpointTest {

    private CosClient cosClient;
    private CosConfig cosConfig;

    /**
     * 构造桶配置节点。
     *
     * @param bucketName 桶名
     * @param customDomain 自定义域名，空串表示不配置（走腾讯云官方端点）
     * @return 桶配置对象
     */
    private static CosConfig.CosBucketPojo bucket(String bucketName, String customDomain) {
        CosConfig.CosBucketPojo pojo = new CosConfig.CosBucketPojo();
        pojo.setBucketName(bucketName);
        pojo.setCustomDomain(customDomain);
        return pojo;
    }

    /**
     * 构建客户端并断言，最后统一释放 SDK 客户端资源。
     *
     * @param bucketType 桶类型
     * @param assertion 针对客户端配置的断言
     */
    private void assertClient(CosBucketConfigEnum bucketType, java.util.function.Consumer<COSClient> assertion) {
        COSClient client = cosClient.cosClient(bucketType);
        try {
            assertThat(client).isNotNull();
            assertion.accept(client);
        } finally {
            client.shutdown();
        }
    }

    @BeforeEach
    void setUp() {
        cosConfig = new CosConfig();
        // 使用假凭据：SDK 构造客户端不校验凭据，也不联网
        cosConfig.setSecretId("test-secret-id");
        cosConfig.setSecretKey("test-secret-key");
        cosConfig.setRegion("ap-shanghai");

        cosClient = new CosClient();
        ReflectionTestUtils.setField(cosClient, "cosConfig", cosConfig);
    }

    @Test
    @DisplayName("开发环境：custom_domain 为空时不替换端点（走 SDK 默认的区域端点）")
    void shouldKeepOfficialEndpointWhenCustomDomainBlank() {
        cosConfig.setImage(bucket("dev-miaoyu-barc-image-1309572720", ""));

        assertClient(CosBucketConfigEnum.image, client -> {
            // SDK 自身总会带一个区域端点构建器；关键是它「不是」我们的自定义端点
            assertThat(client.getClientConfig().getEndpointBuilder())
                    .isNotInstanceOf(CustomEndpointBuilder.class)
                    .isInstanceOf(RegionEndpointBuilder.class);
            // 区域端点产出的主机仍由「桶名 + 区域」推导，即腾讯云官方域名
            assertThat(client.getClientConfig().getEndpointBuilder()
                    .buildGeneralApiEndpoint("dev-miaoyu-barc-image-1309572720"))
                    .contains("dev-miaoyu-barc-image-1309572720")
                    .contains("myqcloud.com");
        });
    }

    @Test
    @DisplayName("生产环境：配置 custom_domain 后端点被替换为自定义域名")
    void shouldUseCustomDomainEndpointWhenConfigured() {
        cosConfig.setImage(bucket("miaoyu-barc-image-1309572720", "https://image-cos-9c98ec3e.barc.work"));

        assertClient(CosBucketConfigEnum.image, client -> {
            assertThat(client.getClientConfig().getEndpointBuilder())
                    .isInstanceOf(CustomEndpointBuilder.class);
            // 端点主机应等于自定义域名的主机名（不含协议与路径）
            assertThat(client.getClientConfig().getEndpointBuilder()
                    .buildGeneralApiEndpoint("miaoyu-barc-image-1309572720"))
                    .isEqualTo("image-cos-9c98ec3e.barc.work");
        });
    }

    @Test
    @DisplayName("非法自定义域名：忽略端点替换且不抛异常")
    void shouldIgnoreMalformedCustomDomain() {
        cosConfig.setImage(bucket("miaoyu-barc-image-1309572720", "这不是一个合法域名"));

        assertDoesNotThrow(() -> assertClient(CosBucketConfigEnum.image, client -> {
            // MalformedURLException 被忽略后仍应构建出可用的官方端点客户端
            assertThat(client.getClientConfig().getEndpointBuilder())
                    .isNotInstanceOf(CustomEndpointBuilder.class);
            assertThat(client.getClientConfig().getEndpointBuilder()
                    .buildGeneralApiEndpoint("miaoyu-barc-image-1309572720"))
                    .contains("myqcloud.com");
        }));
    }

    @Test
    @DisplayName("四个桶分支均可构建客户端（avatar / test / image / 默认兜底）")
    void shouldBuildClientForEveryBucketBranch() {
        cosConfig.setImage(bucket("dev-miaoyu-barc-image-1309572720", ""));
        cosConfig.setAvatar(bucket("dev-miaoyu-barc-avatar-1309572720", ""));
        cosConfig.setTest(bucket("test-default-1309572720", "https://cos.barc.work"));
        cosConfig.setBucketName("test-default-1309572720");
        cosConfig.setCustomDomain("https://cos.barc.work");

        for (CosBucketConfigEnum bucketType : CosBucketConfigEnum.values()) {
            assertClient(bucketType, client -> assertThat(client).isNotNull());
        }
        // 传 null 时兜底为 test 桶（CosClient 内既有行为）
        assertClient(null, client -> assertThat(client).isNotNull());
    }
}
