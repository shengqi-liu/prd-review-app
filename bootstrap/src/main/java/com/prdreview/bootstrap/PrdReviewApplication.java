package com.prdreview.bootstrap;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AI 产品方案评审系统启动类。
 *
 * <p>显式声明扫描根包 {@code com.prdreview}，覆盖 api / application / domain / infrastructure 全部子模块。</p>
 */
@SpringBootApplication(scanBasePackages = "com.prdreview")
@MapperScan("com.prdreview.**.mapper")
public class PrdReviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrdReviewApplication.class, args);
    }
}
