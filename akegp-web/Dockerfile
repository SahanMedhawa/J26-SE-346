FROM node:22-alpine
WORKDIR /web
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build
EXPOSE 5173
CMD ["npm","run","dev","--","--host","0.0.0.0"]
