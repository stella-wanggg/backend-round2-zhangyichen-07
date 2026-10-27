package com.example.service.impl;

import com.example.exception.BusinessException;
import com.example.mapper.UserMapper;
import com.example.pojo.User;
import com.example.service.UserService;
import com.example.util.JwtUtil;
import com.example.util.PasswordUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private PasswordUtil passwordUtil;

    @Autowired
    private UserMapper userMapper;

    @Override
    public User login(com.example.pojo.User user,String token) {
        if(token != null){ // 带了token,免登录
            try{
            Claims claims = jwtUtil.parseToken(token); // token有效
            Integer id = Integer.parseInt(claims.getSubject());
            User u = userMapper.getUserById(id);
            return u;
            }
            catch(JwtException e){ // token过期的异常/token无效(像被删了一段)的异常
                if (user == null){ // 没带信息
                    throw new BusinessException("token已过期或token无效");
                }
                // 带了信息,继续
            }
            catch(Exception e){  // 其他异常
                throw new BusinessException("token无效");
            }
        }
        if(user == null){
            throw new BusinessException("信息不能为空");
        }
        User u = userMapper.getUserByUsername(user.getUsername());// 先确定有没有名字相同的
        if(u == null){
            throw new BusinessException("用户不存在");
        }
        if (passwordUtil.CheckPassword(user.getPassword(),u.getPassword())) { // 有的话再看看密码一不一样
            return u;
        }
        throw new BusinessException("密码错误");
    }


    @Override
    public void register(com.example.pojo.User user) {
        User u = userMapper.getUserByUsername(user.getUsername());
        if(u != null) { // 判是否重名
            throw new BusinessException("用户名已被占用");
        }
        String passwordplus = passwordUtil.NewPassword(user.getPassword()); // 密码加密
        user.setPassword(passwordplus); // 重置加密密码
        user.setRole("user"); // 不管前端输入进来什么身份,统一认定为普通用户,管理员身份只能用SQL语句手动修改数据库的role
        userMapper.addUsers(user); // 向表中填数据
    }

    @Override
    public User getUserById(Integer id,HttpServletRequest request) {
        Integer selfId = (Integer) request.getAttribute("id");
        if(id == null || id <=0){
            throw new BusinessException("id输入有误");
        }
        if (!testRole(request)) {
            if (!id.equals(selfId)) {// 普通用户只能查自己
                throw new BusinessException("你无权访问"); // id不是自己的,不能查
            }
            User user = userMapper.getUserById(id);
            return user;
        }
        User user = userMapper.getUserById(id); // 管理员可以查所有人
        return user;
    }

    // 管理员专享
    @Override
    public List<com.example.pojo.User> showUsers(HttpServletRequest httpServletRequest) {
        if(!testRole(httpServletRequest)){ // 判断为普通用户
            throw new BusinessException("你无权访问");
        }
        List<com.example.pojo.User> users = userMapper.getUsers();
        return users;
    }


    @Override
    public List<com.example.pojo.User> pageShowUsers(Integer page, Integer pageSize,HttpServletRequest httpServletRequest) {
        if(!testRole(httpServletRequest)){
            throw new BusinessException("你无权访问");
        }
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
    public Map<String,Integer> pageShowUsersCount(HttpServletRequest httpServletRequest) {
        if(!testRole(httpServletRequest)){
            throw new BusinessException("你无权访问");
        }
        Map<String,Integer> map = new HashMap<>();
        Integer count = userMapper.getUserCount();
        map.put("现在注册的用户数为:",count);
        return map;
    }


    // 把检验身份的统一代码抽取出来
    public boolean testRole(HttpServletRequest request){
        String role = request.getAttribute("role").toString(); // 获取身份信息
        if(!role.equals("admin") && !role.equals("user")){
            throw new BusinessException("身份信息填写有误");
        }
        if(role.equals("user")){ // 是普通用户
            return false;
        }
        return true; // 是管理员
    }
}
