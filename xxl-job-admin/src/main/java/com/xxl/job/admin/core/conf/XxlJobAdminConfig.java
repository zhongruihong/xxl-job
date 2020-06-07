package com.xxl.job.admin.core.conf;

import com.xxl.job.admin.core.alarm.JobAlarmer;
import com.xxl.job.admin.core.scheduler.XxlJobScheduler;
import com.xxl.job.admin.dao.*;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.util.Arrays;

/**
 * xxl-job config
 *
 * @author xuxueli 2017-04-28
 */
/**
 * 和执行器项目中的配置@Configuration区别
 * Spring @Configuration 和 @Component 区别
 * @Component 注解的范围最广，所有类都可以注解，但是@Configuration注解一般注解在这样的类上：
 * 这个类里面有@Value注解的成员变量和@Bean注解的方法，就是一个配置类。
 * 一句话概括就是 @Configuration 中所有带 @Bean 注解的方法都会被动态代理(Enhancer)，因此调用该方法返回的都是同一个实例。
 * 因为Spring发现方法所请求的Bean已经在容器中，那么就直接返回容器中的Bean。所以全局只有一个SimpleBean对象的实例。
 * 如果使用@Configuration，所有用@Bean标记的方法会被包装成CGLIB的wrapper，
 * 其工作原理是:如果方式是首次被调用那么原始的方法体会被执行并且结果对象会被注册到Spring上下文中。
 * 之后所有的对该方法的调用仅仅只是从Spring上下文中取回该对象返回给调用者。
 * 
 * @Component 注解并没有通过 cglib 来代理@Bean 方法的调用，因此调用该方法返回的就是两个不同的实例。
 * Bean不会被Spring代理，会直接调用方法获取一个全新的Bean对象实例~
 * 有些特殊情况下，我们不希望 XxxBeanConfig 被代理（代理后会变成WebMvcConfig$$EnhancerBySpringCGLIB$$8bef3235293）时，
 * 就得用 @Component，这种情况下，可以使用@Autowired注入，并@Bean返回，这样可以保证使用的同一个 实例。(@Configuration不用@Autowired 就可以返回同一个实例~)

 * @Configuration 注解本质上还是 @Component
 * Spring 容器在启动时，会加载默认的一些 PostPRocessor，
 * 其中就有 ConfigurationClassPostProcessor，
 * 这个后置处理程序专门处理带有 @Configuration 注解的类， 这个程序会在 bean 定义加载完成后，
 * 在 bean 初始化前进行处理。主要处理的过程就是使用 cglib 动态代理增强类，
 * 而且是对其中带有 @Bean 注解的方法进行处理。
 * 
 @Configuration 标记的类必须符合下面的要求：
	配置类必须以类的形式提供（不能是工厂方法返回的实例），允许通过生成子类在运行时增强（cglib 动态代理）。
	配置类不能是 final 类（没法动态代理）。
	配置注解通常为了通过 @Bean 注解生成 Spring 容器管理的类，
	配置类必须是非本地的（即不能在方法中声明，不能是 private）。
	任何嵌套配置类都必须声明为static。
	@Bean 方法可能不会反过来创建进一步的配置类（也就是返回的 bean 如果带有 @Configuration，也不会被特殊处理，只会作为普通的 bean）。
加载过程：
	Spring 容器在启动时，会加载默认的一些 PostProcessor，其中就有 ConfigurationClassPostProcessor，
	这个后置处理程序专门处理带有 @Configuration 注解的类，这个程序会在 bean 定义加载完成后,在 bean 初始化前进行处理。
	主要处理的过程就是使用 cglib 动态代理增强类，而且是对其中带有 @Bean 注解的方法进行处理。
 * */

/***
 * 
 * 1、DisposableBean 接口  spring-bean模块中
 * 只有一个方法： destroy
 * 对于实现了 DisposableBean 的 bean ，在spring释放该bean后调用它的destroy() 方法。 
 * 
 * 在Bean生命周期结束前调用destory()方法做一些收尾工作，亦可以使用配置文件中指定destory-method。
 * 前者与Spring耦合高（destory），使用类型强转.方法名()，效率高；后者耦合低（destory-method），使用反射，效率相对低
 * 
 * 
 * 2、InitializingBean接口  spring-bean模块中
 * 只有一个方法：afterPropertiesSet
 * 凡是继承该接口的子类，在初始化bean的时候会执行该方法。
 * 
 * spring为bean提供了两种初始化bean的方式，实现InitializingBean接口，实现afterPropertiesSet方法，
 * 或者在配置文件中同过init-method指定，两种方式可以同时使用
 * 初始化顺序：Constructor > @PostConstruct > InitializingBean > init-method
 * @PostConstruct 注解后的方法在BeanPostProcessor前置处理器中就被执行了，所以当然要先于InitializingBean和init-method执行了。
 * --> Spring IOC容器实例化Bean
 *--> 调用BeanPostProcessor的postProcessBeforeInitialization方法 (@PostConstruct在此)
 *--> 调用bean实例的初始化方法（invokeInitMethods-> InitializingBean->init-method)
 *--> 调用BeanPostProcessor的postProcessAfterInitialization方法
 */
@Component
public class XxlJobAdminConfig implements InitializingBean, DisposableBean {/**初始时afterPropertiesSet、销毁时destroy*/

    private static XxlJobAdminConfig adminConfig = null;/**static:加在属性前面代表“类属性”！该属性独立于该类的任何对象，只有一个XxlJobAdminConfig对象~*/
    public static XxlJobAdminConfig getAdminConfig() {
        return adminConfig;
    }


    // ---------------------- XxlJobScheduler ----------------------

    private XxlJobScheduler xxlJobScheduler;

    @Override
    public void afterPropertiesSet() throws Exception {
        adminConfig = this;

        xxlJobScheduler = new XxlJobScheduler();/**整个容器中也只有一个XxlJobScheduler对象?*/
        /**
         * 这个方法厉害了，初始化各种线程：
         * 
         * 
         * */
        xxlJobScheduler.init();
    }

    @Override
    public void destroy() throws Exception {
    	/**停止各种线程*/
        xxlJobScheduler.destroy();
    }


    // ---------------------- XxlJobScheduler ----------------------

    // conf
    @Value("${xxl.job.i18n}")
    private String i18n;

    @Value("${xxl.job.accessToken}")
    private String accessToken;

    @Value("${spring.mail.username}")
    private String emailUserName;

    @Value("${xxl.job.triggerpool.fast.max}")
    private int triggerPoolFastMax;

    @Value("${xxl.job.triggerpool.slow.max}")
    private int triggerPoolSlowMax;

    @Value("${xxl.job.logretentiondays}")
    private int logretentiondays;

    // dao, service
    /**
     *  在java代码中使用@Autowired或@Resource注解方式进行装配，
     *  这两个注解的区别是：
     *  @Autowired 默认按类型装配依赖对象，
     *  @Autowired 注解是按类型装配，默认情况下它要求依赖对象必须存在，如果允许null值，可以设置它required属性为false。
     *  如果我们想使用按名称装配，可以结合@Qualifier注解一起使用。如下：    @Autowired  @Qualifier("personDaoBean")   
     *  
     *  @Resource 默认按名称装配，当找不到与名称匹配的bean才会按类型装配。   
     *  @Resource 注解和@Autowired一样，也可以标注在字段或属性的setter方法上，但它默认按名称装配。
     *  名称可以通过@Resource的name属性指定，如果没有指定name属性，
     *  当注解标注在字段上，即默认取字段的名称作为bean名称寻找依赖对象，
     *  当注解标注在属性的setter方法上，即默认取属性名作为bean名称寻找依赖对象。    @Resource(name=“personDaoBean”)    
     * */
    @Resource
    private XxlJobLogDao xxlJobLogDao;
    @Resource
    private XxlJobInfoDao xxlJobInfoDao;
    @Resource
    private XxlJobRegistryDao xxlJobRegistryDao;
    @Resource
    private XxlJobGroupDao xxlJobGroupDao;
    @Resource
    private XxlJobLogReportDao xxlJobLogReportDao;
    @Resource
    private JavaMailSender mailSender;
    @Resource
    private DataSource dataSource;/**注入数据源*/
    @Resource
    private JobAlarmer jobAlarmer;

    /**
     * 
		@Component------------------------泛指组件，当组件不好归类的时候，我们可以使用这个注解进行标注。(Component-------成分; 组分; 零件)
		
		@Resource------------------------(资源)
		
		@Autowired-----------------------(自动绑定)
		
		@Repository-----------------------于标注数据访问组件，即DAO组件(repository-------仓库; 贮藏室，容器。)
		
		@Service----------------------------用于标注业务层组件（我们通常定义的service层就用这个）
		
		@Controller-------------------------用于标注控制层组件（如struts中的action）
		
		这几个注解的作用相同：都是为实现所在类(即组件)的bean的转化，然后可以在容器中调用。然后从名字上的作用就是可以明确各个层次和层次的作用。
		首先还是先了解为什么要bean转化。
     * */
    
    
    
    
    
    
    
    
    

    public String getI18n() {
        if (!Arrays.asList("zh_CN", "zh_TC", "en").contains(i18n)) {
            return "zh_CN";
        }
        return i18n;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getEmailUserName() {
        return emailUserName;
    }

    public int getTriggerPoolFastMax() {
        if (triggerPoolFastMax < 200) {
            return 200;
        }
        return triggerPoolFastMax;
    }

    public int getTriggerPoolSlowMax() {
        if (triggerPoolSlowMax < 100) {
            return 100;
        }
        return triggerPoolSlowMax;
    }

    public int getLogretentiondays() {
        if (logretentiondays < 7) {
            return -1;  // Limit greater than or equal to 7, otherwise close
        }
        return logretentiondays;
    }

    public XxlJobLogDao getXxlJobLogDao() {
        return xxlJobLogDao;
    }

    public XxlJobInfoDao getXxlJobInfoDao() {
        return xxlJobInfoDao;
    }

    public XxlJobRegistryDao getXxlJobRegistryDao() {
        return xxlJobRegistryDao;
    }

    public XxlJobGroupDao getXxlJobGroupDao() {
        return xxlJobGroupDao;
    }

    public XxlJobLogReportDao getXxlJobLogReportDao() {
        return xxlJobLogReportDao;
    }

    public JavaMailSender getMailSender() {
        return mailSender;
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    public JobAlarmer getJobAlarmer() {
        return jobAlarmer;
    }

}
