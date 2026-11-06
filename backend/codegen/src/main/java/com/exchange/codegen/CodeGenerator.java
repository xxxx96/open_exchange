package com.exchange.codegen;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.config.rules.NamingStrategy;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;
import com.baomidou.mybatisplus.generator.query.DefaultQuery;

import java.util.Collections;

/**
 * MyBatis-Plus 代码生成器
 * 
 * 使用方式：只需修改 main 方法中的 serviceName 和 tables 变量
 *
 * @author yang
 */
public class CodeGenerator {

    // ==================== 数据库配置 ====================
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/crypto_exchange?currentSchema=public";
    private static final String DB_USERNAME = "postgres";
    private static final String DB_PASSWORD = "postgres";

    // ==================== 基础包配置 ====================
    public static final String BASE_PACKAGE = "com.openexchange";

    // ==================== 作者信息 ====================
    private static final String AUTHOR = "yanglei";
    private static final String COMMENT_DATE_PATTERN = "yyyy-MM-dd";

    /**
     * 通用代码生成方法
     *
     * @param moduleRoot  模块根目录（如 ../user-service）
     * @param serviceName 服务名称（如 user、trade、wallet 等）
     * @param tables      表名数组
     */
    public static void generate(String moduleRoot, String serviceName, String[] tables) {
        String basePackage = BASE_PACKAGE + "." + serviceName;
        String outputDir = moduleRoot + "/src/main/java";
        String xmlDir = moduleRoot + "/src/main/resources/mapper";
                
        FastAutoGenerator.create(DB_URL, DB_USERNAME, DB_PASSWORD)
                .dataSourceConfig(builder -> builder.databaseQueryClass(DefaultQuery.class))
                .globalConfig(builder -> builder
                        .outputDir(outputDir)
                        .author(AUTHOR)
                        .enableSwagger()
                        .enableSpringdoc()  // 使用 SpringDoc (OpenAPI 3)，生成 @Schema 而不是 @ApiModel
                        .commentDate(COMMENT_DATE_PATTERN)
                        .build()
                )
                .packageConfig(builder -> builder
                        .parent(basePackage)
                        .entity("entity")
                        .mapper("mapper")
                        .service("service")
                        .serviceImpl("service.impl")
                        .controller("controller")
                        .pathInfo(Collections.singletonMap(OutputFile.xml, xmlDir))
                )
                .strategyConfig(builder -> builder
                        .addInclude(tables)
                        // Entity 配置
                        .entityBuilder()
                        .enableLombok()
                        .enableTableFieldAnnotation()
                        .naming(NamingStrategy.underline_to_camel)
                        .columnNaming(NamingStrategy.underline_to_camel)
                        .superClass("org.openexchange.common.web.entity.BaseEntity")
                        .addSuperEntityColumns("id", "created_at", "updated_at")
                        .logicDeleteColumnName("deleted")
                        .versionColumnName("version")
                        .enableFileOverride()  // 确保实体文件覆盖
                        // Service 配置
                        .serviceBuilder()
                        .enableFileOverride()  // 覆盖 Service 文件
                        // Controller 配置
                        .controllerBuilder()
                        .enableRestStyle()
                        .enableFileOverride()  // 覆盖 Controller 文件
                        // Mapper 配置
                        .mapperBuilder()
                        .enableMapperAnnotation()
                        .enableFileOverride()  // 覆盖 Mapper 文件
                )
                .templateEngine(new FreemarkerTemplateEngine())
                .execute();

        System.out.println("========================================");
        System.out.println("代码生成完成！");
        System.out.println("模块目录：" + moduleRoot);
        System.out.println("服务名：" + serviceName);
        System.out.println("包名：" + basePackage);
        System.out.println("表名：" + String.join(", ", tables));
        System.out.println("========================================");
    }

    /**
     * 主方法 - 修改这里的三个配置即可
     */
    public static void main(String[] args) {
        // ====== 只需修改这三个变量 ======
        String moduleRoot = "backend/wallet-service";  // 1. 模块目录（相对于项目根目录）
        String serviceName = "wallet";           // 2. 服务名
        String[] tables = {"wallet","deposit","withdrawal","deposit_address","currency"};// 3. 表名数组
        // =================================

        generate(moduleRoot, serviceName, tables);
    }
}
