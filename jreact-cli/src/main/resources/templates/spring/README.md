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
| `jreact build` | optimized build, one runnable jar: `server/target/*.jar` (Maven) or `server/build/libs/*.jar` (Gradle, see `build=` in jreact.properties) |
| `jreact start` | runs that jar |
| `jreact test` | runs the tests |
| `jreact doctor` | checks Java, Maven or Gradle and the tools the commands need |
| `jreact docker` | writes a Dockerfile for the jar |

## How a request flows

1. The browser opens `/`. Spring Boot runs the routes of `client/.../App` for that URL on the server (the loader calls the `TodoService` bean directly), renders the page to HTML and answers with it inside `index.html`, together with the loader's answers. (`jreact dev` skips this and answers with the empty `index.html`; remove `jreact.ssr.app` from `application.properties` to turn it off everywhere.)
2. `app.js` starts and hydrates that HTML: the router runs the same loader, which takes the embedded answer instead of calling the server again.
3. `TodoApiClient` is generated from the `TodoApi` interface in `shared/`. It sends `POST /_rpc/TodoApi/list`; Spring Boot calls `TodoService.list()` and answers with JSON; the loader's result (a `List<Todo>`) becomes the page.
4. A `<Form method="post">` runs the route's action (`TodoApiClient.add(...)`), then the loaders run again and the page shows the new data.

### Adding a server method

Add it to `shared/.../TodoApi.java`, implement it in `server/.../TodoService.java`, call it as `TodoApiClient.yourMethod(...)` (returns a `Promise`).
Allowed in parameters and results: `boolean`, `int`, `long`, `double` (and boxes), `String`, enums, `List<T>`, `Map<String,T>`, and records of these.
Anything else is a compile error that says so. Throw `RpcException(status, message)` on the server for a 4xx; the page's `errorElement` gets it.
Every method is a public endpoint: check permissions on the server (`@PreAuthorize` on the bean) and validate arguments.

The browser and the server share one origin, so there is no CORS setup, and cookies and Spring Security's CSRF token just work
(the generated client sends the `XSRF-TOKEN` cookie back as the `X-XSRF-TOKEN` header).
