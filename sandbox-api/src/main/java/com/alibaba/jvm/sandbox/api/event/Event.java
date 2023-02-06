package com.alibaba.jvm.sandbox.api.event;

/**
 * 调用事件
 * JVM方法调用事件
 *
 * @author luanjia@taobao.com
 */
public abstract class Event {

    /**
     * 事件类型
     */
    public final Type type;

    /**
     * 构造调用事件
     *
     * @param type 事件类型
     */
    protected Event(Type type) {
        this.type = type;
    }

    /**
     * 事件枚举类型：
     * BEFORE事件：执行方法体之前被调用
     * RETURN事件：执行方法体返回之前被调用
     * THROWS事件：执行方法体抛出异常之前被调用
     * LINE事件：方法行被执行后调用，目前仅记录行号
     * CALL_BEFORE事件：一个方法被调用之前
     * CALL_RETURN事件：一个方法被调用正常返回之后
     * CALL_THROWS事件：一个方法被调用抛出异常之后
     * IMMEDIATELY_RETURN：立即调用:RETURN事件
     * IMMEDIATELY_THROWS：立即调用:THROWS事件
     *
     * 注：
     * 1、CALL事件系列是从GREYS中衍生过来的事件，它描述了一个方法内部，调用其他方法的过程。整个过程可以被描述成为三个阶段
     * 2、严格意义上，IMMEDIATELY_RETURN和IMMEDIATELY_THROWS不是事件，他们是流程控制机制，由
     * com.alibaba.jvm.sandbox.api.ProcessControlException的throwReturnImmediately(Object)和throwThrowsImmediately(Throwable)触发，完成对方法的流程控制
     */
    public enum Type {

        /** 执行方法体之前被调用 */
        BEFORE,
        /** 执行方法体返回之前被调用 */
        RETURN,
        /** 执行方法体抛出异常之前被调用 */
        THROWS,
        /** 方法行被执行后调用，目前仅记录行号 */
        LINE,

        //
        // CALL事件系列是从GREYS中衍生过来的事件，它描述了一个方法内部，调用其他方法的过程。整个过程可以被描述成为三个阶段
        //
        // void test() {
        //     # CALL_BEFORE
        //     try {
        //         logger.info("TEST");
        //         # CALL_RETURN
        //     } catch(Throwable cause) {
        //         # CALL_THROWS
        //     }
        // }
        //

        /** 一个方法被调用之前 */
        CALL_BEFORE,
        /** 一个方法被调用正常返回之后 */
        CALL_RETURN,
        /** 一个方法被调用抛出异常之后 */
        CALL_THROWS,

        /**
         * 立即调用:RETURN
         * 由{@link com.alibaba.jvm.sandbox.api.ProcessControlException#throwReturnImmediately(Object)}触发
         */
        IMMEDIATELY_RETURN,
        /**
         * 立即调用:THROWS
         * 由{@link com.alibaba.jvm.sandbox.api.ProcessControlException#throwThrowsImmediately(Throwable)}触发
         */
        IMMEDIATELY_THROWS;

        /**
         * 空类型
         *
         * @since {@code sandbox-api:1.3.0}
         */
        public static final Event.Type[] EMPTY = new Event.Type[0];

    }

}
