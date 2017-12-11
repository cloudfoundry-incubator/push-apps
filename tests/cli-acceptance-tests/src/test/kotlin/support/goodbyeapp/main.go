package main

import (
	"fmt"
	"net/http"
	"os"
)

func main() {
	http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		name := os.Getenv("NAME")
		fmt.Fprintf(w, "goodbye %s\n", name)
	})
	http.HandleFunc("/v1", func(w http.ResponseWriter, r *http.Request) {
		name := os.Getenv("NAME")
		fmt.Fprintf(w, "goodbye from v1 %s\n", name)
	})

	port := os.Getenv("PORT")
	http.ListenAndServe(fmt.Sprintf(":%s", port), nil)
}
