package com.example.config;

import com.example.config.yaml.detail.userPassword;
import com.example.config.yaml.useryaml;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.crypto.scrypt.SCryptPasswordEncoder;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


@Configuration
public class PasswordConfig {

    @Bean // 放进IOC容器，可以调用
    public PasswordEncoder passwordEncoder(useryaml users) {
        Map<String, PasswordEncoder> encoders = new HashMap<>(); // 储存所有可选择的密码编译器（encoder:

        encoders.put("bcrypt",new BCryptPasswordEncoder()); // 往map里塞编译器
        encoders.put("pbkdf2", Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8());
        encoders.put("scrypt", SCryptPasswordEncoder.defaultsForSpringSecurity_v5_8());

        String algorithm = users.getPassword().getAlgorithm().toLowerCase(Locale.ROOT);
        // 把用户输入的英文转为小写，和map的key匹配

        if(!encoders.containsKey(algorithm)){
            throw new IllegalArgumentException("不支持的密码算法");
        }
        // 判断一下用户选择的编译器在不在可选范围之内

        DelegatingPasswordEncoder delegatingPasswordEncoder = new DelegatingPasswordEncoder(algorithm,encoders);
        // delegate（动词）: 委托、委派、派活/把任务交给别人
        // 所以这句就是用来派发编译器，就相当于药店的工作人员，你要什么他帮你拿

        return delegatingPasswordEncoder;
        // 返回相应编译器
    }
}
