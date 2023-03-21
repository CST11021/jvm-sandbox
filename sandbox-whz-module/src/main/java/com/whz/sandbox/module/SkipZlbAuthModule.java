package com.whz.sandbox.module;

import cn.hutool.core.util.ReflectUtil;
import com.alibaba.jvm.sandbox.api.Information;
import com.alibaba.jvm.sandbox.api.Module;
import com.alibaba.jvm.sandbox.api.ModuleLifecycle;
import com.alibaba.jvm.sandbox.api.ProcessControlException;
import com.alibaba.jvm.sandbox.api.event.BeforeEvent;
import com.alibaba.jvm.sandbox.api.event.Event;
import com.alibaba.jvm.sandbox.api.listener.EventListener;
import com.alibaba.jvm.sandbox.api.listener.ext.EventWatchBuilder;
import com.alibaba.jvm.sandbox.api.listener.ext.EventWatcher;
import com.alibaba.jvm.sandbox.api.resource.*;
import org.kohsuke.MetaInfServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;

import static com.alibaba.jvm.sandbox.api.listener.ext.EventWatchBuilder.PatternType.REGEX;

/**
 * @Author 盖伦
 * @Date 2023/2/24
 */
@MetaInfServices(Module.class)
@Information(id = "skip-zlb-auth", version = "0.0.1")
public class SkipZlbAuthModule implements Module, ModuleLifecycle {

    private final Logger log = LoggerFactory.getLogger("WHZ-MODULE-LOGGER");

    // 以下这些组件jvm-sandbox框架会自动注入进来

    /** 核心类： */
    @Resource
    private ModuleEventWatcher eventWatcher;
    /** 提供模块激活和冻结功能 */
    @Resource
    private ModuleController moduleController;
    /** 沙箱配置信息对应：${SANDBOX_HOME}/cfg/sandbox.properties 文件 */
    @Resource
    private ConfigInfo configInfo;
    /** 模块管理器 */
    @Resource
    private ModuleManager moduleManager;
    /** 提供实时获取模块运行时的事件信息 */
    @Resource
    private EventMonitor eventMonitor;
    /** 提供获取当前沙箱实例已加载类的Class<>信息 */
    @Resource
    private LoadedClassDataSource loadedClassDataSource;

    // private TransmittableThreadLocal<Boolean> threadLocal = new TransmittableThreadLocal();

    @Override
    public void onLoad() throws Throwable {
        System.out.println("开始加载SkipZlbAuthModule");
        log.info("开始加载SkipZlbAuthModule");

        // 关键代码
        final EventWatcher watcher = new EventWatchBuilder(eventWatcher, REGEX)
                // 监控的类
                .onClass("cn.gov.zcy.zlb.web.auth.interceptor.AccessContextInterceptor|cn.gov.zlb.finance.pay.web.controller.BaseController")
                .includeSubClasses()
                // 监控的类方法
                .onBehavior("preHandle|checkUser")
                .onWatching()
                // 核心实现
                .onWatch(new EventListener() {

                    @Override
                    public void onEvent(Event event) throws Throwable {
                        if (event instanceof BeforeEvent) {
                            try {
                                handleBeforeEvent((BeforeEvent) event);
                            } catch (ProcessControlException e) {
                                throw e;
                            } catch (Exception e) {
                                // 注意：这里需要打个日志，不然植入的代码出错了都不知道
                                log.error("植入的代码出错了", e);
                                throw e;
                            }
                        }
                    }

                }, Event.Type.BEFORE);

        moduleController.active();
        System.out.println("完成加载SkipZlbAuthModule");
        log.info("完成加载SkipZlbAuthModule");
    }

    private void handleBeforeEvent(BeforeEvent beforeEvent) throws Exception {
        // 注释掉：这里如果是HttpServletRequest、HttpServletResponse会报序列化错误
        log.info("拦截方法:{}#{}", beforeEvent.javaClassName, beforeEvent.javaMethodName);

        if ("checkUser".equals(beforeEvent.javaMethodName)) {
            // 注意：这里要使用目标类的类加载器来创建实例，不然可能会出现其他的问题
            Class userInfoDTOClass = Class.forName("cn.gov.zlb.finance.pay.domain.dto.UserInfoDTO", false, beforeEvent.javaClassLoader);
            // Class userInfoDTOClass = Class.forName("cn.gov.zlb.finance.pay.domain.dto.UserInfoDTO");

            Object userObject = userInfoDTOClass.newInstance();
            ReflectUtil.invoke(userObject, "setOperatorId", 1L);
            ReflectUtil.invoke(userObject, "setOperatorName", "张三");
            ReflectUtil.invoke(userObject, "setUserId", 2L);
            ReflectUtil.invoke(userObject, "setInstitutionId", 3L);
            ProcessControlException.throwReturnImmediately(userObject);
            return;
        }

        if ("preHandle".equals(beforeEvent.javaMethodName)) {
            // 注意：这里使用这种方式获取HttpServletRequest对象会报：java.lang.ClassCastException类转换错误，因为在sandbox模块中的类加载器和目标机器的类加载器不一样，
            // 所以需要使用下面的反射机制获取值
            // HttpServletRequest request = (HttpServletRequest) beforeEvent.argumentArray[0];
            // String uri = request.getRequestURI();
            // System.out.println("BeforeEvent请求的路径:" + uri + ",请求的ID:" + beforeEvent.invokeId);

            String uri = ReflectUtil.invoke(beforeEvent.argumentArray[0], "getRequestURI");
            System.out.println("BeforeEvent请求的路径:" + uri + ",请求的ID:" + beforeEvent.invokeId);
            ProcessControlException.throwReturnImmediately(Boolean.TRUE);
            return;
        }

    }

    @Override
    public void onUnload() throws Throwable {

    }

    @Override
    public void onActive() throws Throwable {

    }

    @Override
    public void onFrozen() throws Throwable {

    }

    @Override
    public void loadCompleted() {

    }

}
