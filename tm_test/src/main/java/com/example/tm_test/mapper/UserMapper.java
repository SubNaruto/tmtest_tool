package com.example.tm_test.mapper;
import com.example.tm_test.entity.Users;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Component;

import java.util.List;
@Component
public interface UserMapper {
    @Select("select * from Users")
    List<Users>findAll();

    @Select("SELECT id FROM Users ORDER BY id ASC")
    List<Integer> findAllIds();


    @Insert("insert into Users (id,name, ip, keyword, port, nodenum ) values (#{id},#{name}, #{ip}, #{keyword}, #{port}, #{nodenum})")
    void insertUser(Users user);

    @Delete("delete from Users where id = #{id}")
    void deleteUserById(Integer id);

    @Update("update Users set name = #{name}, ip = #{ip}, keyword = #{keyword}, port = #{port}, nodenum= #{nodenum} where id = #{id}")
    void updateUser(Users user);
}
