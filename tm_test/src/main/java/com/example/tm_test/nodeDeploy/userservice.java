package com.example.tm_test.nodeDeploy;

import com.example.tm_test.entity.Users;
import com.example.tm_test.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class userservice {

    @Autowired
    private UserMapper userMapper;

    public List<Users> findAllUsers() {
        return userMapper.findAll();
    }

    public void insertUser(Users user) {
        user.setId(findNextId());
        userMapper.insertUser(user);
    }

    public void deleteUserById(Integer id) {
        userMapper.deleteUserById(id);
    }

    public void updateUser(Users user) {
        userMapper.updateUser(user);
    }

    private int findNextId() {
        List<Integer> Ids = userMapper.findAllIds(); // 查找最小的未使用id
        int nextId=1;
        for (Integer id : Ids) {
            if (id != nextId) {
                break; // 当前id不为期望，则跳出循环
            }
            nextId++;
        }
        return nextId;
    }
}

