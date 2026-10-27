package com.example.interception;

import com.alibaba.fastjson.JSONObject;
import com.example.result.Result;
import com.example.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
@Slf4j
@Component
public class Interceptor implements HandlerInterceptor {
    @Autowired
    private JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String url = request.getRequestURL().toString(); // 获取请求的URL
        log.info("请求的url:{}", url);

        String jwt = request.getHeader("token"); //获取请求头中的令牌

        if(!StringUtils.hasText(jwt)){ // 检验令牌是否存在
            log.info("未登录,令牌不存在");
            response.setContentType("application/json;charset=utf-8"); // 不加会出现中文乱码
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // postman里显示一下响应的状态码
            Result error = Result.error(401,"登录状态无效"); // 错误信息
            String info = JSONObject.toJSONString(error); // 转换成JSON格式
            response.getWriter().write(info); // 写入响应体
            return false;
        }

        try{
            Claims claims = jwtUtil.parseToken(jwt); // 解析令牌是否有效
            Integer userId = Integer.parseInt(claims.getSubject()); // 获取登录用户的姓名和id,方便后面调用
            String name = claims.get("name").toString();
            String role = claims.get("role").toString();
            if(jwtUtil.tokenNearExpiration(jwt)){ // 如果马上要过期
                String newtoken = jwtUtil.generateToken(userId,name,role); // 刷新一下
                log.info("新token已生成");
                response.setHeader("new-token",newtoken); // 新token放入响应头
                }
            request.setAttribute("id",userId); // 把信息暂时存进HttpServletRequest对象,方便以后使用
            request.setAttribute("name",name);
            request.setAttribute("role",role);
        }catch (Exception e){
            log.info("令牌解析失败");
            response.setContentType("application/json;charset=utf-8");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            Result error = Result.error(401,"令牌解析失败");
            String info = JSONObject.toJSONString(error);
            response.getWriter().write(info);
            return false;
        }

        log.info("令牌有效,放行");
        return true;
    }


    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }


    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, @Nullable Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}


