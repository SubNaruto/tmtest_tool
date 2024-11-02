package com.example.tm_test.queryTest;

//import com.example.tm_test.queryTest.ScriptRequest;
import org.springframework.web.bind.annotation.*;
import java.io.*;

@CrossOrigin(origins = "http://localhost:8080")
@RestController
@RequestMapping("/api")
public class IdQuery {
    private static final String SCRIPT_PATH = "/Users/duanjincheng/Desktop/tmtest_tool/run_script/idquery.sh";

    @PostMapping("/idquery")
    public String runScript(@RequestBody ScriptRequest scriptRequest) {
        int id = scriptRequest.getId();

        // 修改脚本文件中的entityID值
        try {
            String scriptContent = readScriptFile();
            scriptContent = scriptContent.replaceAll("%22\\d+%22", "%22" + id + "%22");
            writeScriptFile(scriptContent);
        } catch (IOException e) {
            e.printStackTrace();
            return "Failed to update script file: " + e.getMessage();
        }

        try {
            ProcessBuilder processBuilder = new ProcessBuilder("bash", SCRIPT_PATH);
            processBuilder.redirectErrorStream(true);//将子进程的标准错误流合并到标准输出流中。通过标准输出流读取子进程的所有输出。
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));//读取子进程的标准输出流
            String line;
            StringBuilder output = new StringBuilder();//存储子进程的全部输出
            //循环读取子进程的输出流，在结尾加换行
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            int exitCode = process.waitFor();//待子进程执行完毕，获取子进程的退出码
            if (exitCode == 0) {
                printResult(output.toString());
                return output.toString();
            } else {
                String errorMessage="Script execution failed with exit code " + exitCode;
                printResult(errorMessage);
                return  errorMessage;
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return "Failed to run script: " + e.getMessage();
        }
    }

    private String readScriptFile() throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(SCRIPT_PATH));
        StringBuilder content = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            content.append(line).append("\n");
        }
        reader.close();
        return content.toString();
    }

    private void writeScriptFile(String content) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(SCRIPT_PATH));
        writer.write(content);
        writer.close();
    }
    public void printResult(String result) {
        System.out.println(result);
    }

}
