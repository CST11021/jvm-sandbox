package com.alibaba.jvm.sandbox.core.util;

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Sandbox的命名空间注册到logback
 */
public class NamespaceConvert extends ClassicConverter {

    private static volatile String namespace;

    @Override
    public String convert(ILoggingEvent event) {
        return null == namespace ? "NULL" : namespace;
    }

    /**
     * 注册命名空间到Logback
     *
     * @param namespace 命名空间
     */
    public static void initNamespaceConvert(final String namespace) {
        NamespaceConvert.namespace = namespace;
        // 添加变量，这样可以在logback.xml配置文件中，通过如下配置，将变量输出到日志
        // <pattern>%d{yyyy-MM-dd HH:mm:ss} %SANDBOX_NAMESPACE %-5level %msg%n</pattern>
        PatternLayout.defaultConverterMap.put("SANDBOX_NAMESPACE", NamespaceConvert.class.getName());
    }

}
