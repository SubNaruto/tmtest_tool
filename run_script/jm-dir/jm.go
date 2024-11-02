package main

import (
	"github.com/gin-gonic/gin"
	"os"
	"os/exec"
	"time"
)

func main() {
	engine := gin.Default()

	engine.POST("/jmeter", func(context *gin.Context) {

		filename := "/Users/duanjincheng/Desktop/tmtest_tool/run_script/jmeter_report/statistics.json"
		stat, err := os.Stat(filename)
		if err == nil && stat.Size() > 0 {
			cmd := exec.Command("cat", filename)
			data, err := cmd.Output()
			if err != nil {
				context.JSON(200, gin.H{
					"code": 1,
					"msg":  err.Error(),
				})
				return
			}

			context.JSON(200, gin.H{
				"code": 0,
				"msg":  string(data),
			})
			return
		}

		command := "/Users/duanjincheng/Desktop/tmtest_tool/run_script/jm_test.sh"
		cmd := exec.Command("/bin/bash", "-c", command)
		err = cmd.Run()
		if err != nil {
			context.JSON(200, gin.H{
				"code": 1,
				"msg":  err.Error(),
			})
			return
		}

		ticker := time.NewTimer(time.Second)
		defer ticker.Stop()

		for range ticker.C {
			stat, err := os.Stat(filename)

			if err == nil && stat.Size() > 0 {

				cmd = exec.Command("cat", filename)
				data, err := cmd.Output()
				if err != nil {
					context.JSON(200, gin.H{
						"code": 1,
						"msg":  err.Error(),
					})
					return
				}

				context.JSON(200, gin.H{
					"code": 0,
					"msg":  string(data),
				})

				break
			}
		}
	})

	if err := engine.Run(":9091"); err != nil {
		panic(err)
	}
}
