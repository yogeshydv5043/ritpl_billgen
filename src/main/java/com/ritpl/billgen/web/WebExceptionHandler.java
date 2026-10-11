package com.ritpl.billgen.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;
import com.ritpl.billgen.service.ResourceNotFoundException;

@ControllerAdvice(basePackages = "com.ritpl.billgen.web")
public class WebExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ModelAndView notFound(ResourceNotFoundException ignored) {
        return new ModelAndView("error/404");
    }
}
