# Stage 1 – build
FROM node:20-alpine AS builder
WORKDIR /app
# Install exactly what package-lock.json pins, so image builds are reproducible
COPY package.json package-lock.json ./
RUN npm ci --legacy-peer-deps
COPY . .
RUN npm run build

# Stage 2 – serve with nginx
FROM nginx:alpine
COPY --from=builder /app/dist/hr-admin-dashboard/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]

