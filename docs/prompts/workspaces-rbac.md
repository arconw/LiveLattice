# Stage 4: Core Domain - Workspaces & RBAC

## Objective

Implement the Spring Boot Core Domain with workspace management, member invitation, RBAC permission enforcement, and tier-based quotas.

## Requirements

1. Initialize Spring Boot 4.x baseline project in `core/` with an exact pinned patch version:
   - Spring Web MVC, Spring Data JPA, Flyway, Kafka, Redis
   - Gradle Kotlin DSL build file
   - Dockerfile (multi-stage: build + runtime)
2. Implement domain entities with Flyway migrations:
   - `workspaces` table with RLS-ready `workspace_id`
   - `users` table (federated from Keycloak)
   - `workspace_members` with role enum
   - `role_permissions` lookup table
3. Implement CQRS command/query bus:
   - `CommandBus` dispatches to `@CommandHandler` beans
   - `QueryBus` dispatches to `@QueryHandler` beans
   - Validation via `@Validated` + Jakarta Bean Validation
4. Implement workspace commands:
   - `CreateWorkspace`, `UpdateWorkspaceSettings`, `DeleteWorkspace`
   - Enforce slug uniqueness with retry on collision
5. Implement member commands:
   - `InviteWorkspaceMember`, `ChangeMemberRole`, `RemoveWorkspaceMember`
   - Publish events to Kafka (`WorkspaceMemberAdded`, `WorkspaceMemberRemoved`)
6. Implement permission evaluation:
   - `PermissionService` with Redis cache (5 min TTL)
   - `@WorkspacePermission("canvas:edit")` annotation + AOP aspect
   - Compare user role against `role_permissions` matrix
7. Implement quota enforcement:
   - Tier-based limits (free: 5 members, 10 canvases; pro: 50 members, 500 canvases)
   - `@CheckQuota` annotation + AOP aspect
   - Redis counters with nightly PostgreSQL reconciliation
8. Implement REST controllers with proper validation and error handling
9. Write unit tests (JUnit 5 + Mockito) and integration tests (Testcontainers)

## Constraints

- Do not implement frontend code
- Do not commit changes
- Do not leave comments in code
- Docker Compose must be the only local execution path
- All database changes must go through Flyway migrations
- Permission check must be cached in Redis, not queried from DB on every request

## Verification

```bash
# Start infra
docker compose up -d postgres redis kafka

# Build and run core
cd core && ./gradlew bootRun

# Create workspace
curl -X POST http://localhost:8080/workspaces \
  -H "Content-Type: application/json" \
  -H "x-user-id: user-123" \
  -d '{"name":"My Workspace","slug":"my-workspace"}'

# Invite member
curl -X POST http://localhost:8080/workspaces/ws-123/members \
  -H "Content-Type: application/json" \
  -H "x-user-id: user-123" \
  -d '{"email":"collab@example.com","role":"EDITOR"}'

# Verify permission (should return roles)
curl http://localhost:8080/workspaces/ws-123/members \
  -H "x-user-id: user-123"

# Run tests
cd core && ./gradlew test
```
