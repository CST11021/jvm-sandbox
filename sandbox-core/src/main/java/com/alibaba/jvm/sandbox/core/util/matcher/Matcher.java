package com.alibaba.jvm.sandbox.core.util.matcher;

import com.alibaba.jvm.sandbox.core.util.matcher.structure.ClassStructure;

/**
 * 匹配器：可以判断当前类结构是否符合要求
 *
 * @author luanjia@taobao.com
 */
public interface Matcher {

    /**
     * 匹配类结构
     *
     * @param classStructure 类结构
     * @return 匹配结果
     */
    MatchingResult matching(ClassStructure classStructure);

}
