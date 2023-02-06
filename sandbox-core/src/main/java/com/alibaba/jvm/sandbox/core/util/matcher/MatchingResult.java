package com.alibaba.jvm.sandbox.core.util.matcher;

import com.alibaba.jvm.sandbox.core.util.matcher.structure.BehaviorStructure;

import java.util.LinkedHashSet;

/**
 * 匹配结果
 *
 * @author luanjia@taobao.com
 */
public class MatchingResult {

    /** 匹配的行为结构：类的构造方法、普通方法、静态方法，统一称呼为类的行为结构 */
    private final LinkedHashSet<BehaviorStructure> behaviorStructures = new LinkedHashSet<BehaviorStructure>();

    /**
     * 是否匹配成功：有匹配的行为结构，说明匹配成功了
     *
     * @return TRUE:匹配成功;FALSE:匹配失败;
     */
    public boolean isMatched() {
        return !behaviorStructures.isEmpty();
    }

    /**
     * 获取匹配上的行为列表
     *
     * @return 匹配上的行为列表
     */
    public LinkedHashSet<BehaviorStructure> getBehaviorStructures() {
        return behaviorStructures;
    }

    /**
     * 获取匹配上的行为签名列表
     *
     * @return 行为签名列表
     */
    public LinkedHashSet<String> getBehaviorSignCodes() {
        final LinkedHashSet<String> behaviorSignCodes = new LinkedHashSet<String>();
        for (BehaviorStructure behaviorStructure : behaviorStructures) {
            behaviorSignCodes.add(behaviorStructure.getSignCode());
        }
        return behaviorSignCodes;
    }

}
