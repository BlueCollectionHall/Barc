package com.miaoyu.barc.utils.tencent.cos;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 腾讯云 COS 配置绑定与环境区分单元测试
 *
 * <p>本测试<b>不读取</b>工程里的 application.yaml / application-prod.yaml
 * （两者均被 .gitignore 忽略，在 CI 或他人机器上可能不存在），
 * 而是用与生产配置文件<b>完全同构</b>的属性集驱动绑定，因此可在任何环境稳定运行。</p>
 *
 * <p>覆盖重点：</p>
 * <ol>
 *   <li>开发环境（基础配置）绑定到开发桶，且 custom_domain 为空字符串时会覆盖 YAML 中的非空值
 *       —— 这一步决定「开发环境走腾讯云官方端点」；</li>
 *   <li>生产环境覆写后绑定到生产桶与自定义域名；</li>
 *   <li>已清理的四个零消费历史键（bucket-region / base-url / image-prefix / temp-prefix）
 *       不再绑定到配置类，防止后续被误加回。</li>
 * </ol>
 */
class CosConfigProfileTest {

    /** 仅加载配置属性绑定能力，避免拉起数据源、Flyway 等无关基础设施。 */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class);

    @Test
    @DisplayName("开发环境：绑定开发桶，且 custom_domain 为空串（走腾讯云官方端点）")
    void shouldBindDevelopmentBuckets() {
        contextRunner
                .withPropertyValues(
                        "tencent.cos.image.bucket-name=dev-miaoyu-barc-image-1309572720",
                        "tencent.cos.image.custom_domain=",
                        "tencent.cos.avatar.bucket-name=dev-miaoyu-barc-avatar-1309572720",
                        "tencent.cos.avatar.custom_domain=",
                        "tencent.cos.region=ap-shanghai",
                        "tencent.cos.secret_id=test-secret-id",
                        "tencent.cos.secret-key=test-secret-key"
                )
                .run(context -> {
                    CosConfig cosConfig = context.getBean(CosConfig.class);

                    assertThat(cosConfig.getImage().getBucketName()).isEqualTo("dev-miaoyu-barc-image-1309572720");
                    assertThat(cosConfig.getAvatar().getBucketName()).isEqualTo("dev-miaoyu-barc-avatar-1309572720");
                    // 空串必须原样绑定（而不是回落成 null 或被忽略），否则开发环境会误用生产自定义域名
                    assertThat(cosConfig.getImage().getCustomDomain()).isEmpty();
                    assertThat(cosConfig.getAvatar().getCustomDomain()).isEmpty();
                    assertThat(cosConfig.getRegion()).isEqualTo("ap-shanghai");
                    assertThat(cosConfig.getSecretId()).isEqualTo("test-secret-id");
                    assertThat(cosConfig.getSecretKey()).isEqualTo("test-secret-key");
                });
    }

    @Test
    @DisplayName("生产环境：覆写后的桶名与自定义域名生效（模拟 application-prod.yaml）")
    void shouldBindProductionBucketsAfterOverride() {
        contextRunner
                .withPropertyValues(
                        // 基础配置（开发值）
                        "tencent.cos.image.bucket-name=dev-miaoyu-barc-image-1309572720",
                        "tencent.cos.image.custom_domain=",
                        "tencent.cos.avatar.bucket-name=dev-miaoyu-barc-avatar-1309572720",
                        "tencent.cos.avatar.custom_domain=",
                        // application-prod.yaml 的覆写（只写两个键，区域等继承基础配置）
                        "tencent.cos.image.bucket-name=miaoyu-barc-image-1309572720",
                        "tencent.cos.image.custom_domain=https://image-cos-9c98ec3e.barc.work",
                        "tencent.cos.avatar.bucket-name=miaoyu-barc-avatar-1309572720",
                        "tencent.cos.avatar.custom_domain=https://avatar-cos-3ef160ef.barc.work",
                        "tencent.cos.region=ap-shanghai"
                )
                .run(context -> {
                    CosConfig cosConfig = context.getBean(CosConfig.class);

                    assertThat(cosConfig.getImage().getBucketName()).isEqualTo("miaoyu-barc-image-1309572720");
                    assertThat(cosConfig.getImage().getCustomDomain()).isEqualTo("https://image-cos-9c98ec3e.barc.work");
                    assertThat(cosConfig.getAvatar().getBucketName()).isEqualTo("miaoyu-barc-avatar-1309572720");
                    assertThat(cosConfig.getAvatar().getCustomDomain()).isEqualTo("https://avatar-cos-3ef160ef.barc.work");
                    // 区域未被覆写，继续继承基础配置
                    assertThat(cosConfig.getRegion()).isEqualTo("ap-shanghai");
                });
    }

    @Test
    @DisplayName("已清理的历史键不再绑定（防止 bucket-region / base-url / 前缀项被误加回）")
    void shouldNotBindRemovedLegacyKeys() {
        contextRunner
                .withPropertyValues(
                        // 故意写回历史键：它们必须不再出现在配置对象上
                        "tencent.cos.bucket-region=ap-shanghai",
                        "tencent.cos.base-url=https://cos.barc.work",
                        "tencent.cos.image-prefix=images/",
                        "tencent.cos.temp-prefix=temp/",
                        // 三个桶节点都带上历史键，逐一验证
                        "tencent.cos.image.bucket-name=dev-miaoyu-barc-image-1309572720",
                        "tencent.cos.image.bucket-region=ap-shanghai",
                        "tencent.cos.image.base-url=https://dev-miaoyu-barc-image-1309572720.cos.ap-shanghai.myqcloud.com",
                        "tencent.cos.avatar.bucket-name=dev-miaoyu-barc-avatar-1309572720",
                        "tencent.cos.avatar.bucket-region=ap-shanghai",
                        "tencent.cos.avatar.base-url=https://dev-miaoyu-barc-avatar-1309572720.cos.ap-shanghai.myqcloud.com",
                        "tencent.cos.test.bucket-name=test-default-1309572720",
                        "tencent.cos.test.bucket-region=ap-shanghai",
                        "tencent.cos.test.base-url=https://test-default-1309572720.cos.ap-shanghai.myqcloud.com"
                )
                .run(context -> {
                    CosConfig cosConfig = context.getBean(CosConfig.class);
                    // 唯一应被绑定的仍然是桶名（能取到即证明绑定链路正常）
                    assertThat(cosConfig.getImage()).isNotNull();
                    assertThat(cosConfig.getImage().getBucketName()).isEqualTo("dev-miaoyu-barc-image-1309572720");
                    assertThat(cosConfig.getAvatar().getBucketName()).isEqualTo("dev-miaoyu-barc-avatar-1309572720");
                    assertThat(cosConfig.getTest().getBucketName()).isEqualTo("test-default-1309572720");

                    // 用序列化字段集合做「结构性断言」：历史键一旦被加回字段，本用例立即失败
                    @SuppressWarnings("unchecked")
                    Map<String, Object> fields = new ObjectMapper().convertValue(cosConfig, Map.class);

                    assertThat(fields).doesNotContainKeys("bucketRegion", "baseUrl", "imagePrefix", "tempPrefix");
                    for (String node : new String[]{"image", "avatar", "test"}) {
                        assertThat((Map<String, Object>) fields.get(node))
                                .doesNotContainKeys("bucketRegion", "baseUrl");
                    }
                });
    }

    /** 只注册待测配置类，避免加载整个应用上下文。 */
    @EnableConfigurationProperties(CosConfig.class)
    static class TestConfig {
    }
}
