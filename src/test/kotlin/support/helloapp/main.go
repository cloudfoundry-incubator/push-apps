package main

import (
	"fmt"
	"net/http"
	"os"
	"encoding/json"
)

type Vcap struct {
  UserProvided []UserProvidedService `json:"user-provided"`
}

type UserProvidedService struct {
  Credentials map[string]string `json:"credentials"`
  Name string `json:"name"`
}

func main() {
	http.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		name := os.Getenv("NAME")
		vcapJson := os.Getenv("VCAP_SERVICES")

    var vcap Vcap
    var credentials map[string]string

		json.Unmarshal([]byte(vcapJson), &vcap)
		for _, service := range vcap.UserProvided {
		  if service.Name == "compliment-service" {
		    credentials = service.Credentials
		  }
		}

		fmt.Fprintf(w, "hello %s, you are %s!\nYou have these services: %s",
		  name,
		  credentials["compliment"],
		  vcapJson,
    )
	})

	port := os.Getenv("PORT")
	http.ListenAndServe(fmt.Sprintf(":%s", port), nil)
}
