FROM golang:1.20-alpine AS build
WORKDIR /src
COPY . .
RUN go build -o /out/relay ./

FROM alpine:3.18
COPY --from=build /out/relay /usr/local/bin/relay
EXPOSE 4000
CMD ["/usr/local/bin/relay","-addr=0.0.0.0:4000"]
