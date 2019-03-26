package com.myspring.springweb.web;

import com.myspring.springweb.annotations.MyController;
import com.myspring.springweb.annotations.MyService;
import com.myspring.springweb.annotations.RequestMapping;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DispatcherServlet
 * @author yj
 */
public class MyDispatcherServlet extends HttpServlet {

    private Map<String,Object> ioc = new ConcurrentHashMap<String,Object>();

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req,resp);
    }

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            doDispatch(req,resp);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws IOException, InvocationTargetException, IllegalAccessException {
        //获取到请求的地址
        String requestURI = req.getRequestURI();
        String contextPath = req.getContextPath();
        requestURI = requestURI.replace(contextPath, "").replaceAll("/+", "/");
        //如果请求的地址不在容器中返回 404
        if(!this.ioc.containsKey(requestURI)){resp.getWriter().write("404 Not Found!!");return;}

        //根据url 获取对应处理请求的方法
        Method method = (Method)this.ioc.get(requestURI);

        //得到class name
        String clazzName = method.getDeclaringClass().getName();
        Object targetClazz = this.ioc.get(clazzName);
        String reqParameter = req.getParameter("hello");
        //方法调用, new Object[]{req,resp,reqParameter} 方法参数列表
        method.invoke(targetClazz,new Object[]{req,resp,reqParameter});
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
                    //如果对象被 @RequestMapping 标注，得到请求的url
                    String baseUrl = "";
                    if (aClass.isAnnotationPresent(RequestMapping.class)){
                        RequestMapping requestMapping = aClass.getAnnotation(RequestMapping.class);
                        //得到baseUrl
                        baseUrl = requestMapping.value();

                    }
                    //遍历对象中的所有方法，看是否被 @RequestMapping 标注，如是 得到最后的请求地址
                    Method[] methods = aClass.getMethods();
                    for (Method method : methods) {
                        if (!method.isAnnotationPresent(RequestMapping.class)) {
                            continue;
                        }
                        RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
                        baseUrl = baseUrl +"/" + requestMapping.value();
                        ioc.put(baseUrl.replaceAll("/+","/"), method);
                    }



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
        URL url = this.getClass().getClassLoader().getResource("/" + scanPackage.replaceAll("\\.","/"));
        File classDir = new File(url.getFile());
        for (File file : classDir.listFiles()) {
            if(file.isDirectory()){ scanBean(scanPackage + "." +  file.getName());}else {
                if(!file.getName().endsWith(".class")){continue;}
                String clazzName = (scanPackage + "." + file.getName().replace(".class",""));
                ioc.put(clazzName,"");
            }
        }
    }
}
