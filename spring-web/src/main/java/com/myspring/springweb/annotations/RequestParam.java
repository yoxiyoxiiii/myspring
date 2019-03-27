package com.myspring.springweb.annotations;

import java.lang.annotation.*;

@Target({ElementType.PARAMETER})
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface RequestParam {
    /**
     * 参数名称
     * @return
     */
    String value() default "";

}
