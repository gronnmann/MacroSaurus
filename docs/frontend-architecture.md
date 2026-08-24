# Frontend architecture

The `web` workspace is a React 19, TypeScript, Vite, and TanStack Query PWA. It
is mobile-first because logging and camera workflows happen most often on a
phone, while desktop layouts expose denser nutrition and progress information.

## Boundaries

```text
src/
├── components/    Reusable UI and nutrition/domain components
├── lib/           Authentication, API transport, query keys, and formatting
├── routes/        Route-level feature orchestration
├── styles/        Tokens, global rules, components, and breakpoints
├── test/          Shared test setup
├── types.ts       Frontend API/domain contracts
├── router.tsx     Public and protected route definitions
└── App.tsx        Router composition only
```

Routes may compose domain components and query hooks, but primitive components
must not fetch data. The API transport is the only place that creates
`Authorization` or `X-User-Id` headers and parses RFC 9457 problem responses.

TanStack Query owns remote state. Route search parameters own shareable state
such as the diary date and food query. Form and dialog state remains local. Do
not add a global state library for temporary UI values.

## App shell and routes

Primary destinations are `/dashboard`, `/food-log`, `/progress`, and `/profile`.
The centered `/track` action renders as a modal on desktop and a bottom sheet on
mobile. Foods, recipes, scanning, and goal editing are secondary flows rather
than primary navigation destinations. Legacy URLs redirect into the new shell.

Dashboard requests the displayed week as a range and resolves goals for the
same dates. Its seven-day chart keeps consumption and targets visible together.
Food Log owns diary mutations and presents entries chronologically rather than
grouping them into meals. Track owns unified food/recipe search, quick entry,
weigh-ins, and barcode acquisition; new entries use the current instant.

## Authentication

`VITE_AUTH_MODE` explicitly chooses an authentication adapter:

- `dev` marks the local user as authenticated and sends `VITE_DEV_USER_ID` as
  `X-User-Id`.
- `supabase` configures `@supabase/supabase-js` for Auth operations only, restores
  and refreshes the session, and restores the route requested before login.

All application routes use the protected layout. `/shared/:token` uses a public
layout and never requires a session. A production deployment must configure the
frontend and backend for the same Supabase project. The browser sends the
Supabase access token only to the Macrosaurus backend; it does not query the
Supabase Data API.

## Data and error rules

- Unknown nutrients remain absent; presentation code must not manufacture zero
  values for nutrition facts.
- Food conversions are previewed through `/food-revisions/{id}/resolve`. Do not
  duplicate density, basis, or portion arithmetic in React.
- Food and recipe source revisions remain stable behind the UI so historical
  Food Log values do not change.
- Barcode frames are decoded in the browser. Only decoded digits are sent to
  barcode endpoints; the label endpoint accepts one deliberately captured photo.
- API validation errors are represented by `ApiError.problem`; forms should
  present the detail next to the action and field errors next to fields where
  supplied.
- Mutations invalidate the smallest relevant query-key prefix.

## Adding a screen

1. Add or reuse types in `types.ts` and an endpoint in `lib/api.ts`.
2. Build generic interaction behavior in `components`; document it in a story.
3. Add the feature orchestration to a route module.
4. Register the route and navigation entry only when it is usable.
5. Cover loading, empty, success, validation, authorization, and error states.
6. Test keyboard behavior and both mobile and desktop layout.

## PWA behavior

`vite-plugin-pwa` precaches the application shell and branded assets. API calls
are network-only, and offline writes are intentionally not queued. This avoids
silently replaying diary or food changes without idempotency and conflict
contracts on the backend.
