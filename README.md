# Push Apps

A jar that pushes apps to CF for PCF Metrics

##Backlog

TODO before adding to app dev
* Create services that aren't user provided
* add dial timeout paramerter to CF connection context

v1
* Push mysql logqueue
    * Pushes binary buildpack
    * Blue green
* Push es logqueue
    * Pushes binary buildpack
    * Blue green
* Push ingestor
    * Pushes binary buildpack
    * Blue green
* Push static
    * Pushes static UI
    * Blue green deploy (map-route)

v2
* Should be able to provide a directory or a zip file. If path is a directory, zip it first.
* Migrate database(s)
* Skip ssl flag
* Retry each task x times
* Run tasks in parallel

* Script to run pushapps needs to create security group

##Icebox
* Push APM UI
    * setup env
    * create services
    * bind services
    * blue-green deploy (map-route)