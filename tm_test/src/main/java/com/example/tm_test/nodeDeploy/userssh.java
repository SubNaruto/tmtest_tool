package com.example.tm_test.nodeDeploy;

import com.example.tm_test.entity.Users;
import com.example.tm_test.mapper.UserMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jcraft.jsch.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class RemoteSession {
    Session session;// 会话对象，管理与SSH服务器的连接以及处理来自服务器的消息
    String absoluteSessionPath;// 部署集群时，多个服务器的dockerfiles的绝对地址一致

    RemoteSession(Session s, String str) {
        session = s;
        absoluteSessionPath = str;
    }

    @Override
    public String toString() {
        return "{" + session.getUserName() + "," + session.getUserInfo() + "," + absoluteSessionPath + "}";
    }
}

@Component
public class userssh {

    @Autowired
    private UserMapper usermapper;

    // 用于存储各个远端服务器中节点在部署集群时所需的一些配置信息
    public static String LocalFilePath = "/Users/duanjincheng/Desktop/tmtest_tool/configfile";


    public void establishSSH() {
        try {
            // 1.启动所有session，建立索引, userid -> session
            Map<Integer, RemoteSession> map = establishUserSessionMap(usermapper.findAll());
            System.out.println(map);

            // 2.并行启动docker容器编排，CountDownLatch是内部计数器，阻塞主线程
            CountDownLatch countDownLatch = new CountDownLatch(map.size());
            concurrentStartDocker(map, countDownLatch);

            // 3.主线程阻塞，并行下载
            countDownLatch = new CountDownLatch(map.size());
            concurrentDownload(map, countDownLatch);

            //4.主线程放开，开始整合
            countDownLatch.await();
            configUnify(map);

            //5.主线程阻塞，并行上传
            countDownLatch = new CountDownLatch(map.size());
            concurrentUpload(map, countDownLatch);

            // 6.并行每个session的每个docker节点运行tendermint
            countDownLatch = new CountDownLatch(map.size());
            concurrentTendermintForSession(map, countDownLatch);

            //0.主线程放开，清理资源！
            countDownLatch.await();
            onDestroySession(map);
        } catch (Exception e) {
            e.printStackTrace(); // 打印异常信息
        }
    }

    /**
     * docker节点启动tendermint，部署集群
     *
     * @param countDownLatch
     */
    private void concurrentTendermintForSession(Map<Integer, RemoteSession> userSessionMap, CountDownLatch countDownLatch) {
        if (userSessionMap == null || userSessionMap.isEmpty()) {
            return;
        }
        System.out.println("启动tendermint开始");
        ThreadPoolExecutor startDockerThreadPoolExecutor = generateThreadPoolExecutor();

        for (Map.Entry<Integer, RemoteSession> entry : userSessionMap.entrySet()) {
            if (entry == null) {
                continue;
            }
            startDockerThreadPoolExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    RemoteSession remoteSession = entry.getValue();
                    startTendermint(remoteSession.session);
                    countDownLatch.countDown();
                }
            });

        }
        System.out.println("启动tendermint结束");
    }

    /**
     * 启动tendermint
     *
     * @param session
     */
    private void startTendermint(Session session) {
        if (session == null) {
            return;
        }
        //todo order组装
        // 1.container id
        // 2.node id
        // 3.yml中的ip地址
        String containerID = "";
        List<String> nodeId = new ArrayList<>();
        List<String> nodeIp = new ArrayList<>();
        List<String> nodePort = new ArrayList<>();

        int nodeCount = nodeId.size();
        String tendermintOrder = "nohup ./tendermint node " +
                "--p2p.persistent_peers=\"85ba184d2cfbea97c75c98abeae23cd967af43ea@172.18.0.2:16656" +
                ",6ca9a848ca9fc4d2f0dcebefbc58d902f39dc701@172.18.0.3:26656" +
                ",28715b4f83db63885bb7ffdc0ba15fe21bf2f31d@172.18.0.4:36656" +
                ",05a02132b0d89f788b318f13e259e24544a30b25@172.18.0.5:46656\" &";
        String finalOrder = "docker exec -it" + containerID + " /bin/bash -c " + tendermintOrder;
        try {
            ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
            for (int i = 0; i < nodeCount; i++) {
                channelExec.setCommand(finalOrder);
                channelExec.connect();
            }
            channelExec.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 并行启动docker节点
     *
     * @param userSessionMap
     * @param countDownLatch
     */
    private void concurrentStartDocker(Map<Integer, RemoteSession> userSessionMap, CountDownLatch countDownLatch) {
        if (userSessionMap == null || userSessionMap.isEmpty()) {
            return;
        }
        System.out.println("并行启动开始");
        ThreadPoolExecutor startDockerThreadPoolExecutor = generateThreadPoolExecutor();

        for (Map.Entry<Integer, RemoteSession> entry : userSessionMap.entrySet()) {
            if (entry == null) {
                continue;
            }
            startDockerThreadPoolExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    RemoteSession remoteSession = entry.getValue();
                    startDocker(remoteSession.session);
                    countDownLatch.countDown();
                }
            });

        }
        System.out.println("并行启动结束");
    }

    /**
     * 启动docker
     *
     * @param session
     */
    private void startDocker(Session session) {
        if (session == null) {
            return;
        }
        try {
            ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
            channelExec.setCommand("docker compose up");
            channelExec.connect();
            channelExec.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 关闭所有session，清理资源
     */
    private void onDestroySession(Map<Integer, RemoteSession> userSessionMap) {
        if (userSessionMap == null || userSessionMap.isEmpty()) {
            return;
        }
        for (RemoteSession remoteSession : userSessionMap.values()) {
            if (remoteSession != null && remoteSession.session != null) {
                remoteSession.session.disconnect();
            }
        }
        userSessionMap.clear();
        System.out.println("清理结束");
    }


    /**
     * 启动所有session，建立索引
     */
    private Map<Integer, RemoteSession> establishUserSessionMap(List<Users> userlist) {
        System.out.println("创建 session map 开始");
        Map<Integer, RemoteSession> map = new HashMap<>();

        for (Users user : userlist) {
            String ip = user.getIp();// SSH地址
            int port = user.getPort();// 令SSH端口为22
            String name = user.getName(); // SSH登录用户名
            String keyword = user.getKeyword(); // SSH登录密码
            JSch jsch = new JSch();
            Session session = null;
            try {
                session = jsch.getSession(name, ip, port);

                session.setPassword(keyword);// 设置密码

                // 禁用主机密钥检查 StrictHostKeyChecking
                java.util.Properties config = new java.util.Properties();
                // java.util.Properties用于管理属性集合；继承于java.util.Hashtable类，具有哈希表的特性，可以存储键值对
                config.put("StrictHostKeyChecking", "no");// 添加键值对
                session.setConfig(config);

                session.connect();// 连接SSH服务器

                // 创建用于执行命令的通道，所以将通道转换为ChannelExec类型
                ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
                channelExec.setCommand("pwd");
                channelExec.connect();

                // 从ChannelExec获取输入流，用于从远程服务器读取执行命令的输出
                InputStream in = channelExec.getInputStream();
                byte[] buffer = new byte[1024];
                StringBuilder stringBuilder = new StringBuilder();
                int bytesRead;

                // in.read(buffer)从输入流中读取数据，并将读取的字节存储到buffer数组
                while ((bytesRead = in.read(buffer)) != -1) {
                    stringBuilder.append(new String(buffer, 0, bytesRead));
                }
                String remotePath = stringBuilder.toString().trim();  // trim()去除字符串两端空格
                map.put(user.getId(), new RemoteSession(session, remotePath + "/duan_workspace"));

                channelExec.disconnect();
            } catch (JSchException | IOException e) {
                throw new RuntimeException(e);
            }

        }
        System.out.println("创建 session map 结束");
        return map;
    }


    private ThreadPoolExecutor generateThreadPoolExecutor() {
        int corePoolSize = 5; // 核心线程数
        int maximumPoolSize = 10; // 最大线程数
        long keepAliveTime = 10;
        return new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, new LinkedBlockingDeque<>());
    }


    /**
     * 并行下载，当所有服务器节点的配置内容下载完毕之后，才开始整合
     **/
    public void concurrentDownload(Map<Integer, RemoteSession> userSessionMap, CountDownLatch countDownLatch) {
        if (userSessionMap == null || userSessionMap.isEmpty()) {
            return;
        }
        System.out.println("sftp 并行下载开始");
        ThreadPoolExecutor downloadThreadPoolExecutor = generateThreadPoolExecutor();
        for (Map.Entry<Integer, RemoteSession> entry : userSessionMap.entrySet()) {
            if (entry == null) {
                return;
            }
            downloadThreadPoolExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    Integer id = entry.getKey();
                    RemoteSession remoteSession = entry.getValue();
                    // 本地对应远端的唯一路径，通过userid来区分!!!
                    String specialLocalPath = LocalFilePath + "/" + id;
                    downloadFromSSH(remoteSession.session, specialLocalPath, remoteSession.absoluteSessionPath);
                    // 每个线程下载完毕后，计数器减去1
                    countDownLatch.countDown();
                }
            });
        }
        System.out.println("sftp 并行下载完成");

    }

    /**
     * 并行上传
     */
    private void concurrentUpload(Map<Integer, RemoteSession> userSessionMap, CountDownLatch countDownLatch) {
        if (userSessionMap == null || userSessionMap.isEmpty()) {
            return;
        }
        System.out.println("sftp 并行上传开始");
        ThreadPoolExecutor downloadThreadPoolExecutor = generateThreadPoolExecutor();
        for (Map.Entry<Integer, RemoteSession> entry : userSessionMap.entrySet()) {
            downloadThreadPoolExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    Integer id = entry.getKey();
                    RemoteSession remoteSession = entry.getValue();
                    String specialLocalPath = LocalFilePath + "/" + id;
                    uploadSSH(remoteSession.session, specialLocalPath, remoteSession.absoluteSessionPath);
                    // 每个线程下载完毕后，计数器减去1
                    countDownLatch.countDown();
                }
            });
        }
        System.out.println("sftp 并行上传完成");
    }


    private void downloadFromSSH(Session session, String localFilePath, String remoteFilePath) {
        try {
            Channel channel = session.openChannel("sftp");
            channel.connect();
            ChannelSftp sftp = (ChannelSftp) channel;

            File file = new File(localFilePath);
            if (!file.exists() && !file.mkdir()) {
                throw new RuntimeException("创建本地文件夹失败！");
            }

            downloadFolder(sftp, localFilePath, remoteFilePath);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    // 递归下载远端服务器的文件夹所有内容到本机
    private void downloadFolder(ChannelSftp channelSftp, String localPath, String remotePath) {
        try {
            List<ChannelSftp.LsEntry> list = channelSftp.ls(remotePath);
            for (ChannelSftp.LsEntry lsEntry : list) {
                String localRealPath = localPath + "/" + lsEntry.getFilename();
                String remoteRealPath = remotePath + "/" + lsEntry.getFilename();
                if (lsEntry.getAttrs().isDir()) {
                    // 文件夹！
                    if (!".".equals(lsEntry.getFilename()) && !"..".equals(lsEntry.getFilename())) {
                        File localFolder = new File(localRealPath);
                        localFolder.mkdir();
                        downloadFolder(channelSftp, localRealPath, remoteRealPath);
                    } else {
                        System.out.println("跳过对 . 和 ..文件路径的处理！");
                    }
                } else {
                    // 文件！
                    // SFTP（SSH File Transfer Protocol）的get方法从远端服务器路径下拉取文件到本地路径
                    channelSftp.get(remoteRealPath, localRealPath);
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private boolean configUnify(Map<Integer, RemoteSession> map) {
        System.out.println("整合开始");
        File outputfile = new File(LocalFilePath + "/genesis_temp.json");
        try {
            outputfile.delete();
            outputfile.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        //genesis.json的路径集合
        List<String> rests = new ArrayList<String>();
        findChildFile(rests, LocalFilePath);

        boolean isFirst = true;
        JsonParser parser = new JsonParser();
        JsonObject firstJsonObject = new JsonObject();
        JsonArray firstValidators = null;
        // 提取整合内容
        for (String s : rests) {
            try {
                if (isFirst) {
                    firstJsonObject = parser.parse(new FileReader(s)).getAsJsonObject();
                    firstValidators = firstJsonObject.getAsJsonArray("validators");
                    isFirst = false;
                } else {
                    JsonObject extraJsonObject = parser.parse(new FileReader(s)).getAsJsonObject();
                    JsonArray extraValidators = extraJsonObject.getAsJsonArray("validators");
                    firstValidators.addAll(extraValidators);// 在第一个节点的验证方里，添加其他节点为验证方
                }
                System.out.println(firstJsonObject.toString());
            } catch (Exception e) {
                System.out.println(s + " 文件内容不是json对象！");
            }
        }
        // 更新object
        firstJsonObject.remove("validators");
        firstJsonObject.add("validators", firstValidators);


        // 覆盖所有本地json 文件内容
        try {
            // 1.节点文件写入
            String content = firstJsonObject.toString();
            for (String s : rests) {
                File file = new File(s);
                FileWriter fileWriter = new FileWriter(file);
                fileWriter.write(content);
                fileWriter.close();
            }
            // 2.暂存文件写入
            FileWriter fileWriter = new FileWriter(outputfile);
            fileWriter.write(content);
            fileWriter.close();
        } catch (IOException e) {
            System.out.println("整合 IOException");
        }
        System.out.println("整合结束");
        return true;
    }

    private void findChildFile(List<String> rests, String localFilePath) {
        File localFile = new File(localFilePath);
        String[] childFilePaths = localFile.list();
        if (childFilePaths == null) {
            System.out.println("子文件夹为空！");
            return;
        }
        for (String s : childFilePaths) {
            String childPath = localFilePath + "/" + s;
            File childFile = new File(childPath);
            if (childFile.isDirectory()) {
                if (!".".equals(childFile.getName()) && !"..".equals(childFile.getName())) {
                    findChildFile(rests, childPath);
                }
            } else if ("genesis.json".equals(childFile.getName())) {
                rests.add(childPath);
            }
        }
    }

    private void uploadSSH(Session session, String localFilePath, String remoteFilePath) {
        try {
            Channel channel = session.openChannel("sftp");
            channel.connect();
            ChannelSftp sftp = (ChannelSftp) channel;

            uploadFolder(sftp, localFilePath, remoteFilePath);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private void uploadFolder(ChannelSftp channelSftp, String localPath, String remotePath) throws FileNotFoundException {
        List<String> localJsonsPath = new ArrayList<>();
        findChildFile(localJsonsPath, localPath);// 获取本地路径下的所有json文件路径
        List<String> remoteJsonPath = new ArrayList<>(localJsonsPath);

        // 远端json路径更新
        remoteJsonPath.replaceAll(s -> s.replace(localPath, remotePath));

        System.out.println("remote:" + remoteJsonPath);
        System.out.println("local:" + localJsonsPath);

        try {
            for (int i = 0; i < localJsonsPath.size(); i++) {

                // SFTP（SSH File Transfer Protocol）的put方法把本地路径下文件推到远端服务器上
                channelSftp.put(localJsonsPath.get(i), remoteJsonPath.get(i));
            }
        } catch (SftpException e) {
            System.out.println("上传失败");
        }

    }

}



