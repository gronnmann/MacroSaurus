FROM node:24-alpine AS build

ENV HUSKY=0
WORKDIR /workspace
RUN corepack enable

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY web/package.json web/package.json
RUN pnpm install --frozen-lockfile --filter ./web...

COPY web web

ARG VITE_AUTH_MODE=supabase
ARG VITE_SUPABASE_URL
ARG VITE_SUPABASE_PUBLISHABLE_KEY
ENV VITE_AUTH_MODE=${VITE_AUTH_MODE}
ENV VITE_SUPABASE_URL=${VITE_SUPABASE_URL}
ENV VITE_SUPABASE_PUBLISHABLE_KEY=${VITE_SUPABASE_PUBLISHABLE_KEY}

RUN pnpm --dir web build

FROM nginx:1.30.4-alpine
COPY deploy/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/web/dist /usr/share/nginx/html
EXPOSE 80
