package com.example.controller;

import com.example.result.Result;
import com.example.service.UserService;
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



    @PostMapping("/register")
    public Result register(@Valid @RequestBody com.example.pojo.User user){ // 获取json格式的信息
        log.info("{}成功注册",user.getUsername());// 打印日志
        this.userService.register(user);
        /* 返回Result,第一遍把Result写到了实现类里,但实际上返回结果这部分应该是controller的工作,
           [第一版]return register.register(user);
           导致controller这部分代码怪怪的 ↑↑ ,现在看过去也不知道自己当时怎么想的(汗)
         */
        return Result.success();
    }



    @PostMapping("/login")
    public Result login(@Valid @RequestBody com.example.pojo.User user){
        log.info("{} 成功登录",user.getUsername());
        this.userService.login(user); // 调用实现类的方法
        return Result.success(); // 返回结果
    }



    @GetMapping("/show")
    public Result show(){ // 普查
        List<com.example.pojo.User> users = userService.showUsers();
        return Result.success(users);
    }


    @GetMapping("/showbypage") // 分页查询
    public Result showBypage(@RequestParam(defaultValue = "1") Integer page,
                             @RequestParam(defaultValue = "5") Integer pageSize){// page=页码(默认为1),pageSize=每页信息数(默认为5)@Validated @RequestParam(defaultValue = "5") Integer pageSize){
        List<com.example.pojo.User> users = userService.pageShowUsers(page,pageSize); // 以User类的格式,List形式返回值
        return Result.success(users);
    }


    @GetMapping("/showcount")
    public Result showCount(){
        Map<String,Integer> map = userService.pageShowUsersCount();
        return Result.success(map);
    }
}
