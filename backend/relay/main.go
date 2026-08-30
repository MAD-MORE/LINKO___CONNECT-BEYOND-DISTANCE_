# Simple TCP relay that reads a target host:port on connection start and proxies data
package main

import (
	"bufio"
	"flag"
	"io"
	"log"
	"net"
)

var addr = flag.String("addr", ":4000", "listen address")

func handleConn(c net.Conn) {
	defer c.Close()
	r := bufio.NewReader(c)
	target, err := r.ReadString('\n')
	if err != nil {
		log.Println("read target header error:", err)
		return
	}
	target = target[:len(target)-1]
	remote, err := net.Dial("tcp", target)
	if err != nil {
		log.Println("dial target error:", err)
		return
	}
	defer remote.Close()
	// start piping remaining buffered data plus the rest
	go io.Copy(remote, r)
	io.Copy(c, remote)
}

func main() {
	flag.Parse()
	ln, err := net.Listen("tcp", *addr)
	if err != nil {
		log.Fatal(err)
	}
	log.Println("relay listening on", *addr)
	for {
		c, err := ln.Accept()
		if err != nil {
			log.Println("accept error:", err)
			continue
		}
		go handleConn(c)
	}
}
