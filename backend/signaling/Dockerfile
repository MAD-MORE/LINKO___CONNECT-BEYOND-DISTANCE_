# Build the Go signaling server
FROM golang:1.20-alpine AS build
WORKDIR /src
COPY . .
RUN apk add --no-cache git
RUN go build -o /out/signaling ./

FROM alpine:3.18
COPY --from=build /out/signaling /usr/local/bin/signaling
EXPOSE 3000
CMD ["/usr/local/bin/signaling","-addr=0.0.0.0:3000"]
