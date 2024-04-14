package com.example.tm_test;

import com.example.tm_test.nodeDeploy.userssh;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;

@Component
public class StartController {
    @Autowired
    private userssh ssh;
    public void start() {
        ssh.establishSSH();
    }
}
