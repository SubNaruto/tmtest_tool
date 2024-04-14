package com.example.tm_test.controller;

import com.example.tm_test.entity.Users;
import com.example.tm_test.mapper.UserMapper;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    UserMapper usermapper;

    @GetMapping
    public List<Users> getUsers(){
        return usermapper.findAll();
    }
}
