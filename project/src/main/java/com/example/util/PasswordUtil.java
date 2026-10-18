package com.example.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public class PasswordUtil {
    private static final PasswordEncoder encoder = new BCryptPasswordEncoder(); // 加密密码
    public static String NewPassword(String password){
        return encoder.encode(password);
    }
    public static boolean CheckPassword(String password,String hashPassword){ // 判是否相同
        if(password == null||hashPassword == null){
            return false;
        }
        return encoder.matches(password,hashPassword);
    }
}
