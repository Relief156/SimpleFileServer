
# 简易文件下载站

> 一个开箱即用、零依赖的 Java 单文件 HTTP 下载服务。

## 快速开始

1. 下载打包好的可执行文件  
   ```
   xxx.jar
   ```

2. 启动服务  
   ```bash
   java -jar xxx.jar
   ```

3. 启动成功后，终端会提示类似下图，即代表服务已就绪：  
   ![启动成功提示](https://github.com/Relief156/SimpleFileServer/blob/main/docs/img/1.png)

4. 程序会在当前目录自动生成以下文件（夹）：  
   ![生成的文件](https://github.com/Relief156/SimpleFileServer/blob/main/docs/img/2.png)

   - `resources/`  
     存放静态资源，**请勿删除**。
   - `config.yml`  
     配置文件，示例内容如下：

     ```yaml
     # 监听端口
     port: 36090
     # 对外服务目录
     serve: public
     # 网站名称
     siteName: 我的文件站
     # 背景图片（支持本地路径或 URL）
     backgroundImage: /Resources/img/background_0.png
     ```

5. 把需要分享的文件放入 `public/` 目录，浏览器访问  
   ```
   http://<IP>:36090
   ```
   公网环境请自行开放对应端口。

## 预览

最终效果：  
![最终效果展示](https://github.com/Relief156/SimpleFileServer/blob/main/docs/img/3.png)

