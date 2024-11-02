package com.example.tm_test.nodeDeploy;

import com.jcraft.jsch.Session;

class RemoteSession {
    Session session;// 会话对象，管理与SSH服务器的连接以及处理来自服务器的消息
    String absoluteRemoteRootPath;// 部署集群时，服务器的根目录

    String absoluteRemoteWorkPath; // /dockerfiles

    RemoteSession(Session s, String str) {
        session = s;
        absoluteRemoteRootPath = str;
        absoluteRemoteWorkPath = absoluteRemoteRootPath + "/dockerfiles";
    }

    @Override
    public String toString() {// 返回对象的字符串表示形式
        return "{" + session.getUserName() + "," + session.getUserInfo() + "," + absoluteRemoteRootPath + "," + absoluteRemoteWorkPath + "}";
    }
}
