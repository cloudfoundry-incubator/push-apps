# Push Apps

A jar that pushes apps to CF for PCF Metrics

##Backlog

TODO before adding to app dev
* map route

v1
* Push static
    * Pushes static UI
    * Blue green deploy (map-route)

v2
* Should be able to provide a directory or a zip file. If path is a directory, zip it first.
* Migrate database(s)
* Retry each task x times

* Script to run pushapps needs to create security group

##Icebox
* Push APM UI
    * setup env
    * create services
    * bind services
    * blue-green deploy (map-route)