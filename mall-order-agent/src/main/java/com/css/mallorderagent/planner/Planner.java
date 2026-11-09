package com.css.mallorderagent.planner;

public interface Planner {

    /**
     * 将用户问题分类为受支持意图并生成有序动作计划。
     *
     * @param question 用户原始问题
     * @return 包含策略、动作、分类元数据和人工确认要求的计划
     */
    PlanResult plan(String question);
}
