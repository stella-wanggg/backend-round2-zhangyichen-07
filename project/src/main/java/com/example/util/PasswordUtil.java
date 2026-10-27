package com.example.util;

import com.example.config.PasswordConfig;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordUtil {
    private final PasswordEncoder encoder; // 插槽
    public PasswordUtil(PasswordEncoder encoder) {// 注入前面的那个DelegatePasswordEncoder,方便调度
        this.encoder = encoder; // 把东西插进去
    }
    // 之前提到的构造器注入，比之前用的@Autowired更推荐一些

    public String NewPassword(String password) {
        return encoder.encode(password);
    }

    public boolean CheckPassword(String password,String newPassword){ // 判是否相同,可输入自定义算法类型的密码
        if(password == null||newPassword == null){
            return false;
        }
        return encoder.matches(password,newPassword);
    }
}
