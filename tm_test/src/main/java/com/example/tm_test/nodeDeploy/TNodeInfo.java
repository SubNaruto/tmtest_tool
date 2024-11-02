package com.example.tm_test.nodeDeploy;

// tendermint 节点信息数据体
public class TNodeInfo {
    String containerName;
    String nodeID;
    String nodeIP;
    String nodePort;

    public TNodeInfo(String containerName, String nodeID, String nodeIP, String nodePort) {
        this.containerName = containerName;
        this.nodeID = nodeID;
        this.nodeIP = nodeIP;
        this.nodePort = nodePort;
    }

    @Override
    public String toString() {
        return "TNodeInfo{" +
                "containerName='" + containerName + '\'' +
                ", nodeID='" + nodeID + '\'' +
                ", nodeIP='" + nodeIP + '\'' +
                ", nodePort='" + nodePort + '\'' +
                '}';
    }

    public String getNodeId() {
        return  this.nodeID;
    }

    public String getNodeIp() {
        return this.nodeIP;
    }
    public String getNodePort(){
        return this.nodePort;
    }
}
