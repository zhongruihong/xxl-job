package com.xxl.job.core.executor.impl;

import com.xxl.job.core.biz.model.ReturnT;
import com.xxl.job.core.executor.XxlJobExecutor;
import com.xxl.job.core.glue.GlueFactory;
import com.xxl.job.core.handler.IJobHandler;
import com.xxl.job.core.handler.annotation.XxlJob;
import com.xxl.job.core.handler.impl.MethodJobHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * xxl-job executor (for spring)
 *
 * @author xuxueli 2018-11-01 09:24:52
 */
/**
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
public class XxlJobSpringExecutor extends XxlJobExecutor implements ApplicationContextAware, SmartInitializingSingleton, DisposableBean {
    private static final Logger logger = LoggerFactory.getLogger(XxlJobSpringExecutor.class);

    // start
    @Override
    public void afterSingletonsInstantiated() {

        // init JobHandler Repository
        /*initJobHandlerRepository(applicationContext);*/

        // init JobHandler Repository (for method)
        initJobHandlerMethodRepository(applicationContext);

        // refresh GlueFactory
        GlueFactory.refreshInstance(1);

        // super start
        try {
            super.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // destroy
    @Override
    public void destroy() {
        super.destroy();
    }


    /*private void initJobHandlerRepository(ApplicationContext applicationContext) {
        if (applicationContext == null) {
            return;
        }

        // init job handler action
        Map<String, Object> serviceBeanMap = applicationContext.getBeansWithAnnotation(JobHandler.class);

        if (serviceBeanMap != null && serviceBeanMap.size() > 0) {
            for (Object serviceBean : serviceBeanMap.values()) {
                if (serviceBean instanceof IJobHandler) {
                    String name = serviceBean.getClass().getAnnotation(JobHandler.class).value();
                    IJobHandler handler = (IJobHandler) serviceBean;
                    if (loadJobHandler(name) != null) {
                        throw new RuntimeException("xxl-job jobhandler[" + name + "] naming conflicts.");
                    }
                    registJobHandler(name, handler);
                }
            }
        }
    }*/

    /**
     * 利用Spring上下文环境，初始化jobHander（有带注解@XxlJob的方法，且实现了IJobHander）
     * 各种校验：方法参数类型必须为String、返回值类型必须为ReturnT；注解括号中的参数"value"必须赋值（指定jobHander的名字~）），
     * 实际放入一个ConcurrentHashMap<String, IJobHandler>()*/
    private void initJobHandlerMethodRepository(ApplicationContext applicationContext) {
        if (applicationContext == null) {
            return;
        }
        // init job handler from method
        /**
         * applicationContext.getBeanNamesForType作用:
         * 返回与给定类型（包括子类）匹配的bean的名称，
         * */
        String[] beanDefinitionNames = applicationContext.getBeanNamesForType(Object.class, false, true);
        for (String beanDefinitionName : beanDefinitionNames) {
            Object bean = applicationContext.getBean(beanDefinitionName);
            /**
             * 注解XxlJob
             * */
            Map<Method, XxlJob> annotatedMethods = null;   // referred to ：org.springframework.context.event.EventListenerMethodProcessor.processBean
            try {
            	/**
            	 * 静态类MethodIntrospector  内省
            	 * 作用(官方解释)：
            	 * 定义搜索元数据关联方法（包括接口和父类）的算法，同时还处理参数化方法以及使用接口和基于类的代理遇到的常见情况。
            	 * 通常，但不一定，被用来查找有指定注解的方法
            	 * 可以理解为一个方法搜索器?
            	 * */
            	/**返回所有方法和指定注解（这里是@XxlJob）的map*/
                annotatedMethods = MethodIntrospector.selectMethods(bean.getClass(),
                		/**MetadataLookup接口*/
                        new MethodIntrospector.MetadataLookup<XxlJob>() {
                			/**查找有注解XxlJob的方法，通过工具类AnnotatedElementUtils~*/
                            @Override
                            public XxlJob inspect(Method method) {
                            	/**
                            	 * AnnotatedElementUtils 工具类
                            	 * Spring贡献的多个注解相关的工具类：AnnotationUtils（最重要）、AnnotatedElementUtils、AnnotationConfigUtils
                            	 * ps:JDK已经提供获取注解的方法
                            	 介绍Class提供的获取注解相关方法：
									<A extends Annotation>A getAnnotation(Class<A>annotationClass):获取该class对象对应类上指定类型的Annotation，如果该类型注解不存在，则返回null
									Annotation[] getAnnotations():返回修饰该class对象对应类上存在的所有Annotation
									<A extends Annotation>A getDeclaredAnnotation(Class<A>annotationClass):这是Java 8中新增的，该方法获取直接修饰该class对象对应类的指定类型的Annotation，如果不存在，则返回null（也就说只找自己的，继承过来的注解这个方法就不管了）
									Annotation[] getDeclaredAnnotations():返回修饰该Class对象对应类上存在的所有Annotation（同上，继承的不管）
									<A extends Annotation>A[] getAnnotationByType(Class<A>annotationClass):该方法的功能与前面介绍的getAnnotation()方法基本相似，但由于Java8增加了重复注解功能，因此需要使用该方法获取修饰该类的指定类型的多个Annotation（会考虑继承的注解）
									<A extends Annotation>A[] getDeclaredAnnotationByType(Class<A>annotationClass)
                            		
                            		@MyAnno
									interface Eat {
									}
									
									class Parent implements Eat {
									}
									
									class Child extends Parent {
									}
									
									@Target({ElementType.METHOD, ElementType.TYPE})
									@Retention(RetentionPolicy.RUNTIME)
									@RequestMapping // 特意注解上放一个注解，方面测试看结果
									@Inherited
									@interface MyAnno {
									
									}
                            	 *  MyAnno anno1 = Eat.class.getAnnotation(MyAnno.class);//@com.xxx.maintest.MyAnno()
							     *  MyAnno anno2 = Parent.class.getAnnotation(MyAnno.class);//null
							     *  MyAnno anno3 = Child.class.getAnnotation(MyAnno.class);//null
                            	 * 注意：@Inherited继承只能发生在类上，而不能发生在接口上（也就是说标注在接口上仍然是不能被继承的）
                            	 * 注解是不支持继承的，因此不能使用关键字extends来继承某个@interface，
                            	 * 但注解在编译后，编译器会自动继承java.lang.annotation.Annotation接口（从反编译代码里可以看出，类似于Enum）
                            	 * 
                            	 */
                                return AnnotatedElementUtils.findMergedAnnotation(method, XxlJob.class);
                            }
                        });
            } catch (Throwable ex) {
                logger.error("xxl-job method-jobhandler resolve error for bean[" + beanDefinitionName + "].", ex);
            }
            if (annotatedMethods==null || annotatedMethods.isEmpty()) {
                continue;
            }
            /**循环所有的<方法:注解XxlJob>*/
            for (Map.Entry<Method, XxlJob> methodXxlJobEntry : annotatedMethods.entrySet()) {
                Method method = methodXxlJobEntry.getKey();
                XxlJob xxlJob = methodXxlJobEntry.getValue();
                if (xxlJob == null) {
                    continue;
                }
                /**获取注解XxlJob()里面写的value值,使用@XxlJob必须要写value值~~*/
                String name = xxlJob.value();
                if (name.trim().length() == 0) {
                    throw new RuntimeException("xxl-job method-jobhandler name invalid, for[" + bean.getClass() + "#" + method.getName() + "] .");
                }
                /**父类方法:从所有放入ConcurrentHashMap的jobHander（实现IJobHandler）查看有没有放入~~，已经有了就冲突*/
                if (loadJobHandler(name) != null) {
                    throw new RuntimeException("xxl-job jobhandler[" + name + "] naming conflicts.");
                }

                // execute method
                /**
                 * 反射之Class.isAssignableFrom方法：这个方法是用来判断两个类的之间的关联关系，也可以说是一个类是否可以被强制转换为另外一个实例对象
                 * 反射之Method.getParameterTypes()方法返回一个Class对象数组，它们以声明顺序表示由此Method对象表示的方法的形式参数类型。如果底层方法没有参数，则返回长度为0的数组。
                 * 以下意思是，方法只有一个参数，且该参数是String类型。否则报错~~~
                 * 
                 * */
                if (!(method.getParameterTypes().length == 1 && method.getParameterTypes()[0].isAssignableFrom(String.class))) {
                    throw new RuntimeException("xxl-job method-jobhandler param-classtype invalid, for[" + bean.getClass() + "#" + method.getName() + "] , " +
                            "The correct method format like \" public ReturnT<String> execute(String param) \" .");
                }
                /**方法的返回值类型必须是ReturnT.class*/
                if (!method.getReturnType().isAssignableFrom(ReturnT.class)) {
                    throw new RuntimeException("xxl-job method-jobhandler return-classtype invalid, for[" + bean.getClass() + "#" + method.getName() + "] , " +
                            "The correct method format like \" public ReturnT<String> execute(String param) \" .");
                }
                /**
                 * 值为 true 则指示反射的对象在使用时应该取消 Java 语言访问检查。值为 false 则指示反射的对象应该实施 Java 语言访问检查。 
                 * 实际上setAccessible是启用和禁用访问安全检查的开关,并不是为true就能访问为false就不能访问 
                 * 由于JDK的安全检查耗时较多.所以通过setAccessible(true)的方式关闭安全检查就可以达到提升反射速度的目的
                 * 使用了method.setAccessible(true)后 性能有了20倍的提升 
                 * 
                 * */
                method.setAccessible(true);

                // init and destory
                Method initMethod = null;
                Method destroyMethod = null;
                /**@XxlJob 定义有三个可填参数：value、init、destroy*/
                if (xxlJob.init().trim().length() > 0) {
                	/**如果指定了init值，那么获取该值指定的方法，这里又到反射~~~~ */
                    try {
                        initMethod = bean.getClass().getDeclaredMethod(xxlJob.init());
                        initMethod.setAccessible(true);
                    } catch (NoSuchMethodException e) {
                        throw new RuntimeException("xxl-job method-jobhandler initMethod invalid, for[" + bean.getClass() + "#" + method.getName() + "] .");
                    }
                }
                if (xxlJob.destroy().trim().length() > 0) {
                	/**如果指定了destroy值，那么获取该值指定的方法，这里又到反射~~~~ */
                    try {
                        destroyMethod = bean.getClass().getDeclaredMethod(xxlJob.destroy());
                        destroyMethod.setAccessible(true);
                    } catch (NoSuchMethodException e) {
                        throw new RuntimeException("xxl-job method-jobhandler destroyMethod invalid, for[" + bean.getClass() + "#" + method.getName() + "] .");
                    }
                }

                // registry jobhandler
                /**
                 * 注册jobHander（实现了IJobHander）,实际上放入一个 ConcurrentHashMap<String, IJobHandler>()
                 * 这里就完成了jobHander的初始化~~
                 * 
                 */
                registJobHandler(name, new MethodJobHandler(bean, method, initMethod, destroyMethod));
            }
        }

    }

    // ---------------------- applicationContext ----------------------
    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    public static ApplicationContext getApplicationContext() {
        return applicationContext;
    }

}
