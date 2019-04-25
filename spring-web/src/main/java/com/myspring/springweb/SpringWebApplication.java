package com.myspring.springweb;


import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringWebApplication {

	public static void main(String[] args) {
		ApplicationContext webApplicationContext = new ClassPathXmlApplicationContext("applicationContext.xml");
	}

}
