package com.example.tm_test.mapper;
import com.example.tm_test.entity.Users;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Component;

import java.util.List;
@Component
public interface UserMapper {
    @Select("select * from Users")
    List<Users>findAll();
}
