package org.acme.dto;

public class OllamaResponse {
    public String model;
    public String created_at;
    public String response;
    public boolean done;
    public Long total_duration;
    public Long prompt_eval_count;
    public Long eval_count;
}
