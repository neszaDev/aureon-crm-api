package com.example.boilerplate.workspace.invitation;

import static com.example.boilerplate.workspace.WorkspaceRole.OWNER;

import com.example.boilerplate.audit.AuditAction;
import com.example.boilerplate.audit.AuditChanges;
import com.example.boilerplate.audit.AuditEntityType;
import com.example.boilerplate.audit.AuditTrail;
import com.example.boilerplate.auth.AuthService;
import com.example.boilerplate.auth.RefreshTokenService;
import com.example.boilerplate.auth.dto.AuthTokensResponse;
import com.example.boilerplate.common.exception.ConflictException;
import com.example.boilerplate.common.exception.ErrorCode;
import com.example.boilerplate.common.exception.ForbiddenException;
import com.example.boilerplate.common.exception.NotFoundException;
import com.example.boilerplate.common.security.Tokens;
import com.example.boilerplate.common.text.Texts;
import com.example.boilerplate.ratelimit.RateLimiter;
import com.example.boilerplate.ratelimit.RateLimiter.Policy;
import com.example.boilerplate.user.User;
import com.example.boilerplate.user.UserRepository;
import com.example.boilerplate.workspace.Membership;
import com.example.boilerplate.workspace.Permission;
import com.example.boilerplate.workspace.Workspace;
import com.example.boilerplate.workspace.WorkspaceAccess;
import com.example.boilerplate.workspace.WorkspaceContext;
import com.example.boilerplate.workspace.WorkspaceMember;
import com.example.boilerplate.workspace.WorkspaceMemberRepository;
import com.example.boilerplate.workspace.WorkspaceRepository;
import com.example.boilerplate.workspace.dto.MemberRef;
import com.example.boilerplate.workspace.invitation.dto.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Invitation links: managed inside a workspace, then used through the token by someone who is not a
 * member yet (no workspace context there, so every lookup names the workspace). A link works once,
 * for 7 days, and only for the invited email.
 */
@Service
@Transactional
public class InvitationService {
  static final Duration TTL = Duration.ofDays(7);

  private final InvitationRepository invitations;
  private final WorkspaceRepository workspaces;
  private final WorkspaceMemberRepository members;
  private final UserRepository users;
  private final WorkspaceAccess access;
  private final AuditTrail audit;
  private final PasswordEncoder encoder;
  private final RefreshTokenService refreshTokens;
  private final RateLimiter rateLimiter;

  public InvitationService(
      InvitationRepository invitations,
      WorkspaceRepository workspaces,
      WorkspaceMemberRepository members,
      UserRepository users,
      WorkspaceAccess access,
      AuditTrail audit,
      PasswordEncoder encoder,
      RefreshTokenService refreshTokens,
      RateLimiter rateLimiter) {
    this.invitations = invitations;
    this.workspaces = workspaces;
    this.members = members;
    this.users = users;
    this.access = access;
    this.audit = audit;
    this.encoder = encoder;
    this.refreshTokens = refreshTokens;
    this.rateLimiter = rateLimiter;
  }

  @Transactional(readOnly = true)
  public List<InvitationResponse> list() {
    Membership m = access.require(Permission.MEMBERS_MANAGE);
    return invitations.findPending(m.workspaceId(), Instant.now()).stream()
        .map(InvitationService::response)
        .toList();
  }

  public CreatedInvitationResponse create(CreateInvitationRequest r) {
    Membership m = access.require(Permission.MEMBERS_MANAGE);
    if (r.role() == OWNER) requireOwner(m);
    if (members.existsByWorkspaceIdAndEmail(m.workspaceId(), r.email())) {
      throw new ConflictException(ErrorCode.ALREADY_MEMBER, "This person is already a member");
    }
    Instant now = Instant.now();
    invitations
        .findOpen(m.workspaceId(), r.email())
        .ifPresent(
            open -> {
              if (open.status(now) != InvitationStatus.EXPIRED) {
                throw new ConflictException(
                    ErrorCode.ALREADY_INVITED, "This email already has a pending invitation");
              }
              open.revoke(now);
              // The insert below runs at once; the partial unique index must see this first.
              invitations.flush();
            });
    String token = Tokens.random();
    User inviter =
        users.findById(m.userId()).orElseThrow(() -> new NotFoundException("User not found"));
    Invitation i =
        invitations.save(
            new Invitation(
                m.workspaceId(),
                r.email(),
                r.role(),
                Tokens.sha256Hex(token),
                inviter,
                now.plus(TTL)));
    audit.record(AuditAction.INVITE, AuditEntityType.INVITATION, i.getId(), i.getEmail(), role(i));
    InvitationResponse v = response(i);
    return new CreatedInvitationResponse(
        v.id(), v.email(), v.role(), v.invitedBy(), v.createdAt(), v.expiresAt(), token);
  }

  /** A pending or expired invitation can be revoked; an accepted or revoked one can't. */
  public void revoke(long id) {
    Membership m = access.require(Permission.MEMBERS_MANAGE);
    Invitation i =
        invitations
            .findByIdAndWorkspaceId(id, m.workspaceId())
            .orElseThrow(() -> new NotFoundException("Invitation not found"));
    if (i.getRole() == OWNER) requireOwner(m);
    Instant now = Instant.now();
    InvitationStatus status = i.status(now);
    if (status == InvitationStatus.ACCEPTED || status == InvitationStatus.REVOKED) {
      throw new ConflictException(
          ErrorCode.INVITATION_NOT_PENDING, "This invitation is no longer pending");
    }
    i.revoke(now);
    audit.record(AuditAction.INVITE_REVOKE, AuditEntityType.INVITATION, id, i.getEmail(), Map.of());
  }

  /**
   * Revokes the pending invitations {@code userId} sent in the current workspace: they leave, or
   * lose MEMBERS_MANAGE (MemberService), and their links must not outlive that.
   */
  public void revokeSentBy(long userId) {
    Membership m = WorkspaceContext.require();
    Instant now = Instant.now();
    for (Invitation i : invitations.findPendingSentBy(m.workspaceId(), userId, now)) {
      i.revoke(now);
      audit.record(
          AuditAction.INVITE_REVOKE, AuditEntityType.INVITATION, i.getId(), i.getEmail(), Map.of());
    }
  }

  /** Public: anyone with the link may see what it is for and whether it still works. */
  @Transactional(readOnly = true)
  public InvitationPreviewResponse preview(String token) {
    Invitation i = byToken(token);
    Workspace w =
        workspaces
            .findById(i.getWorkspaceId())
            .orElseThrow(() -> new NotFoundException("Invitation not found"));
    return new InvitationPreviewResponse(
        w.getId(),
        w.getName(),
        i.getEmail(),
        i.getRole(),
        i.getExpiresAt(),
        i.status(Instant.now()));
  }

  /**
   * Two concurrent accepts may both pass the checks; the membership's unique key (or the
   * invitation's version) makes the second fail with a 409.
   */
  public AcceptInvitationResponse accept(String token, long userId) {
    Invitation i = byToken(token);
    User user =
        users.findById(userId).orElseThrow(() -> new UsernameNotFoundException("User not found"));
    if (!i.getEmail().equals(user.getEmail())) {
      throw new ForbiddenException(
          ErrorCode.INVITATION_EMAIL_MISMATCH, "This invitation is for another email address");
    }
    requirePending(i);
    if (members.existsByWorkspaceIdAndUserId(i.getWorkspaceId(), user.getId())) {
      i.accept(Instant.now());
    } else {
      join(i, user);
    }
    return new AcceptInvitationResponse(i.getWorkspaceId());
  }

  /** Creates the invited email's account even when open registration is disabled. */
  public AuthTokensResponse register(
      String token, RegisterThroughInvitationRequest r, String clientIp) {
    AuthService.requireHashable("password", r.password());
    rateLimiter.consume(Policy.REGISTER_PER_IP, clientIp);
    Invitation i = byToken(token);
    requirePending(i);
    if (users.existsByEmail(i.getEmail()))
      throw new ConflictException(ErrorCode.EMAIL_TAKEN, "Email is already registered");
    User user = new User(i.getEmail(), encoder.encode(r.password()));
    user.changeDisplayName(Texts.optional(r.displayName()));
    join(i, users.save(user));
    return refreshTokens.create(user);
  }

  private void join(Invitation i, User user) {
    members.save(new WorkspaceMember(i.getWorkspaceId(), user.getId(), i.getRole()));
    i.accept(Instant.now());
    audit.record(
        i.getWorkspaceId(),
        user.getId(),
        user.getEmail(),
        AuditAction.JOIN,
        AuditEntityType.MEMBER,
        user.getId(),
        user.getEmail(),
        role(i));
  }

  /** {@code {role: {from: null, to: <the invitation's role>}}}. */
  private static Map<String, Map<String, Object>> role(Invitation i) {
    return AuditChanges.diff(Map.of(), Map.of("role", i.getRole().name()));
  }

  private Invitation byToken(String token) {
    return invitations
        .findByTokenHash(Tokens.sha256Hex(token))
        .orElseThrow(() -> new NotFoundException("Invitation not found"));
  }

  private static void requirePending(Invitation i) {
    if (i.status(Instant.now()) != InvitationStatus.PENDING) {
      throw new ConflictException(
          ErrorCode.INVITATION_NOT_PENDING, "This invitation is no longer pending");
    }
  }

  private static void requireOwner(Membership m) {
    if (m.role() != OWNER)
      throw new ForbiddenException(ErrorCode.OWNER_ONLY, "Only owners can manage owners");
  }

  private static InvitationResponse response(Invitation i) {
    User u = i.getInvitedBy();
    MemberRef invitedBy =
        u == null ? null : new MemberRef(u.getId(), u.getEmail(), u.getDisplayName());
    return new InvitationResponse(
        i.getId(), i.getEmail(), i.getRole(), invitedBy, i.getCreatedAt(), i.getExpiresAt());
  }
}
