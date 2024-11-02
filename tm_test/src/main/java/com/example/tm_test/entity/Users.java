package com.example.tm_test.entity;

import lombok.Data;

@Data
public class Users {
    private Integer id;
    private String name;
    private String ip;
    private String keyword;
    private Integer nodenum;
    private Integer port;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public Integer getNodenum() {
        return nodenum;
    }

    public void setNodenum(Integer nodenum) {
        this.nodenum = nodenum;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }
}
