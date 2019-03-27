package com.myspring.springweb.service;

import com.myspring.springweb.annotations.MyService;

@MyService
public class HelloService {

    public String hello() {
        return "hello";
    }
}
