package org.acme.dto;

import java.util.Map;

public class OllamaRequest {
    public String model;
    public String prompt;
    public boolean stream = false;
    public Map<String, Object> options;
}
