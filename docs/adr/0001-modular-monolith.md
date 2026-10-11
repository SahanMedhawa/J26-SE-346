# ADR 0001: Modular monolith

AKEGP starts as one deployable Spring Boot application composed from independent Maven modules. Modules expose only API contracts and communicate through the shared kernel or application events.
