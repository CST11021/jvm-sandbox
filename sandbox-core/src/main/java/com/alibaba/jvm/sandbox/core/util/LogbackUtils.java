package com.alibaba.jvm.sandbox.core.util;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import static com.alibaba.jvm.sandbox.core.util.NamespaceConvert.initNamespaceConvert;

/**
 * Logback日志框架工具类
 */
public class LogbackUtils {


    /**
     * 初始化Logback日志框架
     *
     * @param namespace          命名空间
     * @param logbackCfgFilePath logback配置文件路径
     */
    public static void init(final String namespace, final String logbackCfgFilePath) {
        final LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        final JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(loggerContext);
        loggerContext.reset();

        final Logger logger = LoggerFactory.getLogger(LoggerFactory.class);
        // logback配置文件
        final File configureFile = new File(logbackCfgFilePath);
        InputStream is = null;
        try {
            is = new FileInputStream(configureFile);
            // 将Sandbox的命名空间注册到logback
            initNamespaceConvert(namespace);
            configurator.doConfigure(is);
            // 打印jvm-sandbox的logo
            logger.info(SandboxStringUtils.getLogo());
            logger.info("initializing logback success. file={};", configureFile);
        } catch (Throwable cause) {
            logger.warn("initialize logback failed. file={};", configureFile, cause);
        } finally {
            IOUtils.closeQuietly(is);
        }
    }

    /**
     * 销毁Logback日志框架
     */
    public static void destroy() {
        try {
            ((LoggerContext) LoggerFactory.getILoggerFactory()).stop();
        } catch (Throwable cause) {
            cause.printStackTrace();
        }
    }

}
