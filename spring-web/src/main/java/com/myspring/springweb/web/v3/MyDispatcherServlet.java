package com.myspring.springweb.web.v3;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyDispatcherServlet extends HttpServlet {

    //application.yml 配置文件信息存储
    private final Properties contextConfig = new Properties();
    // 存放类名的map
    private final Map<String, Object> classNameMap = new HashMap<String, Object>();

    // 存放所有实例
    private final Map<String, Object> ioc = new HashMap<String, Object>();

    //    // 维护 url -> method 映射
//    private final Map<String, Method> handlerMapping = new HashMap<String, Method>();
    //保存所有的 Url 和方法的映射关系
    private List<Handler> handlerMapping = new ArrayList<Handler>();

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        //请求分发
        try {
            doDispatch(req, resp);
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取到请求的url
     * 取出 handlerMapping 的method
     * 反射调用
     *
     * @param req
     * @param resp
     */
    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) throws InvocationTargetException, IllegalAccessException {
        String requestURI = req.getRequestURI();
        String contextPath = req.getContextPath();
        requestURI = requestURI.replaceAll(contextPath, "").replaceAll("/+", "/");

        for (Handler handler : handlerMapping) {
            Matcher matcher = handler.pattern.matcher(requestURI);
            if (!matcher.matches()) {
                continue;
            }
            Class<?>[] parameterTypes = handler.method.getParameterTypes();
            //保存所有需要自动赋值的参数值
            Object[] paramValues = new Object[parameterTypes.length];
            Map<String, String[]> params = req.getParameterMap();
            for (Map.Entry<String, String[]> param : params.entrySet()) {
                String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]",
                        "").replaceAll(",\\s", ",");
                //如果找到匹配的对象，则开始填充参数值
                if(!handler.paramIndexMapping.containsKey(param.getKey())){continue;}
                int index = handler.paramIndexMapping.get(param.getKey());
                paramValues[index] = convert(parameterTypes[index],value);
            }

            //设置方法中的 request 和 response 对象
            int reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
            paramValues[reqIndex] = req;
            int respIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
            paramValues[respIndex] = resp;
            handler.method.invoke(handler.controller, paramValues);
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
                        Pattern pattern = Pattern.compile(baseUrl.replaceAll("/+", "/"));
                        // url 规范，设置url-> method 映射
                        handlerMapping.add(new Handler(pattern, instance, method));
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

    private class Handler {

        //请求处理对象
        private Object controller;

        //请求处理方法
        private Method method;

        protected Pattern pattern;
        //参数名称 --> 位置
        protected Map<String, Integer> paramIndexMapping; //参数顺序


        protected Handler(Pattern pattern, Object controller, Method method) {
            this.controller = controller;
            this.method = method;
            this.pattern = pattern;
            paramIndexMapping = new HashMap<String, Integer>();
            putParamIndexMapping(method);
        }

        private void putParamIndexMapping(Method method) {
            //获取添加了注解的 参数列表
            Annotation[][] parameterAnnotations = method.getParameterAnnotations();

            for (int i = 0; i < parameterAnnotations.length; i++) {
                for (Annotation a : parameterAnnotations[i]) {
                    if (a instanceof RequestParam) {
                        //参数名称
                        String paramName = ((RequestParam) a).value();
                        if (!"".equals(paramName.trim())) {
                            paramIndexMapping.put(paramName, i);
                        }
                    }
                }
            }

            //提取方法中的 request 和 response 参数
            Class<?>[] paramsTypes = method.getParameterTypes();
            for (int i = 0; i < paramsTypes.length; i++) {
                Class<?> type = paramsTypes[i];
                if (type == HttpServletRequest.class || type == HttpServletResponse.class) {
                    paramIndexMapping.put(type.getName(), i);
                }
            }
        }

    }

    private Object convert(Class<?> type,String value){
        if(Integer.class == type){
            return Integer.valueOf(value);
        }
        return value;
    }
}
