package com.miaoyu.barc.integration;

import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** 只在专属容器中创建随机新库；不读取应用配置，不连接或清理已有业务库。 */
final class IsolatedMysql {
    private final String name = "barc_audit_" + UUID.randomUUID().toString().replace("-", "");
    private final String baseUrl;
    private final String password;
    private final DataSource source;

    IsolatedMysql() {
        baseUrl = System.getenv("BARC_TEST_MYSQL_URL");
        password = System.getenv("BARC_TEST_MYSQL_PASSWORD");
        String container = System.getenv("BARC_TEST_MYSQL_CONTAINER");
        if (baseUrl == null || !baseUrl.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]{2,5}/")
                || container == null || !container.matches("barc-api-audit-[a-f0-9]{12}") || password == null) {
            throw new IllegalStateException("必须通过 run-isolated-mysql-tests.ps1 创建独立测试容器");
        }
        new JdbcTemplate(dataSource(baseUrl)).execute("CREATE DATABASE `" + name + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        source = dataSource(url());
    }

    private DataSource dataSource(String url) {
        return new DriverManagerDataSource(url + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", "root", password);
    }
    String url() { return baseUrl + name; }
    String password() { return password; }
    DataSource source() { return source; }
    JdbcTemplate jdbc() { return new JdbcTemplate(source); }
    Flyway flyway(String target) {
        var configuration = Flyway.configure().dataSource(source).locations("classpath:db/migration")
                .baselineOnMigrate(false).cleanDisabled(true);
        if (target != null) configuration.target(target);
        return configuration.load();
    }
}
