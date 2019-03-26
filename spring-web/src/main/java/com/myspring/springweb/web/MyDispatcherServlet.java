package com.myspring.springweb.web;

import com.myspring.springweb.annotations.MyController;
import com.myspring.springweb.annotations.MyService;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * DispatcherServlet
 * @author yj
 */
public class MyDispatcherServlet extends HttpServlet {

    private Map<String,Object> ioc = new HashMap<String,Object>();

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req,resp);
    }

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doDispatch(req,resp);
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) {

    }


    @Override
    public void init(ServletConfig servletConfig) throws ServletException {
        try {
        //加载配置文件
        String contextConfig = servletConfig.getInitParameter("contextConfigLocation");

        //读取配置文件
        InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(contextConfig);
        Properties properties = new Properties();
            //读取
            properties.load(resourceAsStream);
            //得到包扫描路径
            String scanPackage = properties.getProperty("scanPackage");
            //扫描路径下的文件
            scanBean(scanPackage);
            //遍历map 中的元素
            Set<String> keySet = ioc.keySet();
            for (String className: keySet) {
                if (!className.contains(".")) {continue;}
                //加载clazz
                Class<?> aClass = Class.forName(className);
                //@MyController 注解标注的类
                if (aClass.isAnnotationPresent(MyController.class)){
                    Object newInstance = aClass.newInstance();
                    //放入Map
                    ioc.put(className, newInstance);

                    //@MyService 标注的类
                }else if (aClass.isAnnotationPresent(MyService.class)) {

                    MyService myService = aClass.getAnnotation(MyService.class);
                    String beanName = myService.value();
                    if ("".equals(beanName)) {
                        beanName = aClass.getName();
                    }
                    ioc.put(beanName, aClass.newInstance());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        }

    }

    private void scanBean(String scanPackage) {
        //classpath 下面加载
        URL resource = this.getClass().getClassLoader().getResource("/"+scanPackage.replaceAll("\\.","/"));
        //包下文件
        File file = new File(resource.getFile());
        File[] files = file.listFiles();

        for (File f: files) {
            //第一层是jar 包
            if (f.isDirectory()) {
                scanBean(scanPackage + "."+ f.getName());
            }else {
                if (f.getName().endsWith(".class")) {
                    continue;
                }
                //得到className，存入map集合
                String className = scanPackage + "." + f.getName().replace(".class","");

                ioc.put(className,null);
            }
        }
    }
}
