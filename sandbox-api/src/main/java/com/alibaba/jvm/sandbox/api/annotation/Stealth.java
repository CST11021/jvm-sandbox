package com.alibaba.jvm.sandbox.api.annotation;

import java.lang.annotation.*;

/**
 * 隐形屏障
 * <ul>
 * <li><s>被标注的类及其子类将不会被Sandbox所感知</s></li>
 * <li>被标注的ClassLoader所加载的类都不会被Sandbox所感知</li>
 * </ul>
 *
 * @author luanjia@taobao.com
 * @since {@code sandbox-api:1.0.10}
 * @since {@code sandbox-api:1.4.0} 隐形屏障标注在单独的类上不再生效
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Stealth {

}
