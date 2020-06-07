package com.xxl.job.executor.core.config;

import com.xxl.job.core.executor.impl.XxlJobSpringExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * xxl-job config
 *
 * @author xuxueli 2017-04-28
 */
/**
 * @Configuration 用于定义配置类，可替换xml配置文件，被注解的类内部包含有一个或多个被@Bean注解的方法，
 * 这些方法将会被AnnotationConfigApplicationContext或AnnotationConfigWebApplicationContext类进行扫描，
 * 并用于构建bean定义，初始化Spring容器~~~~
 * 所以以下@Bean的对象XxlJobSpringExecutor(xxl-job-core)，会被Spring容器初始化，
 * 而该对象实现了3个Spring的接口，继承了一个xxl-job-core的类，在初始化时实现多个方法，
 * 从而实现发现@XxlJob的jobHander，实现jobHander的注入~~~
 * 
 * 
 * 1、ApplicationContextAware 接口
 * 只有一个方法：setApplicationContext
 * 通过实现该方法，Spring容器会自动获得上下文环境对象ApplicationContext，就可以通过这个上下文环境对象得到Spring容器中的Bean。
 * 换句话说，就是这个类可以直接获取spring配置文件中，所有有引用到的bean对象。
 * 
 * 本类实现中，用于获取私有属性applicationContext
 * 
 * 2、SmartInitializingSingleton 接口
 * 只有一个方法：afterSingletonsInstantiated
 * 实现该接口后，当所有单例 bean 都初始化完成以后， 容器会回调该接口的方法 afterSingletonsInstantiated。
 * 主要应用场合就是在所有单例 bean 创建完成之后，可以在该回调中做一些事情。
 * 
 * 本类实现中
 * (1)调用私有initJobHandlerMethodRepository()
 * (2)调用父类XxlJobExecutor:
 * 	GlueFactory.refreshInstance()、 
 * 	start()
 * 
 * 3、DisposableBean 接口
 * 只有一个方法： destroy
 * 对于实现了 DisposableBean 的 bean ，在spring释放该bean后调用它的destroy() 方法。
 * 
 * 本类实现中调用父类XxlJobExecutor的destroy()
 * 
 * */
@Configuration
public class XxlJobConfig {
    private Logger logger = LoggerFactory.getLogger(XxlJobConfig.class);

    @Value("${xxl.job.admin.addresses}")
    private String adminAddresses;

    @Value("${xxl.job.accessToken}")
    private String accessToken;

    @Value("${xxl.job.executor.appname}")
    private String appname;

    @Value("${xxl.job.executor.address}")
    private String address;

    @Value("${xxl.job.executor.ip}")
    private String ip;

    @Value("${xxl.job.executor.port}")
    private int port;

    @Value("${xxl.job.executor.logpath}")
    private String logPath;

    @Value("${xxl.job.executor.logretentiondays}")
    private int logRetentionDays;

    /***
     * @Configuaration 中的 @Bean 每次调用方法，返回的是同一个bean~CGLB动态代理.. 
     */
    @Bean
    public XxlJobSpringExecutor xxlJobExecutor() {
        logger.info(">>>>>>>>>>> xxl-job config init.");
        XxlJobSpringExecutor xxlJobSpringExecutor = new XxlJobSpringExecutor();
        xxlJobSpringExecutor.setAdminAddresses(adminAddresses);
        xxlJobSpringExecutor.setAppname(appname);
        xxlJobSpringExecutor.setAddress(address);
        xxlJobSpringExecutor.setIp(ip);
        xxlJobSpringExecutor.setPort(port);
        xxlJobSpringExecutor.setAccessToken(accessToken);
        xxlJobSpringExecutor.setLogPath(logPath);
        xxlJobSpringExecutor.setLogRetentionDays(logRetentionDays);

        return xxlJobSpringExecutor;
    }

    /**
     * 针对多网卡、容器内部署等情况，可借助 "spring-cloud-commons" 提供的 "InetUtils" 组件灵活定制注册IP；
     *
     *      1、引入依赖：
     *          <dependency>
     *             <groupId>org.springframework.cloud</groupId>
     *             <artifactId>spring-cloud-commons</artifactId>
     *             <version>${version}</version>
     *         </dependency>
     *
     *      2、配置文件，或者容器启动变量
     *          spring.cloud.inetutils.preferred-networks: 'xxx.xxx.xxx.'
     *
     *      3、获取IP
     *          String ip_ = inetUtils.findFirstNonLoopbackHostInfo().getIpAddress();
     */


}