package com.alibaba.jvm.sandbox.core.enhance;

import com.alibaba.jvm.sandbox.api.event.Event;
import com.alibaba.jvm.sandbox.core.enhance.weaver.asm.EventWeaver;
import com.alibaba.jvm.sandbox.core.util.AsmUtils;
import com.alibaba.jvm.sandbox.core.util.ObjectIDs;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import static org.apache.commons.io.FileUtils.writeByteArrayToFile;
import static org.objectweb.asm.ClassReader.EXPAND_FRAMES;
import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.ClassWriter.COMPUTE_MAXS;
import static org.objectweb.asm.Opcodes.ASM7;

/**
 * 事件代码增强器
 *
 * @author luanjia@taobao.com
 */
public class EventEnhancer implements Enhancer {

    private static final Logger logger = LoggerFactory.getLogger(EventEnhancer.class);

    /**
     * 创建ClassWriter for asm
     *
     * @param cr ClassReader
     * @return ClassWriter
     */
    private ClassWriter createClassWriter(final ClassLoader targetClassLoader, final ClassReader cr) {
        return new ClassWriter(cr, COMPUTE_FRAMES | COMPUTE_MAXS) {

            /**
             * 注意，为了自动计算帧的大小，有时必须计算两个类共同的父类。
             * 缺省情况下，ClassWriter将会在getCommonSuperClass方法中计算这些，通过在加载这两个类进入虚拟机时，使用反射API来计算。
             * 但是，如果你将要生成的几个类相互之间引用，这将会带来问题，因为引用的类可能还不存在。
             * 在这种情况下，你可以重写getCommonSuperClass方法来解决这个问题。
             *
             * 通过重写 getCommonSuperClass() 方法，更正获取ClassLoader的方式，改成使用指定ClassLoader的方式进行。
             * 规避了原有代码采用Object.class.getClassLoader()的方式
             */
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                return AsmUtils.getCommonSuperClass(type1, type2, targetClassLoader);
            }

        };
    }

    private static final boolean isDumpClass = false;

    /**
     * 转换为增强后的字节码数组
     *
     * @param targetClassLoader 目标类加载器
     * @param byteCodeArray     源字节码数组
     * @param signCodes         需要被增强的行为签名
     * @param namespace         命名空间
     * @param listenerId        需要埋入的监听器ID
     * @param eventTypeArray    需要配埋入的事件类型
     * @return 增强后的字节码数组
     */
    @Override
    public byte[] toByteCodeArray(final ClassLoader targetClassLoader,
                                  final byte[] byteCodeArray,
                                  final Set<String> signCodes,
                                  final String namespace,
                                  final int listenerId,
                                  final Event.Type[] eventTypeArray) {
        // 返回增强后字节码
        final ClassReader cr = new ClassReader(byteCodeArray);
        final ClassWriter cw = createClassWriter(targetClassLoader, cr);
        final int targetClassLoaderObjectID = ObjectIDs.instance.identity(targetClassLoader);
        cr.accept(
                // EventWeaver继承了 ClassVisitor，通过字节码植入埋点，这样当方法调用的时候，就通过 EventWeaver.visitMethod()方法来实现监听回调
                new EventWeaver(
                        ASM7,
                        cw,
                        namespace,
                        listenerId,
                        targetClassLoaderObjectID,
                        cr.getClassName(),
                        signCodes,
                        eventTypeArray
                ),
                EXPAND_FRAMES
        );
        return dumpClassIfNecessary(cr.getClassName(), cw.toByteArray());
    }

    /**
     * 将字节码写入className对应的
     * dump class to file
     * 用于代码调试
     * @param className
     * @param data
     * @return
     */
    private static byte[] dumpClassIfNecessary(String className, byte[] data) {
        if (!isDumpClass) {
            return data;
        }
        final File dumpClassFile = new File("./sandbox-class-dump/" + className + ".class");
        final File classPath = new File(dumpClassFile.getParent());

        // 创建类所在的包路径
        if (!classPath.mkdirs() && !classPath.exists()) {
            logger.warn("create dump classpath={} failed.", classPath);
            return data;
        }

        // 将类字节码写入文件
        try {
            writeByteArrayToFile(dumpClassFile, data);
            logger.info("dump {} to {} success.", className, dumpClassFile);
        } catch (IOException e) {
            logger.warn("dump {} to {} failed.", className, dumpClassFile, e);
        }

        return data;
    }

}
