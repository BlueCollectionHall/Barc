package com.miaoyu.barc;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.net.MalformedURLException;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证后端启动方式与默认 Spring Profile 的映射关系，以及生产环境的配置安全校验。
 */
class BarcBackendApplicationTest {

    /**
     * 普通 class 文件对应 IDE、main 方法和 Maven 插件启动，应使用开发环境。
     *
     * @throws MalformedURLException 测试 URL 构造失败时抛出
     */
    @Test
    void shouldUseDevelopmentProfileForClassDirectory() throws MalformedURLException {
        URL classFile = new URL("file:/workspace/target/classes/com/miaoyu/barc/BarcBackendApplication.class");

        String profile = BarcBackendApplication.resolveDefaultProfile(classFile);

        assertEquals(BarcBackendApplication.DEVELOPMENT_PROFILE, profile);
    }

    /**
     * Spring Boot 可执行 jar 中的 class 使用 jar 协议，应使用生产环境。
     *
     * @throws MalformedURLException 测试 URL 构造失败时抛出
     */
    @Test
    void shouldUseProductionProfileForExecutableJar() throws MalformedURLException {
        URL jarClass = new URL(
                "jar:file:/opt/barc/BarcBackend.jar!/BOOT-INF/classes!/com/miaoyu/barc/BarcBackendApplication.class"
        );

        String profile = BarcBackendApplication.resolveDefaultProfile(jarClass);

        assertEquals(BarcBackendApplication.PRODUCTION_PROFILE, profile);
    }

    /**
     * 资源地址无法识别时必须回退到开发环境，避免误连生产数据库。
     */
    @Test
    void shouldSafelyFallBackToDevelopmentProfile() {
        String profile = BarcBackendApplication.resolveDefaultProfile(null);

        assertEquals(BarcBackendApplication.DEVELOPMENT_PROFILE, profile);
    }

    /**
     * 当前测试运行在 Maven 的 classes 目录中，应能识别为开发环境。
     */
    @Test
    void shouldRecognizeCurrentMavenTestRuntimeAsDevelopment() {
        URL currentClassResource = BarcBackendApplication.classResourceUrl();

        assertNotNull(currentClassResource);
        assertEquals(
                BarcBackendApplication.DEVELOPMENT_PROFILE,
                BarcBackendApplication.resolveDefaultProfile(currentClassResource)
        );
    }

    /**
     * 生产 Profile 继承到开发数据库地址时必须拒绝启动。
     */
    @Test
    void shouldRejectProductionProfileWithDevelopmentDatasource() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://example.invalid:51001/ba-prod");
        environment.setActiveProfiles(BarcBackendApplication.PRODUCTION_PROFILE);

        assertThrows(
                IllegalStateException.class,
                () -> BarcBackendApplication.validateProductionConfiguration(environment, productionConfigurationUrl())
        );
    }

    /**
     * 开发库与生产库位于同一实例同一端口，仅库名不同；
     * 因此生产配置若指向开发库，必须依靠库名校验拦下，不能只看主机与端口。
     */
    @Test
    void shouldRejectProductionProfilePointingToDevelopmentDatabase() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://127.0.0.1:51001/ba");
        environment.setActiveProfiles(BarcBackendApplication.PRODUCTION_PROFILE);

        assertThrows(
                IllegalStateException.class,
                () -> BarcBackendApplication.validateProductionConfiguration(environment, productionConfigurationUrl())
        );
    }

    /**
     * 即使外部属性提供了本机数据库地址，缺少生产配置文件时也必须拒绝启动。
     */
    @Test
    void shouldRejectProductionProfileWithoutConfigurationFile() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://127.0.0.1:51001/ba-prod");
        environment.setActiveProfiles(BarcBackendApplication.PRODUCTION_PROFILE);

        assertThrows(
                IllegalStateException.class,
                () -> BarcBackendApplication.validateProductionConfiguration(environment, null)
        );
    }

    /**
     * 生产 Profile 使用服务器本机 MySQL 时应允许继续启动。
     */
    @Test
    void shouldAcceptLocalProductionDatasource() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://127.0.0.1:51001/ba-prod");
        environment.setActiveProfiles(BarcBackendApplication.PRODUCTION_PROFILE);

        assertDoesNotThrow(() -> BarcBackendApplication.validateProductionConfiguration(
                environment,
                productionConfigurationUrl()
        ));
    }

    /**
     * 开发与生产 Profile 同时激活时必须拒绝启动，避免配置覆盖顺序产生歧义。
     */
    @Test
    void shouldRejectConflictingProfiles() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:mysql://127.0.0.1:51001/ba-prod");
        environment.setActiveProfiles(
                BarcBackendApplication.DEVELOPMENT_PROFILE,
                BarcBackendApplication.PRODUCTION_PROFILE
        );

        assertThrows(
                IllegalStateException.class,
                () -> BarcBackendApplication.validateProductionConfiguration(environment, productionConfigurationUrl())
        );
    }

    /**
     * 开发 Profile 不依赖生产配置文件。
     */
    @Test
    void shouldNotRequireProductionConfigurationForDevelopment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(BarcBackendApplication.DEVELOPMENT_PROFILE);

        assertDoesNotThrow(() -> BarcBackendApplication.validateProductionConfiguration(
                environment,
                productionConfigurationUrl()
        ));
    }

    /**
     * 未提供任何显式配置时，应允许程序根据启动方式自动选择环境。
     */
    @Test
    void shouldAutomaticallySelectProfileWithoutExplicitConfiguration() {
        assertFalse(BarcBackendApplication.hasExplicitActiveProfile(new String[0], null, null));
    }

    /**
     * 命令行指定活动 Profile 时，应跳过自动环境选择。
     */
    @Test
    void shouldRecognizeCommandLineActiveProfile() {
        assertTrue(BarcBackendApplication.hasExplicitActiveProfile(
                new String[]{"--spring.profiles.active=dev"},
                null,
                null
        ));
    }

    /**
     * JVM 系统属性指定活动 Profile 时，应跳过自动环境选择。
     */
    @Test
    void shouldRecognizeSystemPropertyActiveProfile() {
        assertTrue(BarcBackendApplication.hasExplicitActiveProfile(new String[0], "prod", null));
    }

    /**
     * 环境变量指定活动 Profile 时，应跳过自动环境选择。
     */
    @Test
    void shouldRecognizeEnvironmentVariableActiveProfile() {
        assertTrue(BarcBackendApplication.hasExplicitActiveProfile(new String[0], null, "dev"));
    }

    /**
     * 空白显式配置不应阻止自动环境选择。
     */
    @Test
    void shouldIgnoreBlankExplicitProfile() {
        assertFalse(BarcBackendApplication.hasExplicitActiveProfile(
                new String[]{"--spring.profiles.active=   "},
                " ",
                ""
        ));
    }

    /**
     * 构造仅用于单元测试的生产配置资源地址，不读取任何真实凭据。
     *
     * @return 测试使用的非空资源地址
     */
    private URL productionConfigurationUrl() {
        return BarcBackendApplication.classResourceUrl();
    }
}
