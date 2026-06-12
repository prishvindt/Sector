FROM node:lts-alpine AS deps

WORKDIR /app

COPY package*.json ./
COPY prisma ./prisma

RUN npm ci

FROM deps AS build

COPY tsconfig*.json nest-cli.json ./
COPY src ./src

RUN npm run build

FROM node:lts-alpine AS production

WORKDIR /app

ENV NODE_ENV=production

COPY package*.json ./
COPY prisma ./prisma

RUN npm ci --omit=dev --ignore-scripts && npm cache clean --force

COPY --from=build /app/node_modules/.prisma ./node_modules/.prisma
COPY --from=build /app/node_modules/@prisma/client ./node_modules/@prisma/client
COPY --from=build /app/dist ./dist

USER node

EXPOSE 3000

CMD ["node", "dist/main.js"]
