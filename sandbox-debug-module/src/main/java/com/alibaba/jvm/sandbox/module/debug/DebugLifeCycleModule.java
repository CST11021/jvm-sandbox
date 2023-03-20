package com.alibaba.jvm.sandbox.module.debug;

import com.alibaba.jvm.sandbox.api.Information;
import com.alibaba.jvm.sandbox.api.Module;
import com.alibaba.jvm.sandbox.api.ModuleLifecycle;
import com.alibaba.jvm.sandbox.api.annotation.Command;
import com.alibaba.jvm.sandbox.api.event.Event;
import com.alibaba.jvm.sandbox.api.filter.ExtFilter;
import com.alibaba.jvm.sandbox.api.http.printer.ConcurrentLinkedQueuePrinter;
import com.alibaba.jvm.sandbox.api.http.printer.Printer;
import com.alibaba.jvm.sandbox.api.listener.EventListener;
import com.alibaba.jvm.sandbox.api.resource.ModuleEventWatcher;
import com.alibaba.jvm.sandbox.api.resource.ModuleManager;
import org.kohsuke.MetaInfServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import java.io.PrintWriter;
import java.util.Map;

import static com.alibaba.jvm.sandbox.module.debug.ParamSupported.getParameter;

/**
 * 不增强任何类，只为体验沙箱模块生命周期
 * ModuleLifecycle的方法是在模块发生变更前调用的
 * 在变更前需要做处理时，可以通过实现ModuleLifecycle接口进行控制
 * 在变更前不需要做任何处理时，可以不实现ModuleLifecycle接口
 * onLoad，load
 * onActivity，activity
 * onFrozen，frozen
 * onUnload，unload
 * loadCompleted
 */
@MetaInfServices(Module.class)
@Information(id = "debug-lifecycle", version = "0.0.1", author = "luanjia@taobao.com")
public class DebugLifeCycleModule implements Module, ModuleLifecycle {

    private final Logger lifeCLogger = LoggerFactory.getLogger("DEBUG-LIFECYCLE-LOGGER");

    @Resource
    private ModuleEventWatcher moduleEventWatcher;

    @Resource
    private ModuleManager moduleManager;

    /**
     * 84154
     * sh ~/sandbox/bin/sandbox.sh -p 84154 -d 'debug-lifecycle/control?class=testClass&method=testMethod'
     *
     * 命令：sh ${sandbox_home}/bin/sandbox.sh -p ${PID} -d 'debug-lifecycle/control?class=testClass&method=testMethod'
     * 该命名仅仅只是测试模块的声明周期
     *
     * @param param
     * @param writer
     */
    @Command("control")
    public void control(final Map<String, String> param, final PrintWriter writer) {
        final Printer printer = new ConcurrentLinkedQueuePrinter(writer);

        // --- 解析参数 ---

        final String cnPattern = getParameter(param, "class");
        final String mnPattern = getParameter(param, "method");
        lifeCLogger.info("param.class={}", cnPattern);
        lifeCLogger.info("param.method={}", mnPattern);


        int watcherId = moduleEventWatcher.watch(
                // 不增强类，这里只是体验sandbox的生命周期，ExtFilter新增了增强接口的所有实现类，到boostrap ClassLoader中加载类 的能力
                new ExtFilter() {

                    @Override
                    public boolean doClassFilter(int access, String javaClassName, String superClassTypeJavaClassName, String[] interfaceTypeJavaClassNameArray, String[] annotationTypeJavaClassNameArray) {
//                        if (cnPattern != null || !mnPattern.isEmpty())
//                            return javaClassName.matches(cnPattern);
                        return false;
                    }

                    @Override
                    public boolean doMethodFilter(int access, String javaMethodName, String[] parameterTypeJavaClassNameArray, String[] throwsTypeJavaClassNameArray, String[] annotationTypeJavaClassNameArray) {
//                        if (mnPattern != null || !mnPattern.isEmpty())
//                            return javaMethodName.matches(mnPattern);
                        return false;
                    }

                    @Override
                    public boolean isIncludeSubClasses() {// 搜索子类或实现类
                        return true;
                    }

                    @Override
                    public boolean isIncludeBootstrap() {// 搜索来自BootstrapClassLoader所加载的类
                        return true;
                    }
                },
                // 监听到的事件，不做任何处理
                new EventListener() {
                    @Override
                    public void onEvent(Event event) throws Throwable {

                    }
                },
                // 如果有增强类，可以通过这里查看增强的进度
                new ModuleEventWatcher.Progress() {
                    @Override
                    public void begin(int total) {
                        lifeCLogger.info("Begin to transform class,total={}", total);
                    }

                    @Override
                    public void progressOnSuccess(Class clazz, int index) {
                        lifeCLogger.info("Transform class success,class={},index={}", clazz.getName(), index);
                    }

                    @Override
                    public void progressOnFailed(Class clazz, int index, Throwable cause) {
                        lifeCLogger.error("Transform class fail,class={},index={}", clazz.getName(), index, cause);
                    }

                    @Override
                    public void finish(int cCnt, int mCnt) {
                        lifeCLogger.info("Finish to transform class,classCount={},methodCount={}", cCnt, mCnt);
                    }
                },
                Event.Type.BEFORE,
                Event.Type.LINE,
                Event.Type.RETURN,
                Event.Type.THROWS);

        lifeCLogger.info("Add watcher success,watcher id = [{}]", watcherId);

        try {
            // 模块load完成后，模块已经被激活
            lifeCLogger.info("after sandbox-module-debug-lifecycle load Completed，module isActivated = {}", moduleManager.isActivated("debug-lifecycle"));

            // 冻结模块
            lifeCLogger.info("sandbox-module-debug-lifecycle start frozen");
            moduleManager.frozen("debug-lifecycle");
            lifeCLogger.info("sandbox-module-debug-lifecycle frozen is over");

            // 激活模块
            lifeCLogger.info("sandbox-module-debug-lifecycle start active");
            moduleManager.active("debug-lifecycle");
            lifeCLogger.info("sandbox-module-debug-lifecycle active is over");

            // 刷新模块
            lifeCLogger.info("sandbox-module-debug-lifecycle start flush");
            moduleManager.flush(false);
            lifeCLogger.info("sandbox-module-debug-lifecycle flush is over");

            // 重置模块
            lifeCLogger.info("sandbox-module-debug-lifecycle start reset");
            moduleManager.reset();
            lifeCLogger.info("sandbox-module-debug-lifecycle reset is over");

        } catch (Throwable e) {
            lifeCLogger.error("sandbox lifecycle is fail, " + e.getCause());
        }
    }

    /**
     * 模块加载，模块开始加载之前调用！
     * <p>
     * 模块加载是模块生命周期的开始，在模块生命中期中有且只会调用一次。
     * 这里抛出异常将会是阻止模块被加载的唯一方式，如果模块判定加载失败，将会释放掉所有预申请的资源，模块也不会被沙箱所感知
     * </p>
     *
     * @throws Throwable 加载模块失败
     */
    @Override
    public void onLoad() throws Throwable {
        lifeCLogger.info("sandbox-module-debug-lifecycle onLoaded.");
    }

    /**
     * 模块卸载，模块开始卸载之前调用！
     * <p>
     * 模块卸载是模块生命周期的结束，在模块生命中期中有且只会调用一次。
     * 这里抛出异常将会是阻止模块被卸载的唯一方式，如果模块判定卸载失败，将不会造成任何资源的提前关闭与释放，模块将能继续正常工作
     * </p>
     *
     * @throws Throwable 卸载模块失败
     */
    @Override
    public void onUnload() throws Throwable {
        lifeCLogger.info("sandbox-module-debug-lifecycle onUnload.");
    }

    /**
     * 模块激活
     * <p>
     * 模块被激活后，模块所增强的类将会被激活，所有{@link com.alibaba.jvm.sandbox.api.listener.EventListener}将开始收到对应的事件
     * </p>
     * <p>
     * 这里抛出异常将会是阻止模块被激活的唯一方式
     * </p>
     *
     * @throws Throwable 模块激活失败
     */
    @Override
    public void onActive() throws Throwable {
        lifeCLogger.info("sandbox-module-debug-lifecycle onActive.");
    }

    /**
     * 模块冻结
     * <p>
     * 模块被冻结后，模块所持有的所有{@link com.alibaba.jvm.sandbox.api.listener.EventListener}将被静默，无法收到对应的事件。
     * 需要注意的是，模块冻结后虽然不再收到相关事件，但沙箱给对应类织入的增强代码仍然还在。
     * </p>
     * <p>
     * 这里抛出异常将会是阻止模块被冻结的唯一方式
     * </p>
     *
     * @throws Throwable 模块冻结失败
     */
    @Override
    public void onFrozen() throws Throwable {
        lifeCLogger.info("sandbox-module-debug-lifecycle onFrozen.");
    }

    /**
     * 模块加载完成，模块完成加载后调用！
     * <p>
     * 模块完成加载是在模块完成所有资源加载、分配之后的回调，在模块生命中期中有且只会调用一次。
     * 这里抛出异常不会影响模块被加载成功的结果。
     * </p>
     * <p>
     * 模块加载完成之后，所有的基于模块的操作都可以在这个回调中进行
     * </p>
     */
    @Override
    public void loadCompleted() {
        lifeCLogger.info("sandbox-module-debug-lifecycle loadCompleted.");
    }

}
