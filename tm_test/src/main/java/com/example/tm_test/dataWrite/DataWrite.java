package com.example.tm_test.dataWrite;

import org.springframework.web.bind.annotation.*;
import java.io.*;

@CrossOrigin(origins = "http://localhost:8080")
@RestController
@RequestMapping("/api")
public class DataWrite {

    private static final String SCRIPT_PATH = "/Users/duanjincheng/Desktop/tmtest_tool/run_script/write.sh";

    @PostMapping("/write")
    public String runScript(@RequestBody ScriptRequest scriptRequest) {
        int h = scriptRequest.getH();

        // 修改脚本文件中的-h值
        try {
            String scriptContent = readScriptFile();
            scriptContent = scriptContent.replaceAll("-h=\\d+", "-h=" + h);
            writeScriptFile(scriptContent);
        } catch (IOException e) {
            e.printStackTrace();
            return "Failed to update script file: " + e.getMessage();
        }

        // 执行脚本文件
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("bash", SCRIPT_PATH);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            StringBuilder output = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return output.toString();
            } else {
                return "Script execution failed with exit code " + exitCode;
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
}


