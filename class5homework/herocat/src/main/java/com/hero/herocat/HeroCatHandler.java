package com.hero.herocat;

/**
 * * HeroCat服务端处理器
 * 1）从用户请求URI中解析出要访问的Servlet名称
 * 2）从nameToServletMap中查找是否存在该名称的key。若存在，则直接使用该实例，否则执
 * <p>
 * 行第3）步
 * <p>
 * <p>
 * 3）从nameToClassNameMap中查找是否存在该名称的key，若存在，则获取到其对应的全限定
 * 性类名，
 * <p>
 * <p>
 * 使用反射机制创建相应的serlet实例，并写入到nameToServletMap中，若不存在，则直
 * 接访问默认Servlet
 */

import com.hero.servlet.HeroRequest;
import com.hero.servlet.HeroResponse;
import com.hero.servlet.HeroServlet;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpRequest;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Map;

public class HeroCatHandler extends ChannelInboundHandlerAdapter {
    private Map<String, HeroServlet> nameToServletMap;
    private Map<String, String> nameToClassNameMap;


    public HeroCatHandler(Map<String, HeroServlet> nameToServletMap,
                          Map<String, String> nameToClassNameMap) {
        this.nameToServletMap = nameToServletMap;
        this.nameToClassNameMap = nameToClassNameMap;

    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws
            Exception {
        //System.out.println("object received is " + msg.toString());
        if (msg instanceof HttpRequest) {
            HttpRequest request = (HttpRequest) msg;
            String uri = request.uri().toString();
            // 从请求中解析出要访问的Servlet名称
            //aaa/bbb/twoservlet?name=aa

            if (uri == null || uri.lastIndexOf("/") == -1) {
                // EmptyLastHttpContent
                ctx.close();
                return;
            }
            String lastPortionUri = uri.substring(uri.lastIndexOf("/") + 1);
            if (uri.lastIndexOf("?") == -1 && lastPortionUri.lastIndexOf(".") == -1) {
                ctx.close();
                return;
            }

            if (lastPortionUri == "" || (uri.lastIndexOf("?") == -1 && lastPortionUri.lastIndexOf(".") != -1)) {
                // static content
                HeroResponse res = new HttpHeroResponse(request, ctx);
                if (!request.method().name().equalsIgnoreCase("GET")) {
                    System.out.println("501 Not Implemented : " + request.method().name() + " method.");
                    res.write("501 Not Implemented : " + request.method().name());
                } else {
                    // GET or HEAD method
                    String fileRequested = "index.html";
                    if (lastPortionUri == "" || lastPortionUri.endsWith("/")) {
                        fileRequested = "index.html";
                    } else {
                        fileRequested = lastPortionUri;
                    }

                    File file = null;
                    URL resource = getClass().getClassLoader().getResource(fileRequested);
                    if (resource != null) {
                        file = new File(resource.toURI());
                    }
                    if (file == null) {
                        ctx.close();
                        return;

                    }

                    int fileLength = (int) file.length();
                    String type = getContentType(fileRequested);
                    res.write(readFileData(file, fileLength), type);

                }
                ctx.close();
                return;
            }

            // servlet
            String servletName = uri.substring(uri.lastIndexOf("/") + 1,
                    uri.indexOf("?"));

            HeroServlet heroServlet = new DefaultHeroServlet();
            if (nameToServletMap.containsKey(servletName)) {
                heroServlet = nameToServletMap.get(servletName);
            } else if (nameToClassNameMap.containsKey(servletName)) {
                // double-check，双重检测锁
                if (nameToServletMap.get(servletName) == null) {

                    synchronized (this) {

                        if (nameToServletMap.get(servletName) == null) {
                            // 获取当前Servlet的全限定性类名
                            String className =
                                    nameToClassNameMap.get(servletName);
                            // 使用反射机制创建Servlet实例
                            heroServlet = (HeroServlet)
                                    Class.forName(className).newInstance();
                            // 将Servlet实例写入到nameToServletMap
                            nameToServletMap.put(servletName, heroServlet);
                        }
                    }
                }
            } // end-else if

            // 代码走到这里，servlet肯定不空
            HeroRequest req = new HttpHeroRequest(request);
            HeroResponse res = new HttpHeroResponse(request, ctx);
            // 根据不同的请求类型，调用heroServlet实例的不同方法
            if (request.method().name().equalsIgnoreCase("GET")) {

                heroServlet.doGet(req, res);

            } else if (request.method().name().equalsIgnoreCase("POST")) {

                heroServlet.doPost(req, res);
            }
            ctx.close();


        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause)
            throws Exception {
        cause.printStackTrace();
        ctx.close();

    }

    private byte[] readFileData(File file, int fileLength) throws IOException {
        FileInputStream fileIn = null;
        byte[] fileData = new byte[fileLength];
        if (fileLength == 0) {
            return fileData;
        }

        try {
            fileIn = new FileInputStream(file);
            fileIn.read(fileData);
        } finally {
            if (fileIn != null)
                fileIn.close();
        }

        return fileData;
    }

    private String getContentType(String fileRequested) {
        if (fileRequested.endsWith(".htm") || fileRequested.endsWith(".html"))
            return "text/html";
        else
            return "text/plain";
    }

}