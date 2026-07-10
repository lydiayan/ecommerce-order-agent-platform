package com.css.mallorderagent.graph.context;

import com.example.mallordermemory.profile.UserProfile;
import com.example.mallordermemory.service.MemoryExtractor;
import org.springframework.ai.document.Document;

import java.util.List;

public class AgentContext {

   // private UserRequest request;

    private UserProfile profile;

    private List<MemoryExtractor.ExtractedMemory> facts;

    private List<MemoryExtractor.ExtractedMemory> summaries;

    private List<Document> ragDocuments;

    //private List<ToolCallResult> toolResults;

    private String prompt;

    private String answer;

}