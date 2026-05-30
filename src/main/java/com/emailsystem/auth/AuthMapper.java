package com.emailsystem.auth;

import com.emailsystem.auth.dto.AuthResponse;
import com.emailsystem.user.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AuthMapper {

    @Mapping(target = "token", source = "token")
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "fullname", source = "user.fullname")
    @Mapping(target = "email", source = "user.email")
    AuthResponse toResponse(User user, String token);
}
