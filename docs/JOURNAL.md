# Engineering Journal

This document is used to keep track of any noteworthy incident that I encounter while working on this project. It should contain the specific details of the incident, the diagnosis that I did, fixes that I came up with, the verification I did to confirm the fix, and the lessons I learned about the ordeal.

## 2026-08-07 - Spring boot application with initial scaffolding and configs does not start
**Expected:** Spring boot application should be able to start successfully and connect to the DB when the initial configs are setup properly in both the docker-compose.yml and application.yaml.  
**Happened:** Application failed to start. Getting an error message that says 'The connection attempt failed.' with SQL State 08001.  
**Diagnosis:** Double-checked that config looks correct. Container looks to be running and healthy. Container config looks stale, previous postgres port config is wrong, 5433:5433, which has since been corrected to 5433:5432.  
**Fix:** Run `docker compose up -d` to recreate postgres container  
**Verified:** Started application again, no longer encountering error, and getting log message that LedgerdApplication started.  
**Lesson:** Be cautious of changes to docker-compose.yml. Make use of useful docker commands like `docker port ledgerd-postgres` and `docker compose config`.