package com.myspring.springweb.annotations;

import java.lang.annotation.*;

@Target(ElementType.FIELD)
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface MyAutowired {

    String value() default "";
}
