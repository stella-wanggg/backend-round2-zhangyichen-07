package com.example.config.yaml.detail;

import lombok.Data;

@Data
public class userPassword {
    private String algorithm;
    private Integer min_length;
    private String pattern;
}
