package com.ritpl.billgen.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.ModelAndView;
import com.ritpl.billgen.service.ResourceNotFoundException;

/**
 * Records that belong to another customer account are reported as "not found" so that ids cannot
 * be probed, and access that the role does not have at all is reported as forbidden.
 */
@ControllerAdvice(basePackages = "com.ritpl.billgen.web")
public class WebExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ModelAndView notFound(ResourceNotFoundException exception) {
        return new ModelAndView("error/404");
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ModelAndView forbidden(AccessDeniedException exception) {
        ModelAndView view = new ModelAndView("error/403");
        view.addObject("message", exception.getMessage());
        return view;
    }
}