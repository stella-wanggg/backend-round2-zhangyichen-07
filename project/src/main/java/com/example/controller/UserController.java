package com.example.controller;

import com.example.exception.BusinessException;
import com.example.interception.Interceptor;
import com.example.pojo.User;
import com.example.result.Result;
import com.example.service.UserService;
import com.example.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
public class UserController {
    @Autowired
    private UserService userService; // 引入实现类

    @Autowired
    private JwtUtil jwtUtil; // 引入令牌工具类

    @PostMapping("/register")
    public Result register(@Valid @RequestBody com.example.pojo.User user){ // 获取json格式的信息
        log.info("{}成功注册",user.getUsername());// 打印日志
        userService.register(user);
        return Result.success();
    }

    @PostMapping("/login")
    public Result login(@Valid @RequestBody(required = false) com.example.pojo.User user,
                        @RequestHeader(value = "token",required = false) String token){
        User u = userService.login(user,token); // 调用实现类的方法,获取user对象
        String newToken = jwtUtil.generateToken(u.getId(),u.getUsername(),u.getRole()); // 生成令牌
        log.info("{} 成功登录",u.getUsername());
        return Result.success(newToken); // 登录成功,发放令牌
    }

    @GetMapping("/showbyid/{id}") // 根据id查询用户,什么身份都能查询
    public Result showById(@PathVariable("id") Integer id,HttpServletRequest request){
        User user = userService.getUserById(id,request);
        return Result.success(user);
    }


    // 仅管理员能调用
    @GetMapping("/show")
    public Result show(HttpServletRequest httpServletRequest){ // 普查
        List<com.example.pojo.User> users = userService.showUsers(httpServletRequest);
        return Result.success(users);
    }

    @GetMapping("/showbypage") // 分页查询
    public Result showBypage(@RequestParam(defaultValue = "1") Integer page,
                             @RequestParam(defaultValue = "5") Integer pageSize,
                             HttpServletRequest httpServletRequest){
        // page=页码(默认为1),pageSize=每页信息数(默认为5)@Validated @RequestParam(defaultValue = "5") Integer pageSize){
        List<com.example.pojo.User> users = userService.pageShowUsers(page,pageSize,httpServletRequest); // 以User类的格式,List形式返回值
        return Result.success(users);
    }

    @GetMapping("/showcount")
    public Result showCount(HttpServletRequest httpServletRequest){
        Map<String,Integer> map = userService.pageShowUsersCount(httpServletRequest);
        return Result.success(map);
    }



}
