package com.miaoyu.barc;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置。
 *
 * <p>背景：注册验证码邮件走的是同步 SMTP，实测单封耗时约 12~13 秒。
 * 若把发信放在接口的同步链路上，接口响应时间就被拖到十几秒，
 * 任何短于该时间的客户端超时都会让「验证码已发出、但前端拿不到 unique_id」，
 * 直接导致注册链路断掉（详见注册修复报告）。</p>
 *
 * <p>因此把发信下沉到独立线程池：接口先落库并立即返回 unique_id，邮件随后异步发送。
 * 使用有界队列 + CallerRunsPolicy，保证邮件任务不会被静默丢弃——
 * 队列占满时由调用线程兜底执行，宁可慢也不丢。</p>
 */
@Configuration
public class AsyncConfig {

    /** 邮件异步执行器 Bean 名称，供 @Async("mailTaskExecutor") 引用。 */
    public static final String MAIL_TASK_EXECUTOR = "mailTaskExecutor";

    /**
     * 邮件发送专用线程池。
     *
     * <p>核心线程 2、最大线程 4、队列容量 200，线程名为 mail-* 便于排障；
     * 空闲线程存活 60 秒，容器关闭时等待任务结束（最多 30 秒）。</p>
     *
     * @return 邮件异步执行器
     */
    @Bean(name = MAIL_TASK_EXECUTOR)
    public Executor mailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("mail-");
        // 队列满时由调用线程执行，避免邮件任务被静默丢弃
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 关闭时等待在途邮件发送完成，防止验证码落库成功却发不出信
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
