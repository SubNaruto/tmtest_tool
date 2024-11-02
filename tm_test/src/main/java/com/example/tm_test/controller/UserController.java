package com.example.tm_test.controller;

import com.example.tm_test.StartController;
import com.example.tm_test.TmTestApplication;
import com.example.tm_test.entity.Users;
import com.example.tm_test.mapper.UserMapper;
import com.example.tm_test.nodeDeploy.userservice;
import com.example.tm_test.nodeDeploy.userssh;
import jakarta.annotation.Resource;
import org.apache.catalina.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class UserController {

    @Resource
    UserMapper usermapper;

    @CrossOrigin(origins = "http://localhost:8080",allowCredentials = "true")
    @GetMapping("/user")
    public List<Users> getUsers(){
        return usermapper.findAll();
    }
    @Autowired
    private userssh ssh;
    @Autowired
    private userservice userService;

    @CrossOrigin(origins = "http://localhost:8080",allowCredentials = "true")
    @PostMapping("/TNodeStart")
    public String startProgram(String[] args) {
        ssh.establishSSH();
        return "节点部署已启动";
    }
    @CrossOrigin(origins = "http://localhost:8080",allowCredentials = "true")
    @PostMapping("/insert")
    public String insertUser(@RequestBody Users user) {
        userService.insertUser(user);
        return "添加成功";
    }

    @CrossOrigin(origins = "http://localhost:8080",allowCredentials = "true")
    @PostMapping("/update")
    public String updateUser(@RequestBody Users user) {
        userService.updateUser(user);
        return "添加成功";
    }

    @CrossOrigin(origins = "http://localhost:8080",allowCredentials = "true")
    @DeleteMapping("/delete/{id}")
    public void deleteUser(@PathVariable Integer id) {
        userService.deleteUserById(id);
    }
    
}
