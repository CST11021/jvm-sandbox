package com.alibaba.jvm.sandbox.core.enhance.weaver.asm;

import com.alibaba.jvm.sandbox.api.event.Event;
import com.alibaba.jvm.sandbox.core.enhance.weaver.CodeLock;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.JSRInlinerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.com.alibaba.jvm.sandbox.spy.Spy;
import java.util.ArrayList;
import java.util.Set;

import static com.alibaba.jvm.sandbox.core.util.SandboxStringUtils.toJavaClassName;
import static org.apache.commons.lang3.ArrayUtils.contains;
import static org.apache.commons.lang3.StringUtils.join;

/**
 * 用于Call的代码锁
 */
class CallAsmCodeLock extends AsmCodeLock {

    CallAsmCodeLock(AdviceAdapter aa) {
        super(
                aa,
                new int[]{
                        ICONST_2, POP
                },
                new int[]{
                        ICONST_3, POP
                }
        );
    }
}

/**
 * TryCatch块,用于ExceptionsTable重排序
 */
class AsmTryCatchBlock {

    final Label start;
    final Label end;
    final Label handler;
    final String type;

    AsmTryCatchBlock(Label start, Label end, Label handler, String type) {
        this.start = start;
        this.end = end;
        this.handler = handler;
        this.type = type;
    }

}

/**
 * 方法事件编织者
 * Created by luanjia@taobao.com on 16/7/16.
 */
public class EventWeaver extends ClassVisitor implements Opcodes, AsmTypes, AsmMethods {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    /** 目标类加载器对象的ID(JVM唯一) */
    private final int targetClassLoaderObjectID;
    /** 命名空间 */
    private final String namespace;
    /** 监听器ID */
    private final int listenerId;
    /** 目标类的类名，例如：java.lang.String */
    private final String targetJavaClassName;
    /** 需要被增强的行为签名：这些签名就是我们要关注的切点 */
    private final Set<String> signCodes;
    /** 监听的事件类型 */
    private final Event.Type[] eventTypeArray;

    /** 是否支持LINE_EVENT：LINE_EVENT需要对Class做特殊的增强，所以需要在这里做特殊的判断 */
    private final boolean isLineEnable;
    /** 是否支持CALL_BEFORE/CALL_RETURN/CALL_THROWS事件: CALL系列事件需要对Class做特殊的增强，所以需要在这里做特殊的判断 */
    private final boolean isCallEnable;
    private final boolean hasCallThrows;
    private final boolean hasCallBefore;
    private final boolean hasCallReturn;


    /**
     * 通过 EventEnhancer#toByteCodeArray() 方法将事件编织器植入目标，这样类行为方法调用的时候，就可以通过this.visitMethod()进行回调了
     *
     * @param api                       asm版本ID
     * @param cv                        类访问器
     * @param namespace                 埋点字段：命名空间
     * @param listenerId                埋点字段：监听ID
     * @param targetClassLoaderObjectID 目标类加载器对象的ID(JVM唯一)
     * @param targetClassInternalName   目标类的类名
     * @param signCodes                 需要被增强的行为签名：这些签名就是我们要关注的切点
     * @param eventTypeArray            监听的事件类型
     */
    public EventWeaver(final int api,
                       final ClassVisitor cv,
                       final String namespace,
                       final int listenerId,
                       final int targetClassLoaderObjectID,
                       final String targetClassInternalName,
                       final Set<String/*BehaviorStructure#getSignCode()*/> signCodes,
                       final Event.Type[] eventTypeArray) {
        super(api, cv);
        this.targetClassLoaderObjectID = targetClassLoaderObjectID;
        this.namespace = namespace;
        this.listenerId = listenerId;
        this.targetJavaClassName = toJavaClassName(targetClassInternalName);
        this.signCodes = signCodes;
        this.eventTypeArray = eventTypeArray;

        this.isLineEnable = contains(eventTypeArray, Event.Type.LINE);
        this.hasCallBefore = contains(eventTypeArray, Event.Type.CALL_BEFORE);
        this.hasCallReturn = contains(eventTypeArray, Event.Type.CALL_RETURN);
        this.hasCallThrows = contains(eventTypeArray, Event.Type.CALL_THROWS);
        this.isCallEnable = hasCallBefore || hasCallReturn || hasCallThrows;
    }

    /**
     * 通过 EventEnhancer#toByteCodeArray() 方法将事件编织器植入目标，这样类行为方法调用的时候，就可以通过该进行回调了
     *
     * @param access
     * @param name
     * @param desc
     * @param signature
     * @param exceptions
     * @return
     */
    @Override
    public MethodVisitor visitMethod(final int access, final String name, final String desc, final String signature, final String[] exceptions) {

        final MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        final String signCode = getBehaviorSignCode(name, desc);

        // 判断是不是我们关注的切点签名
        if (!isMatchedBehavior(signCode)) {
            logger.debug("non-rewrite method {} for listener[id={}];", signCode, listenerId);
            return mv;
        }

        logger.info("rewrite method {} for listener[id={}];event={};", signCode, listenerId, join(eventTypeArray, ","));
        MethodVisitor methodVisitor = new JSRInlinerAdapter(mv, access, name, desc, signature, exceptions);
        return new ReWriteMethod(api, methodVisitor, access, name, desc) {

            private final Label beginLabel = new Label();
            private final Label endLabel = new Label();
            private final Label startCatchBlock = new Label();
            private final Label endCatchBlock = new Label();
            private int newlocal = -1;

            /**
             * 用来标记一个方法是否已经进入
             * JVM中的构造函数非常特殊，super();this();是在构造函数方法体执行之外进行，如果在这个之前进行了任何的流程改变操作
             * 将会被JVM加载类的时候判定校验失败，导致类加载出错
             * 所以这里需要用一个标记为告知后续的代码编织，绕开super()和this()
             */
            private boolean isMethodEnter = false;

            /** 代码锁 */
            private final CodeLock codeLockForTracing = new CallAsmCodeLock(this);

            /** 用于tracing的当前行号 */
            private int tracingCurrentLineNumber = -1;

            /** 用于try-catch的重排序：目的是让call的try...catch能在exceptions tables排在前边 */
            private final ArrayList<AsmTryCatchBlock> asmTryCatchBlocks = new ArrayList<AsmTryCatchBlock>();


            /**
             * 触发 {@link Spy#spyMethodOnBefore(Object[], String, int, int, String, String, String, Object)}
             * param argumentArray                 调用目标方法的入参
             * param namespace                     命名空间
             * param listenerId                    要触发的监听ID
             * param targetClassLoaderObjectID     目标类加载器的对象ID（JVM唯一）
             * param javaClassName                 目标类的类名
             * param javaMethodName                目标类的方法名
             * param javaMethodDesc                触发的方法签名
             * param target                        目标类对象
             */
            @Override
            protected void onMethodEnter() {
                codeLockForTracing.lock(new CodeLock.Block() {

                    @Override
                    public void code() {
                        mark(beginLabel);
                        // 设置调用目标方法的入参
                        loadArgArray();
                        dup();
                        // 设置埋点字段：命名空间
                        push(namespace);
                        // 设置埋点字段：监听器ID
                        push(listenerId);
                        // 设置埋点字段：目标类加载器的ID
                        loadClassLoader();
                        // 设置埋点字段：目标类的方法名
                        push(targetJavaClassName);
                        // 设置埋点字段：目标类的类名
                        push(name);
                        // 设置埋点字段：目标类的方法签名
                        push(desc);
                        // 设置埋点字段：目标类实例
                        loadThisOrPushNullIfIsStatic();
                        // 设置调用间谍类的静态方法
                        invokeStatic(ASM_TYPE_SPY, ASM_METHOD_Spy$spyMethodOnBefore);
                        swap();
                        storeArgArray();
                        pop();
                        processControl();
                        isMethodEnter = true;
                    }

                });
            }

            /**
             * 执行方法体返回之前被调用
             *
             * @param opcode
             */
            @Override
            protected void onMethodExit(final int opcode) {
                if (!isThrow(opcode)) {
                    codeLockForTracing.lock(new CodeLock.Block() {
                        @Override
                        public void code() {
                            // 设置方法调用的返回值
                            loadReturn(opcode);
                            // 设置命名空间
                            push(namespace);
                            // 设置监听器ID
                            push(listenerId);
                            // 调用间谍类的静态方法
                            invokeStatic(ASM_TYPE_SPY, ASM_METHOD_Spy$spyMethodOnReturn);
                            processControl();
                        }
                    });
                }
            }

            @Override
            public void visitMaxs(int maxStack, int maxLocals) {
                mark(endLabel);
                mv.visitLabel(startCatchBlock);
                visitTryCatchBlock(beginLabel, endLabel, startCatchBlock, ASM_TYPE_THROWABLE.getInternalName());

                codeLockForTracing.lock(new CodeLock.Block() {
                    @Override
                    public void code() {
                        newlocal = newLocal(ASM_TYPE_THROWABLE);
                        storeLocal(newlocal);
                        loadLocal(newlocal);
                        push(namespace);
                        push(listenerId);
                        invokeStatic(ASM_TYPE_SPY, ASM_METHOD_Spy$spyMethodOnThrows);
                        processControl();
                        loadLocal(newlocal);
                    }
                });

                throwException();
                mv.visitLabel(endCatchBlock);
                super.visitMaxs(maxStack, maxLocals);
            }

            @Override
            public void visitLineNumber(final int lineNumber, Label label) {
                if (isMethodEnter && isLineEnable) {
                    codeLockForTracing.lock(new CodeLock.Block() {
                        @Override
                        public void code() {
                            // 设置代码的行号
                            push(lineNumber);
                            // 设置命名空间
                            push(namespace);
                            // 设置监听器ID
                            push(listenerId);
                            // 调用间谍类的静态方法
                            invokeStatic(ASM_TYPE_SPY, ASM_METHOD_Spy$spyMethodOnLine);
                        }
                    });
                }
                super.visitLineNumber(lineNumber, label);
                this.tracingCurrentLineNumber = lineNumber;
            }

            @Override
            public void visitInsn(int opcode) {
                super.visitInsn(opcode);
                codeLockForTracing.code(opcode);
            }

            @Override
            public void visitMethodInsn(final int opcode, final String owner, final String name, final String desc, final boolean itf) {

                // 如果CALL事件没有启用，则不需要对CALL进行增强
                // 如果正在CALL的方法来自于SANDBOX本身，则不需要进行追踪
                if (!isMethodEnter || !isCallEnable || codeLockForTracing.isLock()) {
                    super.visitMethodInsn(opcode, owner, name, desc, itf);
                    return;
                }

                if (hasCallBefore) {
                    // 方法调用前通知
                    codeLockForTracing.lock(new CodeLock.Block() {
                        @Override
                        public void code() {
                            push(tracingCurrentLineNumber);
                            push(toJavaClassName(owner));
                            push(name);
                            push(desc);
                            push(namespace);
                            push(listenerId);
                            invokeStatic(ASM_TYPE_SPY, ASM_METHOD_Spy$spyMethodOnCallBefore);
                        }
                    });
                }

                // 如果没有CALL_THROWS事件,其实是可以不用对方法调用进行try...catch
                // 这样可以节省大量的字节码
                if (!hasCallThrows) {
                    super.visitMethodInsn(opcode, owner, name, desc, itf);
                    codeLockForTracing.lock(new CodeLock.Block() {
                        @Override
                        public void code() {
                            push(namespace);
                            push(listenerId);
                            invokeStatic(ASM_TYPE_SPY, ASM_METHOD_Spy$spyMethodOnCallReturn);
                        }
                    });
                    return;
                }


                // 这里是需要处理拥有CALL_THROWS事件的场景
                final Label tracingBeginLabel = new Label();
                final Label tracingEndLabel = new Label();
                final Label tracingFinallyLabel = new Label();



                mark(tracingBeginLabel);
                super.visitMethodInsn(opcode, owner, name, desc, itf);
                mark(tracingEndLabel);

                if (hasCallReturn) {
                    // 方法调用后通知
                    codeLockForTracing.lock(new CodeLock.Block() {
                        @Override
                        public void code() {
                            push(namespace);
                            push(listenerId);
                            invokeStatic(ASM_TYPE_SPY, ASM_METHOD_Spy$spyMethodOnCallReturn);
                        }
                    });
                }
                goTo(tracingFinallyLabel);

                catchException(tracingBeginLabel, tracingEndLabel, ASM_TYPE_THROWABLE);
                codeLockForTracing.lock(new CodeLock.Block() {
                    @Override
                    public void code() {
                        dup();
                        invokeVirtual(ASM_TYPE_OBJECT, ASM_METHOD_Object$getClass);
                        invokeVirtual(ASM_TYPE_CLASS, ASM_METHOD_Class$getName);
                        push(namespace);
                        push(listenerId);
                        invokeStatic(ASM_TYPE_SPY, ASM_METHOD_Spy$spyMethodOnCallThrows);
                    }
                });

                throwException();

                mark(tracingFinallyLabel);

            }

            @Override
            public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
                asmTryCatchBlocks.add(new AsmTryCatchBlock(start, end, handler, type));
            }

            @Override
            public void visitEnd() {
                for (AsmTryCatchBlock tcb : asmTryCatchBlocks) {
                    super.visitTryCatchBlock(tcb.start, tcb.end, tcb.handler, tcb.type);
                }
                super.visitLocalVariable("t",ASM_TYPE_THROWABLE.getDescriptor(),null,startCatchBlock,endCatchBlock,newlocal);
                super.visitEnd();
            }



            /**
             * 流程控制
             */
            private void processControl() {
                final Label finishLabel = new Label();
                final Label returnLabel = new Label();
                final Label throwsLabel = new Label();
                dup();
                visitFieldInsn(GETFIELD, ASM_TYPE_SPY_RET, "state", ASM_TYPE_INT);
                dup();
                push(Spy.Ret.RET_STATE_RETURN);
                ifICmp(EQ, returnLabel);
                push(Spy.Ret.RET_STATE_THROWS);
                ifICmp(EQ, throwsLabel);
                goTo(finishLabel);
                mark(returnLabel);
                pop();
                visitFieldInsn(GETFIELD, ASM_TYPE_SPY_RET, "respond", ASM_TYPE_OBJECT);
                checkCastReturn(Type.getReturnType(desc));
                goTo(finishLabel);
                mark(throwsLabel);
                visitFieldInsn(GETFIELD, ASM_TYPE_SPY_RET, "respond", ASM_TYPE_OBJECT);
                checkCast(ASM_TYPE_THROWABLE);
                throwException();
                mark(finishLabel);
                pop();
            }

            /**
             * 加载ClassLoader
             */
            private void loadClassLoader() {
                push(targetClassLoaderObjectID);
            }

            /**
             * 是否抛出异常返回(通过字节码判断)
             *
             * @param opcode 操作码
             * @return true:以抛异常形式返回 / false:非抛异常形式返回(return)
             */
            private boolean isThrow(int opcode) {
                return opcode == ATHROW;
            }

            /**
             * 加载返回值
             * @param opcode 操作吗
             */
            private void loadReturn(int opcode) {
                switch (opcode) {

                    case RETURN: {
                        pushNull();
                        break;
                    }

                    case ARETURN: {
                        dup();
                        break;
                    }

                    case LRETURN:
                    case DRETURN: {
                        dup2();
                        box(Type.getReturnType(methodDesc));
                        break;
                    }

                    default: {
                        dup();
                        box(Type.getReturnType(methodDesc));
                        break;
                    }

                }
            }

        };
    }

    private boolean isMatchedBehavior(final String signCode) {
        return signCodes.contains(signCode);
    }

    private String getBehaviorSignCode(final String name, final String desc) {
        final StringBuilder sb = new StringBuilder(256).append(targetJavaClassName).append("#").append(name).append("(");

        final Type[] methodTypes = Type.getMethodType(desc).getArgumentTypes();
        if (methodTypes.length != 0) {
            sb.append(methodTypes[0].getClassName());
            for (int i = 1; i < methodTypes.length; i++) {
                sb.append(",").append(methodTypes[i].getClassName());
            }
        }

        return sb.append(")").toString();
    }


}