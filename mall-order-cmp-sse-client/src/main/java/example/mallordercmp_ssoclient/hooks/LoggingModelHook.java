package example.mallordercmp_ssoclient.hooks;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class LoggingModelHook extends ModelHook {
    @Override
    public String getName() {
        return "logging_model_hook";
    }

    @Override
    public HookPosition[] getHookPositions() {
        return new HookPosition[] {HookPosition.BEFORE_MODEL, HookPosition.AFTER_MODEL};
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
        System.out.println("Before model call");
        return CompletableFuture.completedFuture(Map.of());
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterModel(OverAllState state, RunnableConfig config) {
        System.out.println("After model call");
        return CompletableFuture.completedFuture(Map.of());
    }
}
