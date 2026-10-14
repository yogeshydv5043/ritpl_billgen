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
 * be probed, access that the role does not have at all is reported as forbidden, and a request the
 * business rules rejected is answered with the reason the rule gave.
 */
@ControllerAdvice(basePackages = "com.ritpl.billgen.web")
public class WebExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ModelAndView notFound(ResourceNotFoundException exception) {
        return new ModelAndView("error/404");
    }

    /**
     * A request the services refused - an inverted date range, an export period outside the account's
     * own viewing window, a term the plan does not offer. These are answers, not crashes, and before this
     * handler they surfaced as a generic 500 error page, which told the customer nothing and made an
     * ordinary typo look like an outage.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ModelAndView rejected(IllegalArgumentException exception) {
        ModelAndView view = new ModelAndView("error/400");
        String message = exception.getMessage();
        view.addObject("message", message == null || message.isBlank()
                ? "That request could not be accepted." : message);
        return view;
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ModelAndView forbidden(AccessDeniedException exception) {
        ModelAndView view = new ModelAndView("error/403");
        view.addObject("message", exception.getMessage());
        return view;
    }
}