# Use official lightweight Nginx Alpine image
FROM nginx:1.25-alpine

# Copy static web files (HTML, CSS, JS) into Nginx document root
COPY . /usr/share/nginx/html/

# Copy custom Nginx server configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Expose port 80 (standard HTTP)
EXPOSE 80

# Run Nginx in foreground mode
CMD ["nginx", "-g", "daemon off;"]
