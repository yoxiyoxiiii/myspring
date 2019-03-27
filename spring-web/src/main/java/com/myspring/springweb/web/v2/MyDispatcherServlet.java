package com.myspring.springweb.web.v2;

import com.myspring.springweb.annotations.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class MyDispatcherServlet extends HttpServlet {

    //application.yml 配置文件信息存储
    private final Properties contextConfig = new Properties();
    // 存放类名的map
    private final Map<String, Object> classNameMap = new HashMap<String, Object>();

    // 存放所有实例
    private final Map<String, Object> ioc = new HashMap<String, Object>();

    // 维护 url -> method 映射
    private final Map<String, Method> handlerMapping = new HashMap<String, Method>();

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        //请求分发
        doDispatch(req, resp);
    }

    /**
     * 获取到请求的url
     * 取出 handlerMapping 的method
     * 反射调用
     *
     * @param req
     * @param resp
     */
    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) {
        String requestURI = req.getRequestURI();
        String contextPath = req.getContextPath();
        requestURI = requestURI.replaceAll(contextPath, "").replaceAll("/+", "/");

        if (!this.handlerMapping.containsKey(requestURI)) {
            try {
                resp.getWriter().write("404 Not Found!!");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }

        Method method = this.handlerMapping.get(requestURI);

        //请求参数
        Map<String, String[]> params = req.getParameterMap();

        //获取该方法的声明参数列表
        Class<?>[] parameterTypesMethod = method.getParameterTypes();

        //保存赋值参数的位置
        Object[] paramValues = new Object[parameterTypesMethod.length];

        for (int i = 0; i < parameterTypesMethod.length; i++) {
            Class<?> parameterType = parameterTypesMethod[i];
            //当前位置 是 HttpServletRequest 类型参数
            if (HttpServletRequest.class == parameterType) {
                paramValues[i] = req;
                continue;
            }
            // 当前位置是 HttpServletResponse 类型参数
            if (HttpServletResponse.class == parameterType) {
                paramValues[i] = resp;
                continue;
            }
            //其他参数设置
            if (String.class == parameterType) {
                Annotation[][] parameterAnnotations = method.getParameterAnnotations();
                for (int j = 0; j < parameterAnnotations.length; j++) {
                    for (Annotation a : parameterAnnotations[j]) {
                        if (a instanceof RequestParam) {
                            String paramName = ((RequestParam) a).value();
                            if (!"".equals(paramName.trim())) {
                                for (Map.Entry<String, String[]> param : params.entrySet()) {
                                    String value = Arrays.toString(param.getValue())
                                            .replaceAll("\\[|\\]", "")
                                            .replaceAll("\\s", ",");
                                    paramValues[i] = value;
                                }
                            }
                        }
                    }

                }
            }
        }

        //得到该方法所在的类
        String simpleName = method.getDeclaringClass().getSimpleName();

        String clazz = toLowerFirstCase(simpleName);

        Object instance = ioc.get(clazz);
        try {
            method.invoke(instance, paramValues);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }

    }

    /**
     * 初始化方法
     *
     * @param config
     * @throws ServletException
     */
    @Override
    public void init(ServletConfig config) throws ServletException {

        // 1.读取配置文件
        loadConfig(config.getInitParameter("contextConfigLocation"));
        //2. 类扫描,将所有扫描到的类放入map 中
        doScanner(contextConfig.getProperty("scanPackage"));
        // 3. 初始化map 中的类, 设置bean 的 name
        initBean();
        // 4 设置依赖注入
        doAutowired();
        // 5 设置url -> method 的映射
        initHandlerMapping();
    }

    /**
     * 给controller 中的方法配置请求路径
     */
    private void initHandlerMapping() {

        Set<Map.Entry<String, Object>> entries = ioc.entrySet();
        for (Map.Entry entry : entries) {
            Object instance = entry.getValue();
            Class<?> aClass = instance.getClass();
            boolean annotationPresent = aClass.isAnnotationPresent(MyController.class);
            //只处理Controller
            if (annotationPresent) {
                boolean requestMapping = aClass.isAnnotationPresent(RequestMapping.class);
                String baseUrl = "";
                // 判断Controller 是否使用在类上面
                if (requestMapping) {
                    RequestMapping annotation = aClass.getAnnotation(RequestMapping.class);
                    baseUrl = annotation.value();
                }
                Method[] methods = aClass.getMethods();
                for (Method method : methods) {
                    boolean methodAnnotationPresent = method.isAnnotationPresent(RequestMapping.class);
                    if (methodAnnotationPresent) {
                        baseUrl = baseUrl + "/" + method.getAnnotation(RequestMapping.class).value();
                        // url 规范，设置url-> method 映射
                        handlerMapping.put(baseUrl.replaceAll("/+", "/"), method);
                    }

                }

            }
        }
    }


    private void doAutowired() {
        Set<Map.Entry<String, Object>> entries = ioc.entrySet();
        for (Map.Entry entry : entries) {
            //需要设置依赖注入的对象
            Object clazz = entry.getValue();
            //得到 对象所有的属性
            Field[] declaredFields = clazz.getClass().getDeclaredFields();
            for (Field field : declaredFields) {
                boolean annotationPresent = field.isAnnotationPresent(MyAutowired.class);
                // 只为 MyAutowired 标注的 属性设置依赖注入
                if (annotationPresent) {
                    MyAutowired myAutowired = field.getAnnotation(MyAutowired.class);
                    String beanName = myAutowired.value().trim();
                    if ("".equals(beanName)) {
                        beanName = field.getType().getSimpleName();
                    }
                    Object instance = ioc.get(toLowerFirstCase(beanName));
                    field.setAccessible(true);
                    try {
                        //设置依赖注入
                        field.set(clazz, instance);
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

    }

    /**
     * 初始化map 中的bean
     */
    private void initBean() {
        Set<String> keySet = classNameMap.keySet();
        for (String className : keySet) {
            try {
                Class<?> aClass = Class.forName(className);
                if (aClass.isAnnotationPresent(MyController.class)) {
                    String simpleName = aClass.getSimpleName();
                    //类名首字母小写 HelloController -> helloController
                    String beanName = toLowerFirstCase(simpleName);
                    Object instance = aClass.newInstance();
                    ioc.put(beanName, instance);
                }
                if (aClass.isAnnotationPresent(MyService.class)) {
                    String beanName = toLowerFirstCase(aClass.getSimpleName());
                    MyService myService = aClass.getAnnotation(MyService.class);
                    String value = myService.value();
                    if (!"".equals(value)) {
                        beanName = value;
                    }
                    ioc.put(beanName, aClass.newInstance());
                }

            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InstantiationException e) {
                e.printStackTrace();
            }
        }
    }

    private String toLowerFirstCase(String simpleName) {
        char[] chars = simpleName.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }


    /**
     * @param scanPackage
     */
    private void doScanner(String scanPackage) {
        // com.myspring.springweb --> com/myspring/springweb
        String path = scanPackage.replaceAll("\\.", "/");
        //得到 class 文件所在的绝对路径
        URL resource = this.getClass().getClassLoader().getResource(path);
        //得到 class 文件所在的绝对路径 -> /E:/workspace/myspring/spring-web/target/classes/com/myspring/springweb
        String resourceFile = resource.getFile();

        File clazzPathFile = new File(resourceFile);
        //获取根目录下的 子目录文件
        File[] files = clazzPathFile.listFiles();
        for (File file : files) {
            if (file.isDirectory()) {
                //如果当前是文件夹 ，递归 进入
                doScanner(scanPackage + "." + file.getName());
            }
            if (!file.getName().endsWith(".class")) {
                continue;
            }
            String replace = file.getName().replace(".class", "");
            //得到类的全面
            String classFullName = scanPackage + "." + replace;
            //将该类存入map 中
            classNameMap.put(classFullName, "");
        }

    }

    /**
     * 加载配置信息
     *
     * @param contextConfigLocation
     */
    private void loadConfig(String contextConfigLocation) {
        InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            contextConfig.load(resourceAsStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
