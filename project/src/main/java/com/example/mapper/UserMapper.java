package com.example.mapper;

import com.example.pojo.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@org.apache.ibatis.annotations.Mapper
public interface UserMapper {
    // 普通查询
    @Select("select * from user_info")
    public List<User> getUsers();

    // 分页查询(加limit,导入前面计算出的起始页和自定义的每页信息数)
    @Select("select * from user_info limit #{start},#{pageSize}")
    public List<User> getUsersByPage(Integer start,Integer pageSize);

    // 增加用户
    @Insert("insert into user_info(username,password,role) values (#{username},#{password},#{role})")
    public void addUsers(User user);

    @Select("select * from user_info where username = #{username}")
    public User getUserByUsername(String username);

    @Select("select count(*) from user_info")
    public int getUserCount();

    @Select("select * from user_info where id = #{id}")
    public User getUserById(Integer id);
}
