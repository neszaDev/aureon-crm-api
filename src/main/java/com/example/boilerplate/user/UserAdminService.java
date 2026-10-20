package com.example.boilerplate.user;

import com.example.boilerplate.common.exception.ConflictException;
import com.example.boilerplate.common.exception.ErrorCode;
import com.example.boilerplate.common.exception.NotFoundException;
import com.example.boilerplate.common.security.AccountEvents;
import com.example.boilerplate.common.text.Texts;
import com.example.boilerplate.common.web.Paging;
import com.example.boilerplate.user.dto.UpdateUserRequest;
import com.example.boilerplate.user.dto.UserResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User management for admins. Security already requires {@code ROLE_ADMIN} in the token; every
 * method re-checks the caller in the database too, so a demoted or deleted admin loses access at
 * once instead of when their access token expires.
 */
@Service
@Transactional
public class UserAdminService {
  private final UserRepository users;
  private final UserMapper mapper;
  private final ApplicationEventPublisher events;

  public UserAdminService(
      UserRepository users, UserMapper mapper, ApplicationEventPublisher events) {
    this.users = users;
    this.mapper = mapper;
    this.events = events;
  }

  @Transactional(readOnly = true)
  public Page<UserResponse> list(long actorId, String query, int page, int size) {
    requireAdmin(actorId);
    String search = Texts.param("q", query);
    var pageable =
        Paging.request(
            page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id").descending()));
    Page<User> result =
        search == null
            ? users.findAll(pageable)
            : users.findByEmailContainingIgnoreCase(search, pageable);
    return result.map(mapper::toResponse);
  }

  @Transactional(readOnly = true)
  public UserResponse get(long actorId, Long id) {
    requireAdmin(actorId);
    return mapper.toResponse(user(id));
  }

  public UserResponse update(long actorId, Long id, UpdateUserRequest r) {
    User actor = requireAdmin(actorId);
    User target = notSelf(actor, user(id), "You cannot change your own account");
    List<String> changed = new ArrayList<>();
    if (r.email() != null && !r.email().equalsIgnoreCase(target.getEmail())) {
      if (users.existsByEmail(r.email().toLowerCase(Locale.ROOT)))
        throw new ConflictException(ErrorCode.EMAIL_TAKEN, "Email is already registered");
      target.changeEmail(r.email());
      changed.add("email");
    }
    if (r.role() != null && r.role() != target.getRole()) {
      target.changeRole(r.role());
      changed.add("role");
    }
    if (!changed.isEmpty()) {
      events.publishEvent(new UserAccessChangedEvent(target.getId()));
      AccountEvents.record("account.update", actor.getId(), target.getId(), changed);
    }
    return mapper.toResponse(target);
  }

  public void delete(long actorId, Long id) {
    User actor = requireAdmin(actorId);
    User target = notSelf(actor, user(id), "You cannot delete your own account");
    events.publishEvent(new UserDeletionEvent(target.getId(), actor.getId(), actor.getEmail()));
    users.delete(target);
    AccountEvents.record("account.delete", actor.getId(), target.getId(), List.of());
  }

  private User requireAdmin(long actorId) {
    User actor =
        users.findById(actorId).orElseThrow(() -> new UsernameNotFoundException("User not found"));
    if (actor.getRole() != Role.ADMIN) throw new AccessDeniedException("Admin role required");
    return actor;
  }

  /** Keeps at least one admin: nobody can demote, rename or delete themselves here. */
  private static User notSelf(User actor, User target, String message) {
    if (actor.getId().equals(target.getId()))
      throw new ConflictException(ErrorCode.OWN_ACCOUNT, message);
    return target;
  }

  private User user(Long id) {
    return users.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
  }
}
