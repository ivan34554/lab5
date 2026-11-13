package com.example.lab5.controller;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.example.lab5.dto.UserDto;
import com.example.lab5.security.CurrentUser;
import com.example.lab5.service.UserService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public List<UserDto.Response> getAll() {
        return service.findAll().stream().map(UserDto.Response::of).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("@access.canAccessUser(#id)")
    public UserDto.Response getById(@PathVariable Long id) {
        return UserDto.Response.of(service.findById(id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("@access.canAccessUser(#id)")
    public UserDto.Response update(@PathVariable Long id, @Valid @RequestBody UserDto.UpdateRequest request) {
        return UserDto.Response.of(service.update(id, request.username(), request.email()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public void delete(@PathVariable Long id) {
        service.delete(CurrentUser.require().getId(), id);
    }

    @PostMapping("/{id}/block")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserDto.Response block(@PathVariable Long id) {
        return UserDto.Response.of(service.block(CurrentUser.require().getId(), id));
    }

    @PostMapping("/{id}/unblock")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserDto.Response unblock(@PathVariable Long id) {
        return UserDto.Response.of(service.unblock(id));
    }

    @PostMapping("/{id}/role")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public UserDto.Response changeRole(@PathVariable Long id, @Valid @RequestBody UserDto.RoleRequest request) {
        return UserDto.Response.of(service.changeRole(CurrentUser.require().getId(), id, request.role()));
    }
}
