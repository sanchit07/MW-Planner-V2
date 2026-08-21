package com.mw.planner.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    servers = {@Server(url = "/")},
    info =
        @Info(
            title = "MovingWalls Planner",
            version = "1.0.0",
            description = "Backend APIs for MovingWalls Planner"))
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    bearerFormat = "JWT",
    scheme = "bearer")
@SecurityScheme(
    name = "basicAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "basic",
    description =
        "HTTP Basic auth. Use management credentials from application config (e.g. admin / your password) for Management APIs.")
public class OpenAPIConfig {}
