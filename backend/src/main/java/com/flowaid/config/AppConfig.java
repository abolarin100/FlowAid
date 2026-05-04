package com.flowaid.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
@EnableAsync
public class AppConfig {

    @Value("${flowaid.async.core-pool-size:4}")
    private int corePoolSize;

    @Value("${flowaid.async.max-pool-size:10}")
    private int maxPoolSize;

    @Value("${flowaid.async.queue-capacity:500}")
    private int queueCapacity;

    @Bean(name = "paymentExecutor")
    public Executor paymentTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("payment-worker-");
        executor.setRejectedExecutionHandler((r, exec) -> {
            throw new RuntimeException("Payment queue capacity exceeded; try again shortly");
        });
        executor.initialize();
        return executor;
    }

    @Bean
    public CacheManager cacheManager() {

        return new org.springframework.cache.concurrent.ConcurrentMapCacheManager(
                "dashboard-stats", "campaign-summary");
    }
}