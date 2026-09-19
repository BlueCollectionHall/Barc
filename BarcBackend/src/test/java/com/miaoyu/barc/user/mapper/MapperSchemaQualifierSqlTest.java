package com.miaoyu.barc.user.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 数据库对象名必须「库名无关」的守护测试。
 *
 * 背景：开发库与生产库位于同一 MySQL 实例、同一端口，仅库名不同（`ba` / `ba-prod`）。
 * 一旦 SQL 里出现「库名.表名」形式，生产环境会把该表指到开发库，属于跨库读取；
 * 若实例上不存在该库名，则会直接抛「Unknown database」。
 *
 * 因此本测试对全仓 MyBatis SQL（Java 注解 + XML mapper）做静态扫描，
 * 不允许出现任何带库名前缀的表引用（系统 schema 除外）。
 */
class MapperSchemaQualifierSqlTest {

    /** 允许出现的系统级 schema 前缀（MySQL 元数据与内置库）。 */
    private static final Set<String> ALLOWED_SCHEMAS = Set.of(
            "information_schema", "mysql", "performance_schema", "sys");

    /** 匹配「FROM/JOIN/INTO/UPDATE/TABLE + 库名.」这种带库名前缀的引用。 */
    private static final Pattern SCHEMA_QUALIFIED =
            Pattern.compile("(?i)\\b(?:from|join|into|update|table)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\.");

    /** 匹配反引号包裹的库名限定符（如 `ba-prod`.user_basic），带连字符的库名只能用这种写法。 */
    private static final Pattern BACKTICK_QUALIFIED =
            Pattern.compile("`([^`]+)`\\s*\\.");

    @Test
    @DisplayName("无筛选条件的用户分页不得使用库名前缀，应直接 JOIN user_basic")
    void selectByPage_ShouldJoinUserBasicWithoutSchemaPrefix() throws NoSuchMethodException {
        String sql = normalize(String.join(" ",
                UserArchiveMapper.class.getMethod("selectByPage", int.class, int.class)
                        .getAnnotation(Select.class).value()));

        assertTrue(sql.contains("LEFT JOIN user_basic b ON a.uuid = b.uuid"),
                "selectByPage 必须直接 JOIN user_basic（不带库名前缀）");
        assertFalse(sql.contains("ba."),
                "selectByPage 不得出现 ba. 前缀：生产库名为 ba-prod，会跨库读取开发库的 user_basic");
    }

    @Test
    @DisplayName("全仓 MyBatis Java 注解 SQL 均不得出现库名前缀")
    void allMapperAnnotationSql_ShouldNotQualifyTableWithSchema() throws Exception {
        List<String> violations = new ArrayList<>();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        List<Path> javaFiles = javaFilesUnder(projectMainDir().resolve("java"));
        int scannedStatements = 0;

        // 扫描规模断言：防止目录定位失败导致「零文件空扫」而假阳性通过
        assertFalse(javaFiles.isEmpty(), "未扫描到任何 Java 源文件，守护测试失效");

        for (Path javaFile : javaFiles) {
            String className = toClassName(projectMainDir().resolve("java"), javaFile);
            // initialize=false：只读注解，不触发静态初始化
            Class<?> type = Class.forName(className, false, loader);
            for (Method method : type.getDeclaredMethods()) {
                for (Annotation annotation : method.getAnnotations()) {
                    String sql = annotationSql(annotation);
                    if (sql == null) {
                        continue;
                    }
                    scannedStatements++;
                    String hit = firstDisallowedSchemaReference(sql);
                    if (hit != null) {
                        violations.add(className + "#" + method.getName() + " -> " + hit);
                    }
                }
            }
        }

        assertTrue(scannedStatements > 0, "未扫描到任何 MyBatis 注解 SQL，守护测试失效");
        assertTrue(violations.isEmpty(),
                "以下 MyBatis 注解 SQL 使用了带库名前缀的表引用，请改为不带库名的写法：" + violations);
    }

    @Test
    @DisplayName("全仓 XML mapper SQL 均不得出现库名前缀")
    void allMapperXml_ShouldNotQualifyTableWithSchema() throws IOException {
        List<String> violations = new ArrayList<>();
        Path xmlDir = projectMainDir().resolve("resources").resolve("mappers");
        List<Path> xmlFiles = xmlFilesUnder(xmlDir);

        // 扫描规模断言：防止目录定位失败导致「零文件空扫」而假阳性通过
        assertFalse(xmlFiles.isEmpty(), "未扫描到任何 XML mapper 文件（" + xmlDir + "），守护测试失效");

        for (Path xmlFile : xmlFiles) {
            String xml = new String(Files.readAllBytes(xmlFile), StandardCharsets.UTF_8);
            String hit = firstDisallowedSchemaReference(xml);
            if (hit != null) {
                violations.add(xmlFile.getFileName() + " -> " + hit);
            }
        }

        assertTrue(violations.isEmpty(),
                "以下 XML mapper 使用了带库名前缀的表引用，请改为不带库名的写法：" + violations);
    }

    /** 取 MyBatis 注解里的 SQL 文本；非 MyBatis 注解返回 null。 */
    private String annotationSql(Annotation annotation) {
        if (annotation instanceof Select select) {
            return String.join(" ", select.value());
        }
        if (annotation instanceof Insert insert) {
            return String.join(" ", insert.value());
        }
        if (annotation instanceof Update update) {
            return String.join(" ", update.value());
        }
        if (annotation instanceof Delete delete) {
            return String.join(" ", delete.value());
        }
        return null;
    }

    /** 返回第一个不被允许的库名前缀引用；没有则返回 null。 */
    private String firstDisallowedSchemaReference(String sql) {
        Matcher matcher = SCHEMA_QUALIFIED.matcher(sql);
        while (matcher.find()) {
            String schema = matcher.group(1).toLowerCase();
            if (!ALLOWED_SCHEMAS.contains(schema)) {
                return matcher.group();
            }
        }
        // 反引号形式：`ba-prod`.user_basic —— 含连字符的库名只能这样写，前一条正则覆盖不到
        Matcher backtick = BACKTICK_QUALIFIED.matcher(sql);
        while (backtick.find()) {
            String schema = backtick.group(1).trim().toLowerCase();
            if (!ALLOWED_SCHEMAS.contains(schema)) {
                return backtick.group();
            }
        }
        return null;
    }

    /**
     * 定位模块的 src/main 目录。
     * Maven surefire 的工作目录是模块根目录；为兼容在仓库根目录下运行的情况，做一次回退。
     */
    private Path projectMainDir() {
        Path direct = Paths.get("src", "main");
        if (Files.isDirectory(direct)) {
            return direct;
        }
        Path nested = Paths.get("BarcBackend", "src", "main");
        if (Files.isDirectory(nested)) {
            return nested;
        }
        throw new IllegalStateException("无法定位 src/main 目录，请从 BarcBackend 模块目录运行测试");
    }

    private List<Path> javaFilesUnder(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    private List<Path> xmlFilesUnder(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(path -> path.toString().endsWith(".xml")).toList();
        }
    }

    private String toClassName(Path javaRoot, Path javaFile) {
        String relative = javaRoot.relativize(javaFile).toString();
        return relative.substring(0, relative.length() - ".java".length())
                .replace('\\', '.')
                .replace('/', '.');
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
