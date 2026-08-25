package com.css.mallorderagent.planner;

@FunctionalInterface
public interface IntentClassifier {

    IntentModelDecision classify(String query);
}
