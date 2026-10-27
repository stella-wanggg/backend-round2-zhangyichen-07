package com.example.service;

import com.example.pojo.User;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Map;


public interface UserService {
     User login(com.example.pojo.User user,String token);

     void register(com.example.pojo.User user);

     User getUserById(Integer id,HttpServletRequest request);

     List<com.example.pojo.User> showUsers(HttpServletRequest httpServletRequest); // 普查

     List<com.example.pojo.User> pageShowUsers(Integer page, Integer pageSize,HttpServletRequest httpServletRequest); //分页查询

     Map<String,Integer> pageShowUsersCount(HttpServletRequest httpServletRequest);

}
