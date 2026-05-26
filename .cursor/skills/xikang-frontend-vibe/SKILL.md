---
name: xikang-frontend-vibe
description: Guides frontend feature development for the Xikang cloud hospital Vue app. Use when developing frontend pages, Vue components, route/menu entries, business API integration, or when the user mentions vibe coding in this project.
---

# Xikang Frontend Vibe Coding

## Quick Rules

When developing frontend features in this project:

1. Prefer editing only page components under `xikang-hospital-frontend/src/modules/**`.
2. Avoid changing global framework files unless the user explicitly asks.
3. Reuse existing global components and design tokens before writing local styles.
4. Read the relevant design document and API contract before inventing fields.
5. Do not put patient-sensitive data in URLs, localStorage, console logs, or error messages.
6. Run `npm run type-check` after code changes. Run `npm run build` for routing, layout, or shared API changes.

## Global Files Are Restricted

Treat these as shared framework files:

- `src/app/router/**`
- `src/app/stores/**`
- `src/app/layouts/**`
- `src/app/styles/**`
- `src/shared/api/request.ts`
- `src/shared/components/**`
- `src/shared/types/**`

Only change them when necessary, and keep changes minimal.

## Preferred Workflow

1. Identify the module: `patient`, `registration`, `physician`, `medical-tech`, `pharmacy`, `admin`, or `ai`.
2. Create or edit a single page component in that module.
3. Use `PageHeader`, `GlassCard`, `StatusTag`, `EmptyState`, `ErrorState`, and `LoadingState`.
4. Use APIs from `src/shared/api/modules/*`; do not create ad-hoc Axios instances.
5. Keep page-local styles scoped and small.
6. Validate with type-checking.

## More Detail

For model-agnostic instructions that can be used by Cursor, Claude, GPT, DeepSeek, Qwen, or other agents, read `FRONTEND_GUIDE.md`.
