package com.css.mallorderagent.planner;

@FunctionalInterface
public interface IntentClassifier {

    /**
     * 对规则无法确定的用户问题执行受限意图分类。
     *
     * @param query 用户问题
     * @return 意图、置信度、澄清要求和原因码
     */
    IntentModelDecision classify(String query);
}
