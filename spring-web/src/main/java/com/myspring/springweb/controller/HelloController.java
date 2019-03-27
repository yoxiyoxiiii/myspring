package com.myspring.springweb.controller;

import com.myspring.springweb.annotations.MyAutowired;
import com.myspring.springweb.annotations.MyController;
import com.myspring.springweb.annotations.RequestMapping;
import com.myspring.springweb.annotations.RequestParam;
import com.myspring.springweb.service.HelloService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@MyController
public class HelloController {

    @MyAutowired
    private HelloService helloService;


    @RequestMapping(value = "/hello")
    public void hello(HttpServletRequest request ,
                        HttpServletResponse response,
                        @RequestParam(name = "hello") String hello) throws IOException {
        String s = helloService.hello();
        System.out.println(s);
        response.getWriter().write(hello + " :" + s);
    }
}
