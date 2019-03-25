package com.myspring.springweb.annotations;

import java.lang.annotation.*;

/**
 * 自定义注解
 */
@Target(ElementType.TYPE)
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface MyController {
    String value() default "";
}
