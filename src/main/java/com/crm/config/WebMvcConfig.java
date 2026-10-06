package com.crm.config;

import com.crm.interceptor.AdminIpInterceptor;
import com.crm.interceptor.AuthInterceptor;
import com.crm.interceptor.CsrfInterceptor;
import com.crm.interceptor.MediaAuthInterceptor;
import com.crm.interceptor.RoleInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final CsrfInterceptor csrfInterceptor;
    private final MediaAuthInterceptor mediaAuthInterceptor;
    private final RoleInterceptor roleInterceptor;
    private final AdminIpInterceptor adminIpInterceptor;

    public WebMvcConfig(AuthInterceptor authInterceptor,
                        CsrfInterceptor csrfInterceptor,
                        MediaAuthInterceptor mediaAuthInterceptor,
                        RoleInterceptor roleInterceptor,
                        AdminIpInterceptor adminIpInterceptor) {
        this.authInterceptor = authInterceptor;
        this.csrfInterceptor = csrfInterceptor;
        this.mediaAuthInterceptor = mediaAuthInterceptor;
        this.roleInterceptor = roleInterceptor;
        this.adminIpInterceptor = adminIpInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // セキュリティ設定 › 管理画面IP許可設定: an IP off the list never reaches the admin screens (login included).
        registry.addInterceptor(adminIpInterceptor).addPathPatterns("/manager", "/manager/**");
        // CSRF runs first so it can reject state-changing requests before auth work kicks in.
        registry.addInterceptor(csrfInterceptor);
        registry.addInterceptor(authInterceptor).addPathPatterns("/manager/**");
        // 権限: hidden menus, masking, sidebar color — needs the session AuthInterceptor checked.
        registry.addInterceptor(roleInterceptor).addPathPatterns("/manager/**");
        // /media/** is the public agency dashboard — Basic Auth, no admin session.
        registry.addInterceptor(mediaAuthInterceptor).addPathPatterns("/media/**");
    }
}
