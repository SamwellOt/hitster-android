FROM node:22-alpine
WORKDIR /srv
COPY server/package*.json ./
RUN npm ci --omit=dev
COPY server/src ./src
COPY catalog ../catalog
ENV PORT=8080
EXPOSE 8080
CMD ["node", "src/index.js"]
# Build from the repository root:  docker build -f server/Dockerfile -t hitster-server .
