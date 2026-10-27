# 方案二：Refresh Token 双令牌（完整刷新 / 免登录）

> 如果还没弄懂“access token / refresh token / 轮换 / 免登录”这几个词，先看同目录的 `00-先弄懂令牌刷新.md`。这篇默认你已经知道两个 token 的分工。

## 1. 为什么还要再加一层

方案一的滑动续期只能处理“**快过期但还没过期**”的 token。如果用户关掉页面两个小时，access token 已经过期，拦截器直接 401，用户只能重新登录。

Refresh Token 的思路是：

- **access token**：短有效期，用来访问业务接口；
- **refresh token**：长有效期，只用来换新的 access token；
- refresh token 存数据库（只存哈希），可以撤销、可以轮换；
- access token 过期后，前端拿 refresh token 调 `/refresh`，换一对新的。

这样就能做到：

- 活跃用户：滑动续期，完全无感；
- 隔了几天再打开：refresh token 还有效，自动换新，不用重新输密码；
- 退出登录：撤销 refresh token，之后不能再刷新。

## 2. 整体流程

```text
登录 POST /login
  → 校验用户名 + 密码
  → 生成 accessToken（15 分钟）
  → 生成 refreshToken（7 天，随机字符串）
  → 数据库只存 refreshToken 的 SHA-256
  → 返回 { accessToken, refreshToken, expiresIn }

普通请求
  Header: token: <accessToken>
  → 拦截器验签、验过期
  → 快过期：响应头 new-token 给新的 accessToken（保留方案一）
  → 已过期：HTTP 401 + body code 40101

刷新 POST /refresh
  Body: { "refreshToken": "..." }
  → 查库、校验未撤销、未过期
  → 旧 refreshToken 作废，生成新的
  → 生成新的 accessToken
  → 返回新的一对

退出 POST /logout
  Body: { "refreshToken": "..." }
  → 把 refreshToken 标记为 revoked
```

## 3. 对照现在的代码：要改哪些地方

| 文件 | 操作 | 原因 |
|---|---|---|
| `resources/application.yaml` | 修改 | 加 `refresh-expiration`，测试时缩短 access 有效期 |
| `config/yaml/jwtYaml.java` | 修改 | 加 `refreshExpiration` 字段 |
| `util/JwtUtil.java` | 修改 | 密钥用 UTF-8；加 `getAccessTokenTtlSeconds()` |
| `pojo/RefreshToken.java` | 新增 | 对应 `refresh_token` 表 |
| `mapper/RefreshTokenMapper.java` | 新增 | refresh_token 的增查改 |
| `service/RefreshTokenService.java` | 新增 | 生成、校验、轮换、撤销 |
| `pojo/LoginResponse.java` | 新增 | 登录 / 刷新的返回体 |
| `pojo/RefreshRequest.java` | 新增 | `/refresh`、`/logout` 的请求体 |
| `exception/TokenException.java` | 新增 | 刷新失败返回 401 |
| `service/UserService.java` | 修改 | 加 `getUserByIdForRefresh` |
| `service/impl/UserServiceImpl.java` | 修改 | 实现一个不做权限判断的查询方法 |
| `controller/UserController.java` | 修改 | 登录返回两个 token，新增 `/refresh`、`/logout` |
| `exception/GlobalExceptionHandler.java` | 修改 | `TokenException` → 401 |
| `interception/Interceptor.java` | 修改 | 过期单独返回 40101；保留滑动续期 |
| `config/WebConfig.java` | 修改 | 放行 `/refresh`、`/logout`、`/error` |
| 数据库 | 新增表 | `refresh_token` |

### 3.1 这里有个坑：`/refresh` 不能直接调用现在的 `getUserById`

现在的 `UserServiceImpl.getUserById` 是这样：

```java
public User getUserById(Integer id, HttpServletRequest request) {
    Integer selfId = (Integer) request.getAttribute("id");
    ...
    if (!testRole(request)) {
        ...
    }
}
```

这个方法有两个前提：

1. 拦截器已经把 `id`、`role` 放进了 request；
2. 它是给业务接口用的，会做“普通用户只能查自己”的权限判断。

但是 `/refresh` 会在 `WebConfig` 里被放行，拦截器根本不执行，request 里没有 `id`/`role`。如果直接调用：

- `selfId` 是 `null`；
- `testRole` 里 `request.getAttribute("role").toString()` 会空指针。

所以不要硬套。给 Service 加一个只给刷新用的查询方法：

```java
User getUserByIdForRefresh(Integer id);
```

它只查数据库，不做 request 权限判断。调用它的前提是：**RefreshTokenService 已经验证过 refresh token 有效**。

## 4. 完整代码

### 4.1 `application.yaml`

只改 `jwt` 这一段：

```yaml
jwt:
  secret: ${JWT_SECRET:zhangyichen0205abcdefghijklmnopqrstuywxz}
  expiration: 900000            # access token：15 分钟
  refresh-expiration: 604800000 # refresh token：7 天
```

说明：

- `expiration` 从现在的 2 小时改成 15 分钟，refresh token 才有意义；
- 测试时可以把 `expiration` 临时改成 `60000`（1 分钟），不然等 15 分钟太慢；
- `secret` 至少 32 字节；提交 GitHub 前用环境变量 `JWT_SECRET` 覆盖，不要把真实密钥提交上去。

### 4.2 `config/yaml/jwtYaml.java`

```java
package com.example.config.yaml;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class jwtYaml {
    private String secret;
    private Long expiration;          // access token 有效期，毫秒
    private Long refreshExpiration;   // refresh token 有效期，毫秒
}
```

### 4.3 `util/JwtUtil.java`

```java
package com.example.util;

import com.example.config.yaml.jwtYaml;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    @Autowired
    private jwtYaml jwt;

    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(
                jwt.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(Integer id, String name, String role) {
        Date now = new Date();
        Date exp = new Date(now.getTime() + jwt.getExpiration());

        return Jwts.builder()
                .setSubject(id.toString())
                .claim("name", name)
                .claim("role", role)
                .claim("type", "access")
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(getSecretKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSecretKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public boolean tokenNearExpiration(String token) {
        Claims claims = parseToken(token);
        long remaining = claims.getExpiration().getTime()
                - System.currentTimeMillis();
        return remaining > 0 && remaining < 5 * 60 * 1000L;
    }

    /** 登录 / 刷新时告诉前端 access token 还能用多少秒 */
    public long getAccessTokenTtlSeconds() {
        return jwt.getExpiration() / 1000;
    }
}
```

### 4.4 `pojo/RefreshToken.java`（新增）

```java
package com.example.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {
    private Long id;
    private Integer userId;
    private String tokenHash;
    private LocalDateTime expiresAt;
    private Boolean revoked;
    private LocalDateTime createdAt;
}
```

### 4.5 `mapper/RefreshTokenMapper.java`（新增）

```java
package com.example.mapper;

import com.example.pojo.RefreshToken;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RefreshTokenMapper {

    @Insert("insert into refresh_token(user_id, token_hash, expires_at, revoked) " +
            "values (#{userId}, #{tokenHash}, #{expiresAt}, #{revoked})")
    void insert(RefreshToken token);

    @Select("select id, user_id as userId, token_hash as tokenHash, " +
            "expires_at as expiresAt, revoked, created_at as createdAt " +
            "from refresh_token where token_hash = #{tokenHash}")
    RefreshToken findByHash(@Param("tokenHash") String tokenHash);

    /**
     * 只有 revoked=0 才能更新成功。
     * 并发刷新时，只有一个请求能把它改成 1，另一个会得到 0。
     */
    @Update("update refresh_token set revoked = 1 " +
            "where id = #{id} and revoked = 0")
    int revokeById(@Param("id") Long id);

    /** 退出登录时按 hash 作废 */
    @Update("update refresh_token set revoked = 1 " +
            "where token_hash = #{tokenHash} and revoked = 0")
    int revokeByHash(@Param("tokenHash") String tokenHash);
}
```

### 4.6 `service/RefreshTokenService.java`（新增）

```java
package com.example.service;

import com.example.config.yaml.jwtYaml;
import com.example.exception.TokenException;
import com.example.mapper.RefreshTokenMapper;
import com.example.pojo.RefreshToken;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long DEFAULT_REFRESH_TTL_MILLIS =
            7L * 24 * 60 * 60 * 1000;

    @Autowired
    private RefreshTokenMapper refreshTokenMapper;

    @Autowired
    private jwtYaml jwt;

    /**
     * 生成 refresh token：
     * - 返回原文给客户端；
     * - 数据库只存 SHA-256，不存原文。
     */
    public String create(Integer userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);

        String rawToken = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);

        long ttlMillis = jwt.getRefreshExpiration() == null
                ? DEFAULT_REFRESH_TTL_MILLIS
                : jwt.getRefreshExpiration();

        RefreshToken entity = new RefreshToken();
        entity.setUserId(userId);
        entity.setTokenHash(sha256Hex(rawToken));
        entity.setExpiresAt(LocalDateTime.now()
                .plus(Duration.ofMillis(ttlMillis)));
        entity.setRevoked(false);

        refreshTokenMapper.insert(entity);
        return rawToken;
    }

    /** 校验客户端传来的 refresh token 原文 */
    public RefreshToken verify(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new TokenException("刷新凭证不能为空");
        }

        RefreshToken stored = refreshTokenMapper.findByHash(sha256Hex(rawToken));
        if (stored == null || Boolean.TRUE.equals(stored.getRevoked())) {
            throw new TokenException("刷新凭证无效");
        }
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new TokenException("刷新凭证已过期，请重新登录");
        }
        return stored;
    }

    /** 轮换：旧的作废，生成新的 */
    @Transactional
    public String rotate(RefreshToken old) {
        int updated = refreshTokenMapper.revokeById(old.getId());
        if (updated == 0) {
            throw new TokenException("刷新凭证已失效，请重新登录");
        }
        return create(old.getUserId());
    }

    /** 退出登录 / 主动作废 */
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenMapper.revokeByHash(sha256Hex(rawToken));
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
```

### 4.7 两个 DTO（新增）

`pojo/LoginResponse.java`：

```java
package com.example.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String tokenType;      // "Bearer"
    private String accessToken;
    private String refreshToken;
    private Long expiresIn;        // access token 剩余秒数
}
```

`pojo/RefreshRequest.java`：

```java
package com.example.pojo;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshRequest {

    @NotBlank(message = "refreshToken不能为空")
    private String refreshToken;
}
```

### 4.8 `exception/TokenException.java`（新增）

```java
package com.example.exception;

public class TokenException extends RuntimeException {

    public TokenException(String message) {
        super(message);
    }
}
```

### 4.9 `UserService` 和 `UserServiceImpl` 加一个刷新专用查询

`service/UserService.java` 里加：

```java
User getUserByIdForRefresh(Integer id);
```

`service/impl/UserServiceImpl.java` 里加：

```java
@Override
public User getUserByIdForRefresh(Integer id) {
    // 只给 /refresh 内部使用：
    // 调用之前 RefreshTokenService.verify() 已经验证过 refresh token，
    // 这里只按 id 查用户，不再做 request 里的权限判断。
    return userMapper.getUserById(id);
}
```

注意：**不要**图省事在 `/refresh` 里传一个空的 `HttpServletRequest`，也不要把现在的权限方法改成“查不到就放过”。

### 4.10 `controller/UserController.java`

```java
package com.example.controller;

import com.example.exception.TokenException;
import com.example.pojo.LoginResponse;
import com.example.pojo.RefreshRequest;
import com.example.pojo.RefreshToken;
import com.example.pojo.User;
import com.example.result.Result;
import com.example.service.RefreshTokenService;
import com.example.service.UserService;
import com.example.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @PostMapping("/register")
    public Result register(@Valid @RequestBody User user) {
        log.info("{} 成功注册", user.getUsername());
        userService.register(user);
        return Result.success();
    }

    @PostMapping("/login")
    public Result login(@Valid @RequestBody User user) {
        User u = userService.login(user);

        String accessToken = jwtUtil.generateToken(
                u.getId(), u.getUsername(), u.getRole());
        String refreshToken = refreshTokenService.create(u.getId());

        log.info("{} 成功登录", u.getUsername());

        return Result.success(new LoginResponse(
                "Bearer",
                accessToken,
                refreshToken,
                jwtUtil.getAccessTokenTtlSeconds()
        ));
    }

    @PostMapping("/refresh")
    public Result refresh(@Valid @RequestBody RefreshRequest request) {
        // 1. 校验 refresh token：存在、未撤销、未过期
        RefreshToken stored = refreshTokenService.verify(request.getRefreshToken());

        // 2. 按 id 查用户（注意：这里不能用带权限判断的 getUserById）
        User u = userService.getUserByIdForRefresh(stored.getUserId());
        if (u == null) {
            throw new TokenException("用户不存在");
        }

        // 3. 旧 refresh token 作废，生成新的
        String newRefreshToken = refreshTokenService.rotate(stored);

        // 4. 生成新的 access token
        String newAccessToken = jwtUtil.generateToken(
                u.getId(), u.getUsername(), u.getRole());

        return Result.success(new LoginResponse(
                "Bearer",
                newAccessToken,
                newRefreshToken,
                jwtUtil.getAccessTokenTtlSeconds()
        ));
    }

    @PostMapping("/logout")
    public Result logout(@Valid @RequestBody RefreshRequest request) {
        refreshTokenService.revoke(request.getRefreshToken());
        return Result.success();
    }

    @GetMapping("/showbyid/{id}")
    public Result showById(@PathVariable("id") Integer id,
                           HttpServletRequest request) {
        User user = userService.getUserById(id, request);
        return Result.success(user);
    }

    @GetMapping("/show")
    public Result show(HttpServletRequest request) {
        List<User> users = userService.showUsers(request);
        return Result.success(users);
    }

    @GetMapping("/showbypage")
    public Result showByPage(@RequestParam(defaultValue = "1") Integer page,
                             @RequestParam(defaultValue = "5") Integer pageSize,
                             HttpServletRequest request) {
        List<User> users = userService.pageShowUsers(page, pageSize, request);
        return Result.success(users);
    }

    @GetMapping("/showcount")
    public Result showCount(HttpServletRequest request) {
        Map<String, Integer> map = userService.pageShowUsersCount(request);
        return Result.success(map);
    }
}
```

### 4.11 `exception/GlobalExceptionHandler.java`

在类里加一个方法（`TokenException` 和这个类在同一个包，不需要 import）：

```java
@ExceptionHandler(TokenException.class)
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public Result handleTokenException(TokenException e) {
    log.warn("令牌异常:{}", e.getMessage());
    return Result.error(401, e.getMessage());
}
```

这样 `/refresh` 里 refresh token 无效或过期，会返回 HTTP 401，而不是被最后的 `Exception` 兜底成 500。

### 4.12 `interception/Interceptor.java`

这一版在方案一的基础上，多了“过期单独返回 40101”，并保留滑动续期：

```java
package com.example.interception;

import com.alibaba.fastjson.JSONObject;
import com.example.result.Result;
import com.example.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;

@Slf4j
@Component
public class Interceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        log.info("请求的url:{}", request.getRequestURL().toString());

        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String jwt = request.getHeader("token");
        if (!StringUtils.hasText(jwt)) {
            String authorization = request.getHeader("Authorization");
            if (StringUtils.hasText(authorization)
                    && authorization.startsWith("Bearer ")) {
                jwt = authorization.substring(7);
            }
        }

        if (!StringUtils.hasText(jwt)) {
            writeError(response, 401, "未登录,令牌不存在");
            return false;
        }

        try {
            Claims claims = jwtUtil.parseToken(jwt);

            Integer userId = Integer.parseInt(claims.getSubject());
            String name = claims.get("name", String.class);
            String role = claims.get("role", String.class);
            if (role == null) {
                role = "user";
            }

            request.setAttribute("id", userId);
            request.setAttribute("name", name);
            request.setAttribute("role", role);

            // 保留方案一：快过期但还没过期，先换新
            if (jwtUtil.tokenNearExpiration(jwt)) {
                String newToken = jwtUtil.generateToken(userId, name, role);
                response.setHeader("new-token", newToken);
                response.setHeader("Access-Control-Expose-Headers", "new-token");
                log.info("token 快过期，已签发 new-token");
            }

            return true;

        } catch (ExpiredJwtException e) {
            // 已经过期：前端看到 40101 就去调 /refresh
            writeError(response, 40101, "登录已过期,请刷新");
            return false;

        } catch (Exception e) {
            log.warn("令牌解析失败", e);
            writeError(response, 401, "令牌解析失败");
            return false;
        }
    }

    private void writeError(HttpServletResponse response,
                            int code,
                            String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=utf-8");
        response.setCharacterEncoding("UTF-8");
        Result error = Result.error(code, message);
        response.getWriter().write(JSONObject.toJSONString(error));
    }

    @Override
    public void postHandle(HttpServletRequest request,
                           HttpServletResponse response,
                           Object handler,
                           @Nullable ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                @Nullable Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
```

### 4.13 `config/WebConfig.java`

```java
package com.example.config;

import com.example.interception.Interceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private Interceptor interceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/login",
                        "/register",
                        "/refresh",
                        "/logout",
                        "/error"
                );
    }
}
```

为什么必须放行 `/refresh`：

access token 过期后，请求会被拦截器拦住并返回 401。如果 `/refresh` 也被拦，前端就永远进不了刷新接口，refresh token 等于白做。

### 4.14 建表 SQL

在 `User` 数据库里执行：

```sql
CREATE TABLE IF NOT EXISTS `refresh_token` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT,
  `user_id`    INT         NOT NULL,
  `token_hash` CHAR(64)    NOT NULL COMMENT 'SHA-256(raw token) 的十六进制',
  `expires_at` DATETIME    NOT NULL,
  `revoked`    TINYINT(1)  NOT NULL DEFAULT 0,
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refresh_token_hash` (`token_hash`),
  KEY `idx_refresh_token_user` (`user_id`),
  CONSTRAINT `fk_refresh_token_user`
    FOREIGN KEY (`user_id`) REFERENCES `user_info` (`id`)
    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='刷新凭证表';
```

> 如果 `user_info.id` 不是 `INT`，把 `user_id` 的类型改成和它一致，否则外键建不上。

## 5. 测试步骤

### 5.1 先把有效期改短

```yaml
jwt:
  expiration: 60000            # 1 分钟，测试用
  refresh-expiration: 604800000
```

重启项目。

### 5.2 注册并登录

```http
POST http://localhost:8080/register
Content-Type: application/json

{
  "username": "alice",
  "password": "123456"
}
```

```http
POST http://localhost:8080/login
Content-Type: application/json

{
  "username": "alice",
  "password": "123456"
}
```

返回变成：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "tokenType": "Bearer",
    "accessToken": "eyJ...",
    "refreshToken": "Xy7k...",
    "expiresIn": 60
  }
}
```

### 5.3 看数据库

```sql
select id, user_id, left(token_hash, 16) as hash_prefix,
       expires_at, revoked
from refresh_token;
```

能查到一行，`token_hash` 是 64 位十六进制，**不是** refreshToken 原文。

### 5.4 正常访问

```http
GET http://localhost:8080/showbyid/3
token: eyJ...
```

普通用户查自己应该成功；查别人应该 400「你无权查看其他用户的信息」。

### 5.5 等 access token 过期

等 70 秒左右，再请求：

```json
{
  "code": 40101,
  "message": "登录已过期,请刷新",
  "data": null
}
```

HTTP 状态是 401。

### 5.6 调 `/refresh`

```http
POST http://localhost:8080/refresh
Content-Type: application/json

{
  "refreshToken": "Xy7k..."
}
```

返回新的 `accessToken` 和 `refreshToken`。

### 5.7 测试轮换

拿**旧的** refreshToken 再调一次 `/refresh`：

```json
{
  "code": 401,
  "message": "刷新凭证无效",
  "data": null
}
```

因为旧的那条已经被 `revoked=1` 了。

### 5.8 测试退出

用新的 refreshToken 调：

```http
POST http://localhost:8080/logout
Content-Type: application/json

{
  "refreshToken": "新的 refreshToken"
}
```

再用这个 refreshToken 调 `/refresh`，会失败。

### 5.9 PowerShell 示例

```powershell
# 登录
$login = Invoke-RestMethod `
    -Uri "http://localhost:8080/login" `
    -Method Post `
    -ContentType "application/json" `
    -Body (@{ username = "alice"; password = "123456" } | ConvertTo-Json)

$access  = $login.data.accessToken
$refresh = $login.data.refreshToken

# 访问受保护接口
try {
    $r = Invoke-WebRequest `
        -Uri "http://localhost:8080/showbyid/3" `
        -Headers @{ token = $access }
    $r.StatusCode
} catch {
    $_.Exception.Response.StatusCode.value__
}

# 刷新
$new = Invoke-RestMethod `
    -Uri "http://localhost:8080/refresh" `
    -Method Post `
    -ContentType "application/json" `
    -Body (@{ refreshToken = $refresh } | ConvertTo-Json)

$new.data.accessToken
$new.data.refreshToken
```

## 6. 免登录怎么接

前端登录成功后，把 `refreshToken` 存起来（作业里用 `localStorage` 就可以）：

```js
localStorage.setItem("refreshToken", data.refreshToken);
```

页面启动时，先不急着让用户登录，拿 refreshToken 调一次 `/refresh`：

```text
有 refreshToken
  → POST /refresh
  → 成功：保存新的 accessToken + refreshToken，进入首页
  → 失败：清掉本地 refreshToken，回登录页
```

这样用户关掉页面几天，只要 refreshToken 还没过期（7 天），打开就能直接进。

安全提醒：

- `localStorage` 简单，但遇到 XSS 会被偷；作业够用。
- 更安全的做法是把 refreshToken 放在 `HttpOnly` Cookie 里，但会涉及 Cookie 跨域、CSRF，复杂度高，这道题不必展开。
- accessToken 尽量只放内存，不要长期放 `localStorage`。

## 7. 和方案一的关系

这两个机制不冲突，推荐一起用：

```text
access token 快过期但还没过期
  → 拦截器给 new-token（方案一）
  → 前端替换本地 accessToken

access token 已经过期
  → 拦截器返回 40101
  → 前端拿 refreshToken 调 /refresh（方案二）
  → 成功：换新，重试原请求
  → 失败：重新登录
```

如果两个都做，拦截器直接用方案二这一版（它已经保留了 `new-token`）。

## 8. 常见坑

1. **`/refresh`、`/logout` 必须放行。**
   否则 access token 过期后，请求先被拦截器拦住，根本进不了刷新接口。

2. **`/refresh` 不能直接调带权限判断的 `getUserById`。**
   用新增的 `getUserByIdForRefresh`，它只查库，不做 request 权限判断。

3. **refresh token 数据库只存哈希。**
   存原文的话，数据库泄露就等于所有人的登录凭证泄露。

4. **刷新必须轮换。**
   旧 refresh token 作废，发新的；不废旧 token 等于可以无限续期。

5. **并发刷新只会成功一个。**
   `update ... where id=? and revoked=0` 是原子操作；另一个请求会收到 401。前端最好做 single-flight（同一个刷新请求只发一次，其他请求等它的结果）。

6. **access token 撤销不了。**
   退出登录只能撤销 refresh token；已经发出去的 access token 在 15 分钟内仍然有效。这是 JWT 的固有特点。

7. **登录返回结构变了。**
   以前 `data` 是字符串，现在是对象；Postman 和前端都要同步改。

8. **旧 token 里没有 role。**
   拦截器里做了兜底 `role = "user"`；管理员需要重新登录一次。

9. **浏览器要能读 `new-token`。**
   跨域时加 `Access-Control-Expose-Headers: new-token`。

10. **阈值必须小于 token 有效期。**
    `tokenNearExpiration` 用 5 分钟，`expiration` 至少要比 5 分钟长。

11. **不要提交真实密钥。**
    `application.yaml` 里的 `JWT_SECRET` 和数据库密码，提交 GitHub 前换成环境变量或占位符。

12. **refresh_token 可以定期清理。**

```sql
DELETE FROM refresh_token
WHERE expires_at < NOW()
   OR (revoked = 1 AND created_at < DATE_SUB(NOW(), INTERVAL 30 DAY));
```

## 9. 实施顺序

1. 改 `application.yaml`、`jwtYaml`；
2. 改 `JwtUtil`（UTF-8、`getAccessTokenTtlSeconds`）；
3. 建 `refresh_token` 表；
4. 新增 `RefreshToken`、`RefreshTokenMapper`、`RefreshTokenService`；
5. 新增 `LoginResponse`、`RefreshRequest`、`TokenException`；
6. `UserService` / `UserServiceImpl` 加 `getUserByIdForRefresh`；
7. 改 `UserController` 的登录，新增 `/refresh`、`/logout`；
8. 改 `GlobalExceptionHandler`；
9. 改 `Interceptor`、`WebConfig`；
10. 按第 5 节测试；
11. 前端接免登录流程。

每一步都能单独编译、单独测，比一次性全改更容易排错。
