package com.oddlycoder.facep2pv2.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

@WebFilter("*.xhtml")
public class AuthFilter implements Filter {

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  req;
        HttpServletResponse response = (HttpServletResponse) res;
        HttpSession         session  = request.getSession(false);

        String uri = request.getRequestURI();
        String ctx = request.getContextPath();

        boolean isPublic =
            uri.equals(ctx + "/login.xhtml")    ||
            uri.equals(ctx + "/register.xhtml") ||
            uri.contains("jakarta.faces.resource");

        boolean loggedIn = session != null && session.getAttribute("currentUser") != null;

        if (isPublic || loggedIn) {
            chain.doFilter(req, res);
        } else {
            response.sendRedirect(ctx + "/login.xhtml");
        }
    }
}
