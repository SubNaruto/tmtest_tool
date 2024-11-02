package com.example.tm_test.nodeDeploy;

import com.example.tm_test.entity.Users;
import com.example.tm_test.mapper.UserMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jcraft.jsch.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;


import java.io.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
public class userssh {

    @Autowired
    private UserMapper usermapper;

    // sessionid -> map(container_name -> TNodeInfo)

    private Map<Integer, Map<String, TNodeInfo>> tendermintMap = new HashMap<>();

    // 用于存储各个远端服务器中节点在部署集群时所需的一些配置信息
    public static String RemoteWorkSpace = "dockerfiles";
    public static String LocalFilePath = "/Users/duanjincheng/Desktop/tmtest_tool/configfile";


    public void establishSSH() {
        try {


            // 0.启动所有session，建立索引, userid -> session
            Map<Integer, RemoteSession> map = establishUserSessionMap(usermapper.findAll());
            System.out.println(map);

            // 1.准备工作，运行测试工具jmeter等
            preparationWork(map);

            // 2.并行启动docker容器编排，CountDownLatch是内部计数器，阻塞主线程
            CountDownLatch countDownLatch = new CountDownLatch(map.size());
            concurrentStartDocker(map, countDownLatch);
            countDownLatch.await();

            // 3.主线程阻塞，并行下载
            countDownLatch = new CountDownLatch(map.size());
            concurrentDownload(map, countDownLatch);
            countDownLatch.await();

            //4.主线程放开，开始整合
            configUnify(map);

            //5.主线程阻塞，并行上传
            countDownLatch = new CountDownLatch(map.size());
            concurrentUpload(map, countDownLatch);
            countDownLatch.await();

            // 6.并行每个session的每个docker节点运行tendermint
            countDownLatch = new CountDownLatch(map.size());
            concurrentTendermintForSession(map, countDownLatch);
            countDownLatch.await();

            //7.主线程放开，清理资源！x
            onDestroySession(map);
        } catch (Exception e) {
            e.printStackTrace(); // 打印异常信息
        }
    }

    private void preparationWork(Map<Integer, RemoteSession> map) {
        ThreadPoolExecutor threadPoolExecutor = generateThreadPoolExecutor();
        for (Map.Entry<Integer, RemoteSession> entry : map.entrySet()) {
            if (entry == null) {
                continue;
            }
            threadPoolExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    RemoteSession remoteSession = entry.getValue();
                    Session session = remoteSession.session;
                    startJMeter(session);
                }
            });
        }
    }

    private void startJMeter(Session session) {
        if (session == null) {
            return;
        }
        try {
            System.out.println("执行JMeter");
            ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
            channelExec.setCommand("ls -l /home/dennis/env/go/bin/go && cd ~/tendermint-prov-plus-master/jmtest/jm-dir && nohup /home/dennis/env/go/bin/go run jm.go > /tmp/go_run_output.log 2>&1 &");
            channelExec.connect();

            // 捕获输出
            InputStream inputStream = channelExec.getInputStream();

            // 读取命令输出
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);  // 打印每一行的输出
            }

//            InputStream in = channelExec.getInputStream();
//            byte[] buffer = new byte[1024];
//            StringBuilder stringBuilder = new StringBuilder();
//            int bytesRead;
//
//            // in.read(buffer)从输入流中读取数据，并将读取的字节存储到buffer数组
//            while ((bytesRead = in.read(buffer)) != -1) {
//                stringBuilder.append(new String(buffer, 0, bytesRead));
//            }
            System.out.println("执行JMeter over" );
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
                    //System.out.println("123456 "+remoteSession.absoluteRemoteRootPath+"/"+RemoteWorkSpace);
                    Session session = remoteSession.session;
                    session.setConfig("StrictHostKeyChecking", "no");
                    //ChannelExec channelExec = (ChannelExec) session.openChannel("exec");
                    try {
                        chmodFloder(session.openChannel("exec"), remoteSession.absoluteRemoteRootPath + "/" + RemoteWorkSpace);
                    } catch (JSchException e) {
                        throw new RuntimeException(e);
                    }
                    countDownLatch.countDown();
                }
            });

        }
        System.out.println("并行启动结束");
    }

    private void chmodFloder(Channel exec, String remoteFilePath) {
        try {
            //sudo chmod -R 777 /home/dennis/dockerfiles
            String command = "sudo chmod -R 777 " + remoteFilePath;
            //String command= "python3 hello.py";
            System.out.println("执行命令" + command + "的结果为：");
            ChannelExec channelExec = (ChannelExec) exec;
            channelExec.setCommand(command);
            InputStream in = channelExec.getInputStream();
            channelExec.connect();

            BufferedReader reader = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

//            int exitStatus = channelExec.getExitStatus();
//            if (exitStatus == 0) {
//                System.out.println("命令执行成功");
//            } else {
//                System.out.println("命令执行失败，exitStatus: " + exitStatus);
//            }

            channelExec.disconnect();
        } catch (JSchException | IOException e) {
            e.printStackTrace();
        }
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
            System.out.println("执行docker compose up");

            // 等待10秒钟！！！不然docker还未启动，就断开了连接
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

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
                Properties config = new Properties();
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
                map.put(user.getId(), new RemoteSession(session, remotePath));


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
                    downloadFromSSH(id, remoteSession);
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
                    uploadSSH(id, remoteSession);
                    // 每个线程下载完毕后，计数器减去1
                    countDownLatch.countDown();
                }
            });
        }
        System.out.println("sftp 并行上传完成");
    }


    private void downloadFromSSH(Integer id, RemoteSession remoteSession) {
        Session session = remoteSession.session;
        session.setConfig("StrictHostKeyChecking", "no");
        Channel channel = null;
        try {
            channel = session.openChannel("sftp");
            //System.out.println("111111sftp");
        } catch (JSchException e) {
            throw new RuntimeException(e);
        }
        try {
            // 本地对应远端的唯一路径，通过userid来区分
            String specialLocalPath = LocalFilePath + "/" + id;
            channel.connect();
            //System.out.println("在这里建立连接成功");
            ChannelSftp sftp = (ChannelSftp) channel;

            // 创建本地工作文件目录
            File file = new File(specialLocalPath);
            if (!file.exists() && !file.mkdir()) {
                throw new RuntimeException("创建本地文件夹失败！");
            }


            downloadYML(sftp, specialLocalPath, remoteSession.absoluteRemoteRootPath);

            downloadFolder(sftp, specialLocalPath, remoteSession.absoluteRemoteWorkPath);
        } catch (Throwable t) {
            t.printStackTrace();
        } finally {
            // 在finally块中确保关闭通道
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
    }

    private void downloadYML(ChannelSftp sftp, String localFilePath, String remoteFilePath) {
        try {
            String ymlName = "docker-compose.yml";
            String finalLocalpath = localFilePath + "/" + ymlName;
            String finalremotepath = remoteFilePath + "/" + ymlName;
            //System.out.println("1111111111" + finalremotepath);
            sftp.get(finalremotepath, finalLocalpath);
        } catch (Exception e) {

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
                        //System.out.println("000000000"+remoteRealPath);
                    } else {
                        System.out.println("跳过对 . 和 ..文件路径的处理！");
                    }
                } else {
                    // 文件！
                    // SFTP（SSH File Transfer Protocol）的get方法从远端服务器路径下拉取文件到本地路径
                    //System.out.println(remoteRealPath);
                    //System.out.println(localRealPath);
                    channelSftp.get(remoteRealPath, localRealPath);
                    System.out.println("拉取成功");
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void configUnify(Map<Integer, RemoteSession> map) {
        unifyTNodeInfo(map);
        unifyJson(map);
    }

    //整合tendermint节点信息
    private void unifyTNodeInfo(Map<Integer, RemoteSession> map) {
        try {
            // 1.session id
            // 2.container name
            //3. node info
            System.out.println("map:" + map);
            for (Map.Entry<Integer, RemoteSession> entry : map.entrySet()) {
                int session_id = entry.getKey();
                tendermintMap.put(session_id, new HashMap<>());


                String nodeRootPath = LocalFilePath + "/" + session_id;
                String localYMLPath = nodeRootPath + "/docker-compose.yml";
                InputStream inputStream = new FileInputStream(localYMLPath);
                Yaml yaml = new Yaml();
                Map<String, Map<String, Object>> data = yaml.load(inputStream);

                // 提取节点信息
                for (Map.Entry<String, Object> ymlEntry : data.get("services").entrySet()) {
                    Map<String, Object> serviceData = (Map<String, Object>) ymlEntry.getValue();
                    String container_name = (String) serviceData.get("container_name");
                    String node_ip = (String) ((Map<String, Object>) ((Map<String, Object>) serviceData.get("networks")).get("dennis_mynet")).get("ipv4_address");

                    List<String> ports = (List<String>) serviceData.get("ports");
                    String node_port = "";
                    for (String port : ports) {
                        String[] portParts = port.split(":");
                        String frontPort = portParts[0];
                        String endPort = portParts[1];
                        if ("26656".equals(endPort)) {
                            node_port = frontPort;
                        }
                    }
                    String node_id = "";
                    String nodeIDPath = nodeRootPath + "/" + container_name + "/nodeid.txt";
                    BufferedReader br = new BufferedReader(new FileReader(nodeIDPath));
                    node_id = br.readLine();


                    TNodeInfo tNodeInfo = new TNodeInfo(container_name, node_id, node_ip, node_port);
                    tendermintMap.get(session_id).put(container_name, tNodeInfo);

                }
            }

            System.out.println(tendermintMap);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //整合创世区块json文件
    private boolean unifyJson(Map<Integer, RemoteSession> map) {
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

    private void uploadSSH(Integer id, RemoteSession remoteSession) {
        Session session = remoteSession.session;
        Channel channel = null;
        try {
            channel = session.openChannel("sftp");
            //System.out.println("222222sftp");
        } catch (JSchException e) {
            throw new RuntimeException(e);
        }
        try {
            //System.out.println("~~~~~~~" + LocalFilePath + "/" + id);
            //System.out.println("~~~~~~~" + remoteSession.absoluteRemoteWorkPath);
            channel.connect();
            ChannelSftp sftp = (ChannelSftp) channel;

            String specialLocalPath = LocalFilePath + "/" + id;
            uploadFolder(sftp, specialLocalPath, remoteSession.absoluteRemoteWorkPath);
        } catch (Throwable t) {
            t.printStackTrace();

        } finally {
            // 在 finally 块中确保关闭通道
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
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

    /**
     * docker节点启动tendermint，部署集群
     *
     * @param countDownLatch
     */
    private void concurrentTendermintForSession(Map<Integer, RemoteSession> userSessionMap, CountDownLatch countDownLatch) {
        if (userSessionMap == null || userSessionMap.isEmpty()) {
            return;
        }
        String tendermintOrder = generateNodeInfoString(tendermintMap);
        System.out.println("开始启动tendermint集群");
        ThreadPoolExecutor startDockerThreadPoolExecutor = generateThreadPoolExecutor();

        for (Map.Entry<Integer, RemoteSession> entry : userSessionMap.entrySet()) {
            if (entry == null) {
                continue;
            }
            startDockerThreadPoolExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    //RemoteSession remoteSession = entry.getValue();
                    startTendermint(entry, tendermintOrder, tendermintMap);
                    countDownLatch.countDown();
                }
            });

        }
        System.out.println("启动tendermint集群结束");
    }

    //整合启动tendermint的tendermintOrder
    private String generateNodeInfoString(Map<Integer, Map<String, TNodeInfo>> tendermintMap) {
        StringBuilder stringBuilder = new StringBuilder();

        for (Map<String, TNodeInfo> sessionMap : tendermintMap.values()) {
            for (TNodeInfo tNodeInfo : sessionMap.values()) {
                String nodeInfoString = String.format("%s@%s:%s", tNodeInfo.getNodeId(), tNodeInfo.getNodeIp(), tNodeInfo.getNodePort());
                stringBuilder.append(nodeInfoString).append(",");
            }
        }

        // 删除末尾多余的逗号
        if (stringBuilder.length() > 0) {
            stringBuilder.deleteCharAt(stringBuilder.length() - 1);
        }

        return stringBuilder.toString();
    }

    /**
     * 启动tendermint
     */
    private void startTendermint(Map.Entry<Integer, RemoteSession> userSession, String tendermintOrder, Map<Integer, Map<String, TNodeInfo>> tendermintMap) {
        int sessionId = userSession.getKey();
        //String startOrder = "nohup ./tendermint node --p2p.persistent_peers=\"" + tendermintOrder + "\" > /dev/null 2>&1 &";
        String startOrder = "nohup ./tendermint node --p2p.persistent_peers=\"" + tendermintOrder + "\" --proxy_app=kvstore &";
        Map<String, TNodeInfo> sessionNodes = tendermintMap.get(sessionId);

        if (sessionNodes != null) {
            StringBuilder scriptContent = new StringBuilder("#!/bin/bash\n");
            for (Map.Entry<String, TNodeInfo> nodeEntry : sessionNodes.entrySet()) {
                String containerName = nodeEntry.getValue().containerName;
                String finalOrder = "docker exec -d " + containerName + " " + startOrder;
                scriptContent.append(finalOrder).append("\n");
            }

            try {
                // 保存脚本内容到本地文件
                String LocalShPath = LocalFilePath + "/start.sh";
                generateAndwriteSh(LocalShPath, scriptContent);

                //上传脚本到远端服务器
                ChannelSftp sftpChannel = null;
                try {
                    Channel channel = userSession.getValue().session.openChannel("sftp");
                    channel.connect();
                    sftpChannel = (ChannelSftp) channel;
                } catch (JSchException e) {
                    e.printStackTrace();
                }
                String RemoteShPath = userSession.getValue().absoluteRemoteRootPath + "/start.sh";
                uploadSh(LocalShPath, RemoteShPath, sftpChannel);

                // 在远程服务器上添加可执行权限后立即执行
                String executeScriptCommand = "chmod +x " + RemoteShPath + " && " + RemoteShPath;
                ChannelExec channelExec = (ChannelExec) userSession.getValue().session.openChannel("exec");
                channelExec.setCommand(executeScriptCommand);

                // 获取命令的输出流
                InputStream in = channelExec.getInputStream();
                // 执行命令
                channelExec.connect();
                // 读取输出流中的内容
                BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
                // 关闭连接
                channelExec.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("No nodes found for session ID: " + sessionId);
        }
    }

    private void generateAndwriteSh(String localFilePath, StringBuilder scriptContent) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(localFilePath))) {
            writer.write(scriptContent.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void uploadSh(String localFilePath, String remoteFilePath, ChannelSftp sftpChannel) {
        try {
            sftpChannel.put(localFilePath, remoteFilePath);
            sftpChannel.disconnect(); // 关闭SFTP通道
        } catch (SftpException e) {
            e.printStackTrace();
        }
    }

}



