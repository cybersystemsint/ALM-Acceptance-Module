/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.almKSAZain.config;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 *
 * @author jgithu
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = "com.telkom.almKSAZain.repo", // MySQL repository package
        entityManagerFactoryRef = "mysqlEntityManagerFactory",
        transactionManagerRef = "mysqlTransactionManager"
)
public class DataSourceConfig {
    //.76 
//    @Primary
//    @Bean(name = {"mysqlDataSource", "dataSource"})
//    public DataSource mysqlDataSource() {
//        return DataSourceBuilder.create()
//                .url("jdbc:mysql://127.0.0.1:3306/ALM_ZAIN_KSA")
//                .username("root")
//                .password("ALMDev@2024!")
//                .driverClassName("com.mysql.cj.jdbc.Driver")
//                .build();
//    }

    //KSA UAT
//    @Primary
//    @Bean(name = {"mysqlDataSource", "dataSource"})
//    public DataSource mysqlDataSource() {
//        return DataSourceBuilder.create()
//                .url("jdbc:mysql://10.123.105.59:3306/ALM_ZAIN_KSA")
//                .username("root")
//                .password("ALM-KSA_db20@5")
//                .driverClassName("com.mysql.cj.jdbc.Driver")
//                .build();
//    }
    //KSA PROD 1 
    @Primary
    @Bean(name = {"mysqlDataSource", "dataSource"})
    public DataSource mysqlDataSource() {
        return DataSourceBuilder.create()
                .url("jdbc:mysql://10.123.102.67:3306/ALM_ZAIN_KSA")
                .username("root")
                .password("ALM-KSA_db20@5")
                .driverClassName("com.mysql.cj.jdbc.Driver")
                .build();
    }

    //KSA PROD 2
//    @Primary
//    @Bean(name = {"mysqlDataSource", "dataSource"})
//    public DataSource mysqlDataSource() {
//        return DataSourceBuilder.create()
//                .url("jdbc:mysql://10.123.102.69:3306/ALM_ZAIN_KSA")
//                .username("root")
//                .password("ALM-KSA_db20@5")
//                .driverClassName("com.mysql.cj.jdbc.Driver")
//                .build();
//    }
    //217.160.147.169
//    @Primary
//    @Bean(name = {"mysqlDataSource", "dataSource"})
//    public DataSource mysqlDataSource() {
//        return DataSourceBuilder.create()
//                .url("jdbc:mysql://127.0.0.1:3306/ALM_ZAIN_KSA")
//                .username("root")
//                .password("ALMDev@2024!")
//                .driverClassName("com.mysql.cj.jdbc.Driver")
//                .build();
//    }
    @Primary
    @Bean(name = {"mysqlEntityManagerFactory", "entityManagerFactory"})
    public LocalContainerEntityManagerFactoryBean mysqlEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("mysqlDataSource") DataSource dataSource) {
        return builder
                .dataSource(dataSource)
                .packages("com.telkom.almKSAZain.model") // Package with MySQL entities
                .persistenceUnit("mysql")
                .build();
    }
    @Primary
    @Bean(name = "mysqlTransactionManager")
    public PlatformTransactionManager mysqlTransactionManager(
            @Qualifier("mysqlEntityManagerFactory") EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}
