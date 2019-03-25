package com.myspring.springweb.annotations;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface MyService {
    String value() default "";
}
