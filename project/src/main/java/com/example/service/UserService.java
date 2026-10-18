package com.example.service;

import java.util.List;
import java.util.Map;


public interface UserService {
     /*Result*/void login(com.example.pojo.User user);

     /*Result*/void register(com.example.pojo.User user);

     List<com.example.pojo.User> showUsers(); // 普查

     List<com.example.pojo.User> pageShowUsers(Integer page, Integer pageSize); //分页查询

     Map<String,Integer> pageShowUsersCount();
}
