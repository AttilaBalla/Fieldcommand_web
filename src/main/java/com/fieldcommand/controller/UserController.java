package com.fieldcommand.controller;

import com.fieldcommand.payload.GenericResponseJson;
import com.fieldcommand.payload.user.InviteJson;
import com.fieldcommand.payload.user.UpdateJson;
import com.fieldcommand.user.User;
import com.fieldcommand.user.UserPrincipal;
import com.fieldcommand.user.UserService;
import com.fieldcommand.utility.Exception.UnauthorizedModificationException;
import com.fieldcommand.utility.Exception.UserNotFoundException;
import com.fieldcommand.utility.JsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailSendException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.bind.annotation.*;

import javax.management.relation.RoleNotFoundException;

@CrossOrigin
@RestController
public class UserController {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping(value =  "/api/user/currentUser")
    public UserPrincipal getLoggedInUser(@AuthenticationPrincipal UserPrincipal loggedInUser) {
        return loggedInUser;
    }

    @PostMapping(value = "/api/admin/invite")
    public String inviteUser(@RequestBody InviteJson invite) {

        String internalError = "An internal error occurred. Try again later!";

        GenericResponseJson response = new GenericResponseJson();

        String email = invite.getEmail();
        String username = invite.getUsername();

        response = userService.validateInvite(email, username, response);
        if(!response.isSuccess()) {
            return JsonUtil.toJson(response);
        }

        try {

            User user = new User();

            user.setUsername(username);
            user.setEmail(email);

            userService.registerUser(user);

        } catch (RoleNotFoundException ex) {
            logger.error("Failed to set role for {}, reason: {}", username, ex.getMessage());
            response.setSuccess(false);
            response.setInformation(internalError);

        } catch (MailSendException ex) {
            logger.error("Failed to send e-mail to {}, reason: {}", email, ex.getMessage());
            response.setSuccess(false);
            response.setInformation(internalError);

        } catch (IllegalArgumentException ex) {
            response.setSuccess(false);
            response.setInformation(ex.getMessage());
        }

        return JsonUtil.toJson(response);
    }

    @PutMapping("/api/admin/updateUser")
    public ResponseEntity<?> updateUser(@RequestBody UpdateJson updateJson, Authentication authentication) {

        GenericResponseJson response = new GenericResponseJson();
        try {
            userService.prepareUserUpdate(updateJson, authentication.getName());

        } catch (IllegalArgumentException | UnauthorizedModificationException | UserNotFoundException ex) {

            response.setSuccess(false);
            response.setInformation(ex.getMessage());
            return ResponseEntity.badRequest().body(response);

        } catch (TransactionSystemException ex) {

            response.setSuccess(false);
            response.setInformation("The fields cannot be empty!");
            return ResponseEntity.badRequest().body(response);

        }

        response.setSuccess(true);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/admin/resetUser")
    public ResponseEntity<?> resetUser(@RequestBody Long userId) {

        GenericResponseJson response = new GenericResponseJson();

        try {
            userService.resetActivation(userId);
        } catch(UserNotFoundException ex) {

            response.setSuccess(false);
            response.setInformation(ex.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
        return ResponseEntity.status(HttpStatus.OK).body(response);

    }


    @GetMapping(value = "/api/admin/users")
    public String getUsers() {
        return JsonUtil.toJson(userService.findAll());
    }

    @GetMapping(value = "/api/admin/userRoles")
    public String getUserRoles() {
        return JsonUtil.toJson(userService.findAllRolesOfAllUsers());
    }
}
