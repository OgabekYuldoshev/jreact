# {{name}}

A JReact UI and a Spring Boot API in one project.

```
shared/   types used by both sides (plain Java)
client/   the UI: src/main/jrx (.jrx components), src/main/styles/app.css (Tailwind)
server/   Spring Boot: the API, and it serves the built UI
```

## Commands

| | |
|---|---|
| `jreact dev` | server on http://localhost:8080, rebuilds the UI on every change and reloads the browser |
| `jreact build` | optimized build, one runnable jar: `server/target/{{name}}-server-0.1.0-SNAPSHOT.jar` |
| `jreact start` | runs that jar |
| `jreact test` | runs the tests |
| `jreact doctor` | checks Java, Maven and the tools the commands need |
| `jreact docker` | writes a Dockerfile for the jar |

## How a request flows

1. The browser opens `/`. Spring Boot answers with `index.html` (every URL that is a page, not `/api/...`, gets it).
2. `app.js` starts, the router matches the URL and runs the route's loader: `Http.get("/api/todos")`.
3. Spring Boot's `TodoController` answers with JSON, the loader's result becomes the page.
4. A `<Form method="post">` runs the route's action (`Http.post(...)`), then the loaders run again and the page shows the new data.

The browser and the server share one origin, so there is no CORS setup, and cookies and Spring Security's CSRF token just work
(`Http` sends the `XSRF-TOKEN` cookie back as the `X-XSRF-TOKEN` header).
