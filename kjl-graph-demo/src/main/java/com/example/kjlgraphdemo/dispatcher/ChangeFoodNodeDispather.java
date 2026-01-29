package com.example.kjlgraphdemo.dispatcher;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.example.kjlgraphdemo.node.ChangeFoodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ChangeFoodNodeDispather implements EdgeAction {

    private static final Logger logger = LoggerFactory.getLogger(ChangeFoodNodeDispather.class);

    public ChangeFoodNodeDispather()
    {

    }
    @Override
    public String apply(OverAllState state) throws Exception {
        Map<String, Object> data=state.data();
        logger.info("changeFoodNodeDispather===>{}",(String)data.get("nextNode"));
        return (String)data.get("nextNode");
    };
}
