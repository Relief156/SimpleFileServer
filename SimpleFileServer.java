import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.file.*;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

public class SimpleFileServer {

    /* ========== 工具方法 ========== */
    private static void extractResource(String resourcePath, Path targetPath) throws IOException {
        URL resourceUrl = SimpleFileServer.class.getResource(resourcePath);
        if (resourceUrl == null) {
            System.err.println("[ERROR] 无法找到资源: " + resourcePath);
            return;
        }
        
        try (InputStream in = resourceUrl.openStream();
             ReadableByteChannel rbc = Channels.newChannel(in);
             FileOutputStream fos = new FileOutputStream(targetPath.toFile())) {
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        }
        System.out.println("[INFO] 已提取资源文件: " + targetPath);
    }

    /* ========== 入口 ========== */
    public static void main(String[] args) throws Exception {
        /* 1. 自动创建 config.yml */
        Path cfgPath = Paths.get("config.yml");
        if (!Files.exists(cfgPath)) {
            String defaultCfg = "# 修改监听端口\nport: 36090\n\n" +
                               "# 服务目录\nserve: public\n\n" +
                               "# 网站名称\nsiteName: 我的文件站\n\n" +
                               "# 背景图片\nbackgroundImage: /Resources/img/background_0.png";
            Files.write(cfgPath, defaultCfg.getBytes("UTF-8"));
            System.out.println("[INFO] 已自动生成默认配置文件: " + cfgPath.toAbsolutePath());
        }

        /* 2. 确保 Resources 目录存在并包含所需文件 */
        Path resourcesPath = Paths.get("Resources");
        if (!Files.exists(resourcesPath)) {
            Files.createDirectories(resourcesPath);
            System.out.println("[INFO] 已自动创建 Resources 目录: " + resourcesPath);
        }
        
        Path imgPath = resourcesPath.resolve("img");
        if (!Files.exists(imgPath)) {
            Files.createDirectories(imgPath);
            System.out.println("[INFO] 已自动创建 Resources/img 目录: " + imgPath);
        }
        
        // 检查并创建图片文件
        Path backgroundImgPath = imgPath.resolve("background_0.png");
        if (!Files.exists(backgroundImgPath)) {
            extractResource("/Resources/img/background_0.png", backgroundImgPath);
        }
        
        Path fileImgPath = imgPath.resolve("file.png");
        if (!Files.exists(fileImgPath)) {
            extractResource("/Resources/img/file.png", fileImgPath);
        }
        
        Path folderImgPath = imgPath.resolve("folder.png");
        if (!Files.exists(folderImgPath)) {
            extractResource("/Resources/img/folder.png", folderImgPath);
        }

        /* 3. 读取配置 */
        Config cfg = Config.load(cfgPath);

        /* 4. 确保 serve 目录存在 */
        Path root = Paths.get(cfg.serve).toAbsolutePath().normalize();
        if (!Files.exists(root)) {
            Files.createDirectories(root);
            System.out.println("[INFO] 已自动创建服务目录: " + root);
        }

        /* 4. 启动 HTTP 服务器 */
        HttpServer server = HttpServer.create(new InetSocketAddress(cfg.port), 0);
        server.createContext("/", new LoggingFileHandler(root, cfg));
        server.start();

        System.out.println("[INFO] 文件服务器已启动");
        System.out.println("[INFO] 访问: http://localhost:" + cfg.port);
        System.out.println("[INFO] 根目录: " + root);
        System.out.println("[INFO] 网站名称: " + cfg.siteName);
        if (cfg.backgroundImage != null) {
            System.out.println("[INFO] 背景图片: " + cfg.backgroundImage);
        }
    }

    /* ========== 配置对象 ========== */
    public static class Config {
        int port;
        String serve;
        String siteName;  // 网站名称配置
        String backgroundImage;  // 背景图片配置

        static Config load(Path p) throws IOException {
            Config c = new Config();
            try (BufferedReader br = Files.newBufferedReader(p)) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("port:")) c.port = Integer.parseInt(line.substring(5).trim());
                    else if (line.startsWith("serve:")) c.serve = line.substring(6).trim();
                    else if (line.startsWith("siteName:")) c.siteName = line.substring(9).trim(); // 读取网站名称
                    else if (line.startsWith("backgroundImage:")) c.backgroundImage = line.substring(16).trim(); // 读取背景图片
                }
            }
            // 设置默认网站名称
            if (c.siteName == null) {
                c.siteName = "文件站";
            }
            return c;
        }
    }

    /* ========== 带日志的文件处理器 ========== */
        static class LoggingFileHandler implements HttpHandler {
            private final Path root;
            private final String siteName;  // 存储网站名称
            private final String backgroundImage;  // 存储背景图片路径
            private static final DateTimeFormatter LOG_TIME =
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            private static final DateTimeFormatter FILE_TIME =
                    DateTimeFormatter.ofPattern("yyyy.M.d HH:mm:ss", Locale.getDefault());
            private static final DecimalFormat SIZE_FMT = new DecimalFormat("#,###");

        LoggingFileHandler(Path root, Config cfg) {
            this.root = root;
            this.siteName = cfg.siteName;  // 从配置初始化网站名称
            this.backgroundImage = cfg.backgroundImage;  // 从配置初始化背景图片路径
        }

        @Override
        public void handle(HttpExchange ex) throws IOException {
            long start = System.currentTimeMillis();
            String clientIp = ex.getRemoteAddress().getAddress().getHostAddress();
            String uriPath = ex.getRequestURI().getPath();

            /* 实际业务处理 */
            try {
                // 处理静态资源请求
                if (uriPath.startsWith("/Resources/")) {
                    Path resourcePath = Paths.get(".").resolve(uriPath.substring(1)).normalize();
                    if (Files.isRegularFile(resourcePath)) {
                        serveFile(ex, resourcePath);
                        log(clientIp, uriPath, 200, System.currentTimeMillis() - start);
                        return;
                    } else {
                        serve404(ex);
                        log(clientIp, uriPath, 404, System.currentTimeMillis() - start);
                        return;
                    }
                }
                
                Path target = root.resolve("." + uriPath).normalize();
                if (!target.startsWith(root)) {   // 防穿越
                    serve404(ex);
                    log(clientIp, uriPath, 404, System.currentTimeMillis() - start);
                    return;
                }

                if (Files.isDirectory(target)) {
                    listDirectory(ex, target, uriPath);
                    log(clientIp, uriPath, 200, System.currentTimeMillis() - start);
                } else if (Files.isRegularFile(target)) {
                    serveFile(ex, target);
                    log(clientIp, uriPath, 200, System.currentTimeMillis() - start);
                } else {
                    serve404(ex);
                    log(clientIp, uriPath, 404, System.currentTimeMillis() - start);
                }
            } catch (Exception e) {
                serve500(ex);
                // 忽略对外部URL的请求日志
                if (uriPath.contains(backgroundImage)) {
                    return;
                }
                log(clientIp, uriPath, 500, System.currentTimeMillis() - start);
            }
        }

        /* ---------- 日志输出 ---------- */
        private void log(String ip, String path, int status, long cost) {
            // 忽略对静态资源的请求日志
            if (path.startsWith("/Resources/")) {
                return;
            }
            
            String time = LocalDateTime.now().format(LOG_TIME);
            double costInSeconds = cost / 1000.0;
            System.out.printf("[%s] %d | %-15s | %s (%.3fs)%n",
                    time, status, ip, path, costInSeconds);
        }

        /* ---------- 目录列表 ---------- */
        private void listDirectory(HttpExchange ex, Path dir, String uriPath) throws IOException {
            // 获取排序参数
            String sortBy = "name";
            String order = "asc";
            String query = ex.getRequestURI().getQuery();
            if (query != null) {
                String decodedQuery = URLDecoder.decode(query, "UTF-8");
                for (String param : decodedQuery.split("&")) {
                    String[] kv = param.split("=");
                    if (kv.length == 2) {
                        if ("sort".equals(kv[0])) sortBy = kv[1];
                        if ("order".equals(kv[0])) order = kv[1];
                    }
                }
            }

            // 收集文件信息
            List<FileItem> items = new ArrayList<>();
            try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
                for (Path p : ds) {
                    FileItem item = new FileItem();
                    item.name = p.getFileName().toString();
                    item.isDirectory = Files.isDirectory(p);
                    item.size = item.isDirectory ? 0 : Files.size(p);
                    item.lastModified = Files.getLastModifiedTime(p).toMillis();
                    items.add(item);
                }
            }

            // 排序处理
            Comparator<FileItem> comparator = null;
            switch (sortBy) {
                case "size":
                    comparator = Comparator.comparingLong(FileItem::getSize);
                    break;
                case "date":
                    comparator = Comparator.comparingLong(FileItem::getLastModified);
                    break;
                default: // name
                    comparator = Comparator.comparing(FileItem::getName, String.CASE_INSENSITIVE_ORDER);
            }

            // 目录优先排序
            comparator = Comparator.comparingInt((FileItem item) -> item.isDirectory ? 0 : 1)
                    .thenComparing(comparator);

            if ("desc".equals(order)) {
                comparator = comparator.reversed();
            }

            items.sort(comparator);

            // 构建HTML
            StringBuilder sb = new StringBuilder();
            sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><title>")
              .append(siteName).append(" ").append(uriPath).append("</title>")
              .append("<style>body{font-family:'Segoe UI',Arial,sans-serif;margin:0;padding:40px;background-color:#f5f5f5;")
              .append("background-image:url('").append(backgroundImage).append("');")
              .append("background-size:cover;background-repeat:no-repeat;background-attachment:fixed;overflow:hidden;}")
              .append(".container{background-color:rgba(255,255,255,0.9);padding:20px;border-radius:8px;box-shadow:0 2px 10px rgba(0,0,0,0.1);")
              .append("max-width:1200px;margin:0 auto;max-height:85vh;overflow-y:auto;height:85vh;}")
              .append("h1{color:#333;margin-top:0;font-size:24px;}")
              .append("table{border-collapse:collapse;width:100%;margin-top:20px;}")
              .append("th,td{padding:12px 15px;text-align:left;border-bottom:1px solid #eee;}")
              .append("th{background-color:#f8f9fa;font-weight:600;color:#555;}")
              .append("tr:hover{background-color:#f8f9fa;}")
              .append("a{color:#0066cc;text-decoration:none;display:inline-flex;align-items:center;}")
              .append("a:hover{text-decoration:underline;}")
              .append(".icon{width:16px;height:16px;margin-right:8px;}")
              .append(".btn{display:inline-block;padding:6px 12px;margin:0 5px;font-size:14px;font-weight:400;line-height:1.42857143;")
              .append("text-align:center;white-space:nowrap;vertical-align:middle;cursor:pointer;border:1px solid transparent;")
              .append("border-radius:4px;background-color:#f0f0f0;color:#333;text-decoration:none;}")
              .append(".btn:hover{background-color:#e0e0e0;}")
              .append(".btn-primary{background-color:#007bff;color:#fff;}")
              .append(".btn-primary:hover{background-color:#0056b3;}")
              .append(".btn-success{background-color:#28a745;color:#fff;}")
              .append(".btn-success:hover{background-color:#1e7e34;}")
              .append(".sortable{cursor:pointer;text-decoration:underline;}")
              .append(".actions{white-space:nowrap;}")
              .append("</style>")
              .append("<script>")
              .append("function toggleSort(field) {")
              .append("  const url = new URL(window.location.href);")
              .append("  const params = new URLSearchParams(url.search);")
              .append("  if (params.get('sort') === field) {")
              .append("    params.set('order', params.get('order') === 'asc' ? 'desc' : 'asc');")
              .append("  } else {")
              .append("    params.set('sort', field);")
              .append("    params.set('order', 'asc');")
              .append("  }")
              .append("  url.search = params.toString();")
              .append("  window.location.href = url.toString();")
              .append("}")
              .append("function copyLink(path) {")
              .append("  const fullUrl = window.location.origin + path;")
              .append("  if (navigator.clipboard && window.isSecureContext) {")
              .append("    navigator.clipboard.writeText(fullUrl).then(() => {")
              .append("      alert('链接已复制到剪贴板');")
              .append("    }).catch(err => {")
              .append("      console.error('复制失败: ', err);")
              .append("      fallbackCopyTextToClipboard(fullUrl);")
              .append("    });")
              .append("  } else {")
              .append("    fallbackCopyTextToClipboard(fullUrl);")
              .append("  }")
              .append("}")
              .append("function fallbackCopyTextToClipboard(text) {")
              .append("  const textArea = document.createElement('textarea');")
              .append("  textArea.value = text;")
              .append("  textArea.style.position = 'fixed';")
              .append("  textArea.style.left = '-999999px';")
              .append("  textArea.style.top = '-999999px';")
              .append("  document.body.appendChild(textArea);")
              .append("  textArea.focus();")
              .append("  textArea.select();")
              .append("  try {")
              .append("    const successful = document.execCommand('copy');")
              .append("    if (successful) {")
              .append("      alert('链接已复制到剪贴板');")
              .append("    } else {")
              .append("      console.error('复制失败');")
              .append("    }")
              .append("  } catch (err) {")
              .append("    console.error('复制失败: ', err);")
              .append("  }")
              .append("  document.body.removeChild(textArea);")
              .append("}")
              .append("</script>")
              .append("</head><body><div class=\"container\"><h1>").append(siteName).append(" ").append(uriPath)
              .append("</h1><table><tr>")
              .append("<th onclick=\"toggleSort('name')\" class=\"sortable\">文件名")
              .append(sortBy.equals("name") ? (" (".concat("asc".equals(order) ? "↑" : "↓").concat(")")) : "")
              .append("</th>")
              .append("<th onclick=\"toggleSort('size')\" class=\"sortable\">大小")
              .append(sortBy.equals("size") ? (" (".concat("asc".equals(order) ? "↑" : "↓").concat(")")) : "")
              .append("</th>")
              .append("<th onclick=\"toggleSort('date')\" class=\"sortable\">更新日期")
              .append(sortBy.equals("date") ? (" (".concat("asc".equals(order) ? "↑" : "↓").concat(")")) : "")
              .append("</th><th>操作</th></tr>");

            // 父目录链接
            if (!dir.equals(root)) {
                sb.append("<tr><td><a href=\"../\">../</a></td><td>-</td><td>-</td></tr>");
            }

            // 文件列表
            for (FileItem item : items) {
                String link = item.name + (item.isDirectory ? "/" : "");
                String size = item.isDirectory ? "-" : formatSize(item.size);
                String time = FILE_TIME.format(LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(item.lastModified), java.time.ZoneId.systemDefault()));
                String icon = item.isDirectory ? "folder.png" : "file.png";
                sb.append("<tr><td><a href=\"").append(link).append("\">")
                  .append("<img src=\"/Resources/img/").append(icon).append("\" alt=\"\" class=\"icon\">")
                  .append(link).append("</a></td><td>").append(size)
                  .append("</td><td>").append(time).append("</td>")
                  .append("<td class=\"actions\">");
                if (!item.isDirectory) {
                    sb.append("<a href=\"").append(link).append("\" class=\"btn btn-primary\">下载</a>");
                    String path = uriPath + link;
                    sb.append("<button class=\"btn btn-success\" onclick=\"copyLink('" + path + "')\">复制链接</button>");
                }
                sb.append("</td></tr>");
            }
            sb.append("</table></div></body></html>");
            sendHtml(ex, sb.toString());
        }

        /* ---------- 文件下载 ---------- */
        private void serveFile(HttpExchange ex, Path file) throws IOException {
            String mime = Files.probeContentType(file);
            if (mime == null) mime = "application/octet-stream";
            ex.getResponseHeaders().set("Content-Type", mime);
            
            // 为静态资源添加缓存头
            if (file.toString().contains("Resources")) {
                // 设置缓存头，让浏览器缓存资源1小时
                ex.getResponseHeaders().set("Cache-Control", "public, max-age=3600");
                ex.getResponseHeaders().set("Expires", java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(
                    java.time.ZonedDateTime.now().plusHours(1)));
            }
            
            ex.sendResponseHeaders(200, Files.size(file));
            try (OutputStream os = ex.getResponseBody()) {
                Files.copy(file, os);
            }
        }

        /* ---------- 错误页面 ---------- */
        private void serve404(HttpExchange ex) throws IOException {
            String html = "<h1>404 Not Found</h1>";
            ex.sendResponseHeaders(404, html.length());
            try (OutputStream os = ex.getResponseBody()) {
                os.write(html.getBytes("UTF-8"));
            }
        }
        private void serve500(HttpExchange ex) throws IOException {
            String html = "<h1>500 Internal Server Error</h1>";
            ex.sendResponseHeaders(500, html.length());
            try (OutputStream os = ex.getResponseBody()) {
                os.write(html.getBytes("UTF-8"));
            }
        }

        /* ---------- 工具 ---------- */
        private void sendHtml(HttpExchange ex, String html) throws IOException {
            byte[] bytes = html.getBytes("UTF-8");
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        }
        private String formatSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            int exp = (int) (Math.log(bytes) / Math.log(1024));
            String pre = "KMGTPE".charAt(exp - 1) + "";
            return SIZE_FMT.format(bytes / Math.pow(1024, exp)) + " " + pre + "B";
        }
        
        /* ---------- 文件项辅助类 ---------- */
        private static class FileItem {
            String name;
            boolean isDirectory;
            long size;
            long lastModified;
            
            String getName() { return name; }
            long getSize() { return size; }
            long getLastModified() { return lastModified; }
        }
    }
}