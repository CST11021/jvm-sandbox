package com.alibaba.jvm.sandbox.core.manager.impl;

import com.alibaba.jvm.sandbox.api.event.Event;
import com.alibaba.jvm.sandbox.api.listener.EventListener;
import com.alibaba.jvm.sandbox.core.enhance.EventEnhancer;
import com.alibaba.jvm.sandbox.core.util.ObjectIDs;
import com.alibaba.jvm.sandbox.core.util.SandboxClassUtils;
import com.alibaba.jvm.sandbox.core.util.SandboxProtector;
import com.alibaba.jvm.sandbox.core.util.matcher.Matcher;
import com.alibaba.jvm.sandbox.core.util.matcher.MatchingResult;
import com.alibaba.jvm.sandbox.core.util.matcher.UnsupportedMatcher;
import com.alibaba.jvm.sandbox.core.util.matcher.structure.ClassStructure;
import com.alibaba.jvm.sandbox.core.util.matcher.structure.ClassStructureFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.Set;

/**
 * 沙箱类形变器
 *
 * @author luanjia@taobao.com
 */
public class SandboxClassFileTransformer implements ClassFileTransformer {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final int watchId;
    private final String uniqueId;
    /** 用于匹配哪些类的哪些方法需要进行监听 */
    private final Matcher matcher;

    private final int listenerId;
    private final EventListener eventListener;
    private final Event.Type[] eventTypeArray;

    /** 如果未开启unsafe开关，是不允许增强来自BootStrapClassLoader的类 */
    private final boolean isEnableUnsafe;

    private final String namespace;

    /** 增强类的统计器：统计影响类和方法的统计信息 */
    private final AffectStatistic affectStatistic = new AffectStatistic();

    SandboxClassFileTransformer(final int watchId,
                                final String uniqueId,
                                final Matcher matcher,
                                final EventListener eventListener,
                                final boolean isEnableUnsafe,
                                final Event.Type[] eventTypeArray,
                                final String namespace) {
        this.watchId = watchId;
        this.uniqueId = uniqueId;
        this.matcher = matcher;
        this.eventListener = eventListener;
        this.isEnableUnsafe = isEnableUnsafe;
        this.eventTypeArray = eventTypeArray;
        this.namespace = namespace;
        this.listenerId = ObjectIDs.instance.identity(eventListener);
    }

    /**
     *
     * @param loader                定义要转换的类加载器；如果是引导加载器，则为 null
     * @param internalClassName     完全限定类内部形式的类名称和 The Java Virtual Machine Specification 中定义的接口名称。例如，"java/util/List"。
     * @param classBeingRedefined   如果是被重定义或重转换触发，则为重定义或重转换的类；如果是类加载，则为 null
     * @param protectionDomain      要定义或重定义的类的保护域
     * @param srcByteCodeArray      类文件格式的输入字节缓冲区（不得修改）
     *
     * @return 一个格式良好的类文件缓冲区（转换的结果），如果未执行转换,则返回 null。
     */
    @Override
    public byte[] transform(final ClassLoader loader,
                            final String internalClassName,
                            final Class<?> classBeingRedefined,
                            final ProtectionDomain protectionDomain,
                            final byte[] srcByteCodeArray) {

        SandboxProtector.instance.enterProtecting();
        try {

            // 这里过滤掉Sandbox所需要的类|来自SandboxClassLoader所加载的类|来自ModuleJarClassLoader加载的类
            // 防止ClassCircularityError的发生
            if (SandboxClassUtils.isComeFromSandboxFamily(internalClassName, loader)) {
                return null;
            }

            return _transform(
                    loader,
                    internalClassName,
                    classBeingRedefined,
                    srcByteCodeArray
            );


        } catch (Throwable cause) {
            logger.warn("sandbox transform {} in loader={}; failed, module={} at watch={}, will ignore this transform.",
                    internalClassName,
                    loader,
                    uniqueId,
                    watchId,
                    cause
            );
            return null;
        } finally {
            SandboxProtector.instance.exitProtecting();
        }
    }

    /**
     * 类变形实现
     *
     * @param loader                当前转换类对应的类加载器
     * @param internalClassName     当前转换类的完全限定类名，例如：java/util/List
     * @param classBeingRedefined   如果是被重定义或重转换触发，则为重定义或重转换的类；如果是类加载，则为 null
     * @param srcByteCodeArray      类文件格式的输入字节缓冲区（不得修改）
     * @return 返回增强后类的字节数组
     */
    private byte[] _transform(final ClassLoader loader,
                              final String internalClassName,
                              final Class<?> classBeingRedefined,
                              final byte[] srcByteCodeArray) {
        // 如果未开启unsafe开关，是不允许增强来自BootStrapClassLoader的类
        if (!isEnableUnsafe && null == loader) {
            logger.debug("transform ignore {}, class from bootstrap but unsafe.enable=false.", internalClassName);
            return null;
        }

        // 获取加载类的结构信息
        final ClassStructure classStructure = getClassStructure(loader, classBeingRedefined, srcByteCodeArray);
        // 返回类匹配的行为结构：类的构造方法、普通方法、静态方法，统一称呼为类的行为结构
        final MatchingResult matchingResult = new UnsupportedMatcher(loader, isEnableUnsafe).and(matcher).matching(classStructure);
        // 获取行为结构的签名：这些签名就是我们要关注的切点
        final Set<String> behaviorSignCodes = matchingResult.getBehaviorSignCodes();

        // 如果一个行为都没匹配上也不用继续了
        if (!matchingResult.isMatched()) {
            logger.debug("transform ignore {}, no behaviors matched in loader={}", internalClassName, loader);
            return null;
        }

        // 开始进行类匹配
        try {
            final byte[] toByteCodeArray = new EventEnhancer().toByteCodeArray(
                    loader,
                    srcByteCodeArray,
                    behaviorSignCodes,
                    namespace,
                    listenerId,
                    eventTypeArray
            );

            // 类的字节数组没什么变化，说明没有做什么增强
            if (srcByteCodeArray == toByteCodeArray) {
                logger.debug("transform ignore {}, nothing changed in loader={}", internalClassName, loader);
                return null;
            }

            // 统计增强的类个数、方法个数等
            affectStatistic.statisticAffect(loader, internalClassName, behaviorSignCodes);

            logger.info("transform {} finished, by module={} in loader={}", internalClassName, uniqueId, loader);
            return toByteCodeArray;
        } catch (Throwable cause) {
            logger.warn("transform {} failed, by module={} in loader={}", internalClassName, uniqueId, loader, cause);
            return null;
        }
    }

    /**
     * 获取当前类结构
     *
     * @param loader                类加载器
     * @param classBeingRedefined   变形类的Class
     * @param srcByteCodeArray      类元数据的字节数组
     * @return
     */
    private ClassStructure getClassStructure(final ClassLoader loader, final Class<?> classBeingRedefined, final byte[] srcByteCodeArray) {
        return null == classBeingRedefined
                ? ClassStructureFactory.createClassStructure(srcByteCodeArray, loader)
                : ClassStructureFactory.createClassStructure(classBeingRedefined);
    }

    /**
     * 获取观察ID
     *
     * @return 观察ID
     */
    int getWatchId() {
        return watchId;
    }

    /**
     * 获取事件监听器
     *
     * @return 事件监听器
     */
    EventListener getEventListener() {
        return eventListener;
    }

    /**
     * 获取事件监听器ID
     *
     * @return 事件监听器ID
     */
    int getListenerId() {
        return listenerId;
    }

    /**
     * 获取本次匹配器
     *
     * @return 匹配器
     */
    Matcher getMatcher() {
        return matcher;
    }

    /**
     * 获取本次监听事件类型数组
     *
     * @return 本次监听事件类型数组
     */
    Event.Type[] getEventTypeArray() {
        return eventTypeArray;
    }

    /**
     * 获取本次增强的影响统计
     *
     * @return 本次增强的影响统计
     */
    public AffectStatistic getAffectStatistic() {
        return affectStatistic;
    }

}
