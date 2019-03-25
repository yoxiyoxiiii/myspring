package com.myspring.springweb.controller;

import com.myspring.springweb.annotations.MyController;
import com.myspring.springweb.annotations.RequestMapping;
import com.myspring.springweb.annotations.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@MyController
public class HelloController {


    @RequestMapping
    public void hello(HttpServletRequest request ,
                        HttpServletResponse response,
                        @RequestParam(name = "hello") String hello) throws IOException {
        response.getWriter().write(hello);
    }
}
