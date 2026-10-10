package com.miaoyu.barc;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.URL;
import java.util.Arrays;

@MapperScan("com.miaoyu.barc.**.mapper")
@EnableAsync
@EnableScheduling
@SpringBootApplication
public class BarcBackendApplication {

    static final String DEVELOPMENT_PROFILE = "dev";
    static final String PRODUCTION_PROFILE = "prod";
    // 生产库指纹：同时锁定主机、端口与库名。
    // 本项目的开发库（ba）与生产库（ba-prod）位于同一 MySQL 实例、同一端口，
    // 仅库名不同，因此只有把库名一并纳入校验，才能防止生产环境静默连到开发库。
    // 变更生产库地址或库名时，必须同步修改此常量与 application-prod.yaml。
    static final String PRODUCTION_DATASOURCE_PREFIX = "jdbc:mysql://127.0.0.1:51001/ba-prod";
    // 生产镜像桶指纹：与数据源指纹同样的思路——把「生产误连开发资源」从人工审查变成启动即拒绝。
    // 只锁定 image 桶的原因：全部上传链路与全部签名 URL 链路使用的都是 image 桶，
    // 一旦误连开发桶影响面最大；avatar 桶当前暂无消费方，不做强校验以免过度约束配置。
    // 变更生产 image 桶时必须同步修改此常量与 application-prod.yaml。
    static final String PRODUCTION_IMAGE_BUCKET_PREFIX = "miaoyu-barc-image-";
    private static final String ACTIVE_PROFILE_ARGUMENT = "--spring.profiles.active=";

    /**
     * 启动后端服务，并根据代码的加载方式选择默认运行环境。
     *
     * <p>IDE、直接运行 main 方法和 {@code mvn spring-boot:run} 都从普通 class 文件加载，
     * 因此默认使用开发环境；{@code java -jar} 从 jar 内加载，默认使用生产环境。
     * 显式传入 {@code spring.profiles.active} 时，Spring 会优先使用显式配置，便于排障和测试。</p>
     *
     * @param args Spring Boot 启动参数
     */
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(BarcBackendApplication.class);
        if (!hasExplicitActiveProfile(
                args,
                System.getProperty("spring.profiles.active"),
                System.getenv("SPRING_PROFILES_ACTIVE")
        )) {
            // 附加 Profile 会在 Config Data 加载前生效，确保对应环境配置文件被读取。
            application.setAdditionalProfiles(resolveDefaultProfile(classResourceUrl()));
        }
        // 在创建数据库连接前确认本地生产配置已随部署产物提供，禁止缺失时回退到开发库。
        application.addInitializers(context -> validateProductionConfiguration(
                context.getEnvironment(),
                productionConfigurationUrl()
        ));
        application.run(args);
    }

    /**
     * 校验最终生效的 Profile、数据库地址与对象存储桶，防止配置缺失或冲突时连错环境。
     *
     * @param environment 已完成 Config Data 加载的 Spring 环境
     * @param productionConfigurationUrl classpath 中的生产配置资源地址
     * @throws IllegalStateException 环境冲突、配置文件缺失、生产数据源指纹或生产 COS 桶指纹不匹配时抛出
     */
    static void validateProductionConfiguration(Environment environment, URL productionConfigurationUrl) {
        boolean developmentActive = Arrays.asList(environment.getActiveProfiles()).contains(DEVELOPMENT_PROFILE);
        boolean productionActive = Arrays.asList(environment.getActiveProfiles()).contains(PRODUCTION_PROFILE);
        if (developmentActive && productionActive) {
            throw new IllegalStateException("dev 与 prod Profile 不能同时激活");
        }
        if (!productionActive) {
            return;
        }
        if (productionConfigurationUrl == null) {
            throw new IllegalStateException("生产配置文件缺失：请确认 application-prod.yaml 已随部署产物提供");
        }

        String datasourceUrl = environment.getProperty("spring.datasource.url");
        if (datasourceUrl == null || !datasourceUrl.startsWith(PRODUCTION_DATASOURCE_PREFIX)) {
            // 直接复用常量，避免错误提示与实际校验规则出现两处不一致。
            throw new IllegalStateException(
                    "生产数据源配置错误：spring.datasource.url 必须以 " + PRODUCTION_DATASOURCE_PREFIX + " 开头"
            );
        }

        // 对象存储同样按指纹校验：base 配置放的是开发桶，生产完全依赖 application-prod.yaml 覆写，
        // 一旦漏配覆写段就会静默写入开发桶，因此这里必须拦在启动阶段。
        String imageBucketName = environment.getProperty("tencent.cos.image.bucket-name");
        if (imageBucketName == null || !imageBucketName.startsWith(PRODUCTION_IMAGE_BUCKET_PREFIX)) {
            throw new IllegalStateException(
                    "生产对象存储配置错误：tencent.cos.image.bucket-name 必须以 "
                            + PRODUCTION_IMAGE_BUCKET_PREFIX + " 开头（请在 application-prod.yaml 覆写生产桶）"
            );
        }
    }

    /**
     * 定位被 Git 忽略但必须随生产部署产物提供的生产配置文件。
     *
     * @return classpath 中的生产配置地址；未提供时返回 {@code null}
     */
    static URL productionConfigurationUrl() {
        return BarcBackendApplication.class.getClassLoader().getResource("application-prod.yaml");
    }

    /**
     * 判断调用方是否已经显式指定活动 Profile；显式配置始终优先于自动识别结果。
     *
     * @param args Spring Boot 启动参数
     * @param systemProperty JVM 系统属性 {@code spring.profiles.active}
     * @param environmentVariable 环境变量 {@code SPRING_PROFILES_ACTIVE}
     * @return 任一来源提供非空活动 Profile 时返回 {@code true}
     */
    static boolean hasExplicitActiveProfile(String[] args, String systemProperty, String environmentVariable) {
        if (hasText(systemProperty) || hasText(environmentVariable)) {
            return true;
        }
        if (args == null) {
            return false;
        }
        for (String argument : args) {
            if (argument != null
                    && argument.startsWith(ACTIVE_PROFILE_ARGUMENT)
                    && hasText(argument.substring(ACTIVE_PROFILE_ARGUMENT.length()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查字符串是否包含非空白内容。
     *
     * @param value 待检查字符串
     * @return 包含非空白内容时返回 {@code true}
     */
    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 获取启动类自身的资源地址，用于区分普通 class 目录与打包后的 jar。
     *
     * @return 启动类资源地址；极端情况下无法定位时返回 {@code null}
     */
    static URL classResourceUrl() {
        return BarcBackendApplication.class.getResource("BarcBackendApplication.class");
    }

    /**
     * 根据启动类资源协议计算默认环境。
     *
     * @param classResourceUrl 启动类资源地址
     * @return jar 内运行时返回 {@code prod}，其余情况返回 {@code dev}
     */
    static String resolveDefaultProfile(URL classResourceUrl) {
        // Spring Boot 可执行 jar 中的类使用 jar 协议；开发工具和 Maven 插件使用 file 协议。
        if (classResourceUrl != null && "jar".equalsIgnoreCase(classResourceUrl.getProtocol())) {
            return PRODUCTION_PROFILE;
        }
        // 无法识别来源时安全回退到开发库，避免本地开发意外操作生产数据。
        return DEVELOPMENT_PROFILE;
    }

}
