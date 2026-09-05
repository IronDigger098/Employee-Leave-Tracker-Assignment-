# =============================================================================
#  Frontend image - multi-stage build.
#
#  An Angular application is compiled, not interpreted: `ng build` turns the
#  TypeScript, HTML and CSS into plain static files. Once that is done, Node is no
#  longer needed at all - the runtime just has to serve files over HTTP.
#
#  So stage 1 uses Node to build, and stage 2 is nginx with nothing but the
#  compiled output. The final image contains no source, no node_modules, and no
#  Node runtime - a few hundred megabytes smaller than shipping the dev server.
#
#  Shipping `ng serve` to production is a common mistake worth being able to name:
#  it is a development server with a file watcher and unminified output.
# =============================================================================

# ---------- Stage 1: build ----------
FROM node:22-alpine AS build
WORKDIR /build

# package*.json first, for the same layer-caching reason as the backend's pom.xml.
COPY package.json package-lock.json ./

# `npm ci` rather than `npm install`:
#   - installs exactly the versions pinned in package-lock.json, so the build is
#     reproducible instead of drifting as new patch releases appear
#   - fails loudly if package.json and the lockfile disagree
#   - deletes node_modules first, so no stale state survives
RUN npm ci

COPY . .

# Produces dist/frontend/browser - minified, tree-shaken, with the lazy-loaded
# route chunks split out.
RUN npm run build

# ---------- Stage 2: runtime ----------
FROM nginx:1.27-alpine

# Our server block: SPA fallback plus the /api reverse proxy. Replaces the
# default site that ships with the image.
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Only the compiled output crosses over. Note the /browser suffix - Angular's
# application builder puts the browser bundle in its own subfolder.
COPY --from=build /build/dist/frontend/browser /usr/share/nginx/html

EXPOSE 80

# The nginx base image already defines a correct CMD, so we do not override it.
