package com.example.service.impl;

import com.example.exception.BusinessException;
import com.example.mapper.UserMapper;
import com.example.pojo.User;
import com.example.service.UserService;
import com.example.util.PasswordUtil;
import com.example.yaml.useryaml;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Override
    public void login(com.example.pojo.User user) {
        User u = userMapper.getUserByUsername(user.getUsername());// 先确定有没有名字相同的
        if(u == null){
            throw new BusinessException("用户不存在");
        }
        if (PasswordUtil.CheckPassword(user.getPassword(),u.getPassword())) { // 有的话再看看密码一不一样
            return;
        }
        throw new BusinessException("密码错误");
    }


    @Override
    public void register(com.example.pojo.User user) {
        User u = userMapper.getUserByUsername(user.getUsername());
        if(u != null) { // 判是否重名
            throw new BusinessException("用户名已被占用");
        }
        String passwordplus = PasswordUtil.NewPassword(user.getPassword()); // 密码加密
        user.setPassword(passwordplus); // 重置加密密码
        userMapper.addUsers(user); // 向表中填数据
    }


    @Override
    public List<com.example.pojo.User> showUsers() {
        List<com.example.pojo.User> users = userMapper.getUsers();
        return users;
    }


    @Override
    public List<com.example.pojo.User> pageShowUsers(Integer page, Integer pageSize) {
        if (page == null || page < 1) {
            throw new BusinessException("页码不能为空或为负");
        }
        if (pageSize == null || pageSize < 1) {
            throw new BusinessException("每页信息数不能为空或为负");
        }
        Integer count = userMapper.getUserCount();// 总User数
        Integer start = (page - 1) * pageSize; // 起始页计算公式=(页码-1)*每页信息数
        Integer pageTotal = (count+pageSize-1)/pageSize; // 页数计算公式
        if(count > 0 && pageTotal < page){ // 防止页码过大和count为0时出问题
            throw new BusinessException("数据不足,你的数据总数是"+count+
                                        ",你将pageSize设定为"+pageSize+
                                        ",则总页数为"+pageTotal+
                                        ",建议将页码改为小于等于"+pageTotal+
                                        "的数");
        }
        List<com.example.pojo.User> users = userMapper.getUsersByPage(start, pageSize);
        return users;
    }

    @Override
    public Map<String,Integer> pageShowUsersCount() {
        Map<String,Integer> map = new HashMap<>();
        Integer count = userMapper.getUserCount();
        map.put("现在注册的用户数为:",count);
        return map;
    }
}
