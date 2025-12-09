#!/bin/sh

# Replace env vars in env-config.js
echo "window.__DRKUBE_CONFIG__ = {" > /usr/share/nginx/html/env-config.js
echo "  API_URL: \"${API_URL}\"," >> /usr/share/nginx/html/env-config.js
echo "};" >> /usr/share/nginx/html/env-config.js

exec "$@"
