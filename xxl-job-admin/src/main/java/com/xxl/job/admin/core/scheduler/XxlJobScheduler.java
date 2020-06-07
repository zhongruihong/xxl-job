package com.xxl.job.admin.core.scheduler;

import com.xxl.job.admin.core.conf.XxlJobAdminConfig;
import com.xxl.job.admin.core.thread.*;
import com.xxl.job.admin.core.util.I18nUtil;
import com.xxl.job.core.biz.ExecutorBiz;
import com.xxl.job.core.biz.client.ExecutorBizClient;
import com.xxl.job.core.enums.ExecutorBlockStrategyEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author xuxueli 2018-10-28 00:18:17
 */

public class XxlJobScheduler  {
    private static final Logger logger = LoggerFactory.getLogger(XxlJobScheduler.class);


    public void init() throws Exception {
        // init i18n
        initI18n();
/*****************************以下用都是用的线程：Thread或者ThreadPoolExecutor~*****************************************/
        /***在java中，每次程序运行至少启动2个线程。一个是main线程，一个是垃圾收集线程。
         * 多线程的编程方式有两种：一是继承Thread类，另一种是实现Runnable接口；
         * Thread类是实现了Runnable接口，它们之间实现了多态关系；但是有一个弊端是，继承只能是单继承的
         * 
         * Thread和Runnable的区别和联系
         * 
         * 1、Thread类是在java.lang包中定义的。
         * 多个构造方法和其他多个方法，如：run()、stop()、interrupt()、suspend()等等
         * 一个类只要继承了Thread类同时覆写了本类中的run()方法就可以实现多线程操作了，
         * 但是一个类只能继承一个父类，这是此方法的局限。Thread类也是Runnable接口的子类。 
         * 
         * 2、接口Runnable
         * 只有一个方法：run()
         * 在实际开发中一个多线程的操作很少使用Thread类，而是通过Runnable接口完成。
         * 如果一个类继承Thread，则不适合资源共享。但是如果实现了Runable接口的话，则很容易的实现资源共享：
         * 多个Thread可以同时加载一个Runnable，当各自Thread获得CPU时间片的时候开始运行runnable，runnable里面的资源是被共享的
         * 因为可以将同一个实现了Runnable接口的线程，放入不同的线程中启动：
				new Thread(mt).start();//同一个mt，但是在Thread中就不可以，如果用同一 个实例化对象mt，就会出现异常  
				new Thread(mt).start();//
				new Thread(mt).start();  
			Thread中如果用同一 个实例化对象mt，就会出现异常  ，如：
				MyThread mt1=new MyThread();  
				MyThread mt2=new MyThread();  
				MyThread mt3=new MyThread();  
				mt1.start();//每个线程都各卖了10张，共卖了30张票  
				mt2.start();//但实际只有10张票，每个线程都卖自己的票  
				mt3.start();//没有达到资源共享  
         * 
         * 线程Thread的5状态：

         * 创建：执行new方法创建对象，即进入创建状态

         * 就绪：创建对象后，执行start方法，即被加入线程队列中等待获取CPU资源，这个时候即为就绪状态

         * 运行：CPU腾出时间片，该thread获取了CPU资源开始运行run方法中的代码，即进入了运行状态

         * 阻塞：如果在run方法中执行了sleep方法，或者调用了thread的wait/join方法，即意味着放弃CPU资源而进入阻塞状态，但是还没有运行完毕，待重新获取CPU资源后，重新进入就绪状态

         * 停止：一般停止线程有两种方式：1执行完毕run方法，2调用stop方法，后者不推荐使用。可以在run方法中循环检查某个public变量，当想要停止该线程时候，通过thread.para为false即可以将run提前运行完毕，即进入了停止状态
         * 
         * 
         * 
		         实现Runnable接口比继承Thread类所具有的优势：
		
			1）：适合多个相同的程序代码的线程去处理同一个资源
			
			2）：可以避免java中的单继承的限制
			
			3）：增加程序的健壮性，代码可以被多个线程共享，代码和数据独立
			
			4）：线程池只能放入实现Runable或callable类线程，不能直接放入继承Thread的类
			
		线程分类
		a.　用户线程，比如主线程，连接网络的线程

		b.　守护线程，运行在后台，为用户线程服务 Thread.setDeamon,必须在start方法前调用。守护线程里面不能做一写IO读写的操作。
			因为当用户线程都结束后，守护线程也会随jvm一起被销毁，如果这个时候守护线程里面还有IO未完成的操作，就会崩溃
         */
        // admin registry monitor run
        JobRegistryMonitorHelper.getInstance().start();

        // admin fail-monitor run
        JobFailMonitorHelper.getInstance().start();

        // admin lose-monitor run
        JobLosedMonitorHelper.getInstance().start();

        // admin trigger pool start
        JobTriggerPoolHelper.toStart();

        // admin log report start
        JobLogReportHelper.getInstance().start();

        // start-schedule
        JobScheduleHelper.getInstance().start();

        logger.info(">>>>>>>>> init xxl-job admin success.");
    }

    /**停止所有线程*/
    public void destroy() throws Exception {

        // stop-schedule
        JobScheduleHelper.getInstance().toStop();

        // admin log report stop
        JobLogReportHelper.getInstance().toStop();

        // admin trigger pool stop
        JobTriggerPoolHelper.toStop();

        // admin lose-monitor stop
        JobLosedMonitorHelper.getInstance().toStop();

        // admin fail-monitor stop
        JobFailMonitorHelper.getInstance().toStop();

        // admin registry stop
        JobRegistryMonitorHelper.getInstance().toStop();

    }

    // ---------------------- I18n ----------------------

    private void initI18n(){
        for (ExecutorBlockStrategyEnum item:ExecutorBlockStrategyEnum.values()) {
            item.setTitle(I18nUtil.getString("jobconf_block_".concat(item.name())));
        }
    }

    // ---------------------- executor-client ----------------------
    private static ConcurrentMap<String, ExecutorBiz> executorBizRepository = new ConcurrentHashMap<String, ExecutorBiz>();
    public static ExecutorBiz getExecutorBiz(String address) throws Exception {
        // valid
        if (address==null || address.trim().length()==0) {
            return null;
        }

        // load-cache
        address = address.trim();
        ExecutorBiz executorBiz = executorBizRepository.get(address);
        if (executorBiz != null) {
            return executorBiz;
        }

        // set-cache
        executorBiz = new ExecutorBizClient(address, XxlJobAdminConfig.getAdminConfig().getAccessToken());

        executorBizRepository.put(address, executorBiz);
        return executorBiz;
    }

}
