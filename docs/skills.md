# Agent Skills

`mall-order-agent` supports administrator-managed, instruction-only skills. A skill can add a reusable
workflow and can request a small allowlist of existing server actions. Downloaded code is never executed.

## Package format

Every package must contain `SKILL.md`:

```markdown
---
name: refund-explainer
description: Explain refund eligibility when a user asks about refund conditions.
---
Explain the authoritative eligibility result. Do not change the result returned by the order service.
```

Optional platform metadata belongs in `agents/ecommerce-order-agent.yaml`:

```yaml
required_actions:
  - REFUND_ELIGIBILITY
trigger_keywords:
  - refund eligibility
  - 退款资格
allow_implicit_invocation: true
```

Supported actions are `MEMORY_LOAD`, `KNOWLEDGE_SEARCH`, `ORDER_QUERY`, and
`REFUND_ELIGIBILITY`. The platform always appends `LLM_GENERATE`. Packages containing a `scripts/`
directory or unknown actions are rejected.

## Install and activate

Only authenticated administrators can use these endpoints. State-changing requests use the application's
existing CSRF protection.

1. Preview the exact package that would be installed:

```http
POST /admin/skills/preview
Content-Type: application/json

{
  "sourceUrl": "https://github.com/example/agent-skills",
  "ref": "main",
  "path": "skills/refund-explainer"
}
```

2. Install the package using the digest returned by preview:

```http
POST /admin/skills/install
Content-Type: application/json

{
  "sourceUrl": "https://github.com/example/agent-skills",
  "ref": "main",
  "path": "skills/refund-explainer",
  "expectedDigest": "<sha256-from-preview>"
}
```

3. Activate the installed version:

```http
POST /admin/skills/{skillId}/versions/{versionId}/activate
```

Installed versions are drafts until activated. Activating an older version performs a rollback. Other
management endpoints are:

- `GET /admin/skills`
- `GET /admin/skills/{skillId}/versions`
- `POST /admin/skills/{skillId}/activate` with `{ "versionId": 123 }`
- `POST /admin/skills/{skillId}/rollback` with `{ "versionId": 122 }`
- `POST /admin/skills/{skillId}/disable`
- `POST /admin/skills/{skillId}/policy`

The policy body is `{ "allowImplicitInvocation": false }` when a skill should only be invoked explicitly.

## Invocation

Use `$refund-explainer` for explicit invocation. Enabled skills can also be selected by their declared
trigger keywords and description when implicit invocation is enabled. Skill plans still pass through the
same order, knowledge, and human-approval capability checks as built-in plans.

GitHub and GitLab repositories are supported directly. An allowlisted HTTPS ZIP URL is also accepted.
Additional source hosts must be explicitly configured with `AGENT_SKILL_ALLOWED_HOSTS`. Private, loopback,
link-local, credential-bearing, query-bearing, and non-HTTPS URLs are rejected by default.
