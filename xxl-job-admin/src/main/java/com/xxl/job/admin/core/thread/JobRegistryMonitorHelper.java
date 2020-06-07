package com.xxl.job.admin.core.thread;

import com.xxl.job.admin.core.conf.XxlJobAdminConfig;
import com.xxl.job.admin.core.model.XxlJobGroup;
import com.xxl.job.admin.core.model.XxlJobRegistry;
import com.xxl.job.core.enums.RegistryConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * job registry instance
 * @author xuxueli 2016-10-02 19:10:24
 */
public class JobRegistryMonitorHelper {
	private static Logger logger = LoggerFactory.getLogger(JobRegistryMonitorHelper.class);

	private static JobRegistryMonitorHelper instance = new JobRegistryMonitorHelper();
	public static JobRegistryMonitorHelper getInstance(){
		return instance;
	}

	private Thread registryThread;
	private volatile boolean toStop = false;
	public void start(){
		registryThread = new Thread(new Runnable() {
			@Override
			public void run() {
				while (!toStop) {
					try {
						// auto registry group
						/**XxlJobGroupDao执行sql查询，从xxl_job_group查找执行器地址类型：0=自动注册、1=手动录入的执行器*/
						List<XxlJobGroup> groupList = XxlJobAdminConfig.getAdminConfig().getXxlJobGroupDao().findByAddressType(0);
						if (groupList!=null && !groupList.isEmpty()) {

							// remove dead address (admin/executor)
							/**XxlJobRegistryDao执行sql查询，从xxl_job_registry查找更新时间小于当前时间90秒以内的注册的执行器*/
							List<Integer> ids = XxlJobAdminConfig.getAdminConfig().getXxlJobRegistryDao().findDead(RegistryConfig.DEAD_TIMEOUT, new Date());
							if (ids!=null && ids.size()>0) {
								/**找到就移除出xxl_job_registry表*/
								XxlJobAdminConfig.getAdminConfig().getXxlJobRegistryDao().removeDead(ids);
							}

							// fresh online address (admin/executor)
							HashMap<String, List<String>> appAddressMap = new HashMap<String, List<String>>();
							/**XxlJobRegistryDao执行sql查询，从xxl_job_registry查找更新时间大于当前时间90秒以内的注册的执行器*/
							List<XxlJobRegistry> list = XxlJobAdminConfig.getAdminConfig().getXxlJobRegistryDao().findAll(RegistryConfig.DEAD_TIMEOUT, new Date());
							if (list != null) {
								for (XxlJobRegistry item: list) {
									/**如果注册的是EXECUTOR (执行器) ，类型只有EXECUTOR 和 ADMIN**/
									if (RegistryConfig.RegistType.EXECUTOR.name().equals(item.getRegistryGroup())) {
										/**执行器名称*/
										String appname = item.getRegistryKey();
										List<String> registryList = appAddressMap.get(appname);
										if (registryList == null) {
											registryList = new ArrayList<String>();
										}
										/**如果注册列表里面没有该注册地址，就加注册列表**/
										if (!registryList.contains(item.getRegistryValue())) {
											registryList.add(item.getRegistryValue());
										}
										/**将注册的执行器信息：执行器地址，放入执行器map*/
										appAddressMap.put(appname, registryList);
									}
								}
							}

							// fresh group address
							/**遍历自动注册的执行器*/
							for (XxlJobGroup group: groupList) {
								/**获取注册地址*/
								List<String> registryList = appAddressMap.get(group.getAppname());
								String addressListStr = null;
								if (registryList!=null && !registryList.isEmpty()) {
									/**将注册地址，默认按ASCII码排序，一位一位的比较...*/
									Collections.sort(registryList);
									addressListStr = "";
									/**将注册地址用逗号连接*/
									for (String item:registryList) {
										addressListStr += item + ",";
									}
									addressListStr = addressListStr.substring(0, addressListStr.length()-1);
								}
								/**设计执行器的注册地址并更新*/
								group.setAddressList(addressListStr);
								XxlJobAdminConfig.getAdminConfig().getXxlJobGroupDao().update(group);
							}
						}
					} catch (Exception e) {
						if (!toStop) {
							logger.error(">>>>>>>>>>> xxl-job, job registry monitor thread error:{}", e);
						}
					}
					try {
						/**
						 * TimeUnit是java.util.concurrent包下面的一个类，TimeUnit提供了可读性更好的线程暂停操作
						 * 是对Thread.sleep方法的包装，实现是一样的，只是多了时间单位转换和验证，然而TimeUnit枚举成员的方法却提供更好的可读性
						 * 暂停当前线程30秒
						 * 
						 * 
 *  *  *  *  *  *  *  *  *  *  *  *  * * 这里就是每30秒，更新一次注册执行器地址~~~~~~ *  *  *  *  *  *  *  *  *  *  *  *  *  *  *  *  *  * 
						 */
						TimeUnit.SECONDS.sleep(RegistryConfig.BEAT_TIMEOUT);
						/**
						 * sleep与wait的不同点是：sleep并不释放锁，并且sleep的暂停和wait暂停是不一样的。obj.wait会使线程进入obj对象的等待集合中并等待唤醒。
						 * sleep是线程类（Thread）的方法，导致此线程暂停执行指定时间，给执行机会给其他线程，但是监控状态依然保持，到时后会自动恢复 。调用sleep不会释放对象锁。
						 * wait是Object类的方法，对此对象调用wait方法导致本线程放弃对象锁，进入等待此对象的等待锁定池，只有针对此对象发出notify方 法（或notifyAll）后本线程才进入对象锁定池准备获得对象锁进入运行状态。
						 * 
						 * */
					} catch (InterruptedException e) {
						if (!toStop) {
							logger.error(">>>>>>>>>>> xxl-job, job registry monitor thread error:{}", e);
						}
					}
				}
				logger.info(">>>>>>>>>>> xxl-job, job registry monitor thread stop");
			}
		});
		/**
		 * 通过Thread.setDaemon(true)设置为守护线程,在没有用户线程可服务时会自动离开;如果不设置此属性，默认为用户线程；
		 * 通过Thread.setDaemon(false)设置为用户线程,用于为系统中的其它对象和线程提供服务；
		 * setDaemon需要在start方法调用之前使用
		 * 用Thread.isDaemon()来返回是否是守护线程
		 * 
		 * 守护线程是依赖于用户线程，用户线程退出了，守护线程也就会退出，典型的守护线程如垃圾回收线程。
		 * 用户线程是独立存在的，不会因为其他用户线程退出而退出。
		 * 默认情况下启动的线程是用户线程,通过setDaemon(true)将线程设置成守护线程,这个函数务必在线程启动前进行调用，否则会报java.lang.IllegalThreadStateException异常,启动的线程无法变成守护线程,而是用户线程。
		 * */
		registryThread.setDaemon(true);
		registryThread.setName("xxl-job, admin JobRegistryMonitorHelper");
		/**开启线程，上面只是定义了线程。。。这里才正式执行线程，也就是执行上面的逻辑*/
		registryThread.start();
	}
	/***
	 * XxlJobScheduler停止所有线程的方法destroy()中调用
	 */
	public void toStop(){
		toStop = true;
		// interrupt and wait
		registryThread.interrupt();
		try {
			registryThread.join();
		} catch (InterruptedException e) {
			logger.error(e.getMessage(), e);
		}
	}

}
