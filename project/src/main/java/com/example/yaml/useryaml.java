package com.example.yaml;

import com.example.yaml.detail.userName;
import com.example.yaml.detail.userPassword;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
@Data
@Component
@ConfigurationProperties(prefix = "users")
public class useryaml {
    private String path;
    private userName username;
    private userPassword password;
}

// yaml配置映射
