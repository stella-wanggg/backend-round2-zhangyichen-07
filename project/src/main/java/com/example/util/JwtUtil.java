package com.example.util;

import com.example.config.yaml.jwtYaml;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {
    @Autowired
    private jwtYaml jwt; // 调用映射

    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(jwt.getSecret().getBytes()); // 根据映射里的密钥字符串输出密钥对象
    }

    public String generateToken(Integer id,String name,String role){
        Date now = new Date(); // 获取现在的日期时间
        Date exp = new Date(now.getTime() + jwt.getExpiration()); // 获取令牌过期时间
        return Jwts.builder() // 生成空白令牌
                .setSubject(id.toString()) // subject是特殊的claim,存放唯一标识(用来认人),一般存id
                .claim("name",name) // 存放姓名和角色,可以直接调用,方便以后调用
                .claim("role",role)
                .claim("type","access") // 标明这是短期令牌
                .setIssuedAt(now) // 写上令牌颁发时间
                .setExpiration(exp) // 写上过期时间
                .signWith(getSecretKey(),SignatureAlgorithm.HS256) // 签名
                .compact(); // 把令牌字符串返回
    }

    public Claims parseToken(String token){
        return Jwts.parserBuilder() // 拿到解析器的建造工具
                .setSigningKey(getSecretKey()) // 输入配置:输入密钥(用来校验签名)
                .build() // 生成解析器
                .parseClaimsJws(token) // 进行解析(解析令牌的签名,判断是否过期...)
                .getBody(); // 取出claim里的信息
    }

    public boolean tokenNearExpiration(String token){ // 判是否快要过期
        Claims claims = parseToken(token);
        Date expiration = claims.getExpiration();
        Date now = new Date();
        return expiration.getTime()-now.getTime() < 5*60*1000L && expiration.getTime()-now.getTime() > 0;
    }
}
