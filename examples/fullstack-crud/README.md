# Library: a full stack CRUD example

A book catalogue written entirely in Java: Spring Boot 3 + Spring Data JPA (H2) on the server, JReact (`.jrx`, compiled to JavaScript by TeaVM) in the browser. Generated with `jreact new`, then filled in.

What it shows:

- **CRUD**: list, view, create, edit, delete (two-step confirm).
- **Search, status filter and paging**, all in the URL (`/?q=fowler&status=LOST&page=2`).
- **Typed RPC**: `shared/BookApi` is a `@ServerApi` interface; the browser calls it through the generated `BookApiClient` (Promises), the server implements it as a Spring bean. `Optional`, `LocalDate`, enums, records and `Map` cross the wire.
- **Data router**: every page is a route with a `loader` (reads) and, for forms, an `action` (writes); `Form`, `useActionData`, `useNavigation`, `Redirect`.
- **Validation**: the server throws `RpcException(422, "The year must be ...")`; the form shows the message and keeps what was typed.
- **Errors**: an unknown book is a 404 `ErrorResponse`, shown inside the layout (and answered with HTTP 404).
- **SSR + hydration**: the first page is rendered on the server (`jreact.ssr.app`) from the same routes, the loader answers are embedded in the page, and the browser hydrates it without calling the API again.

## Layout

```
shared/   Book, BookInput, BookPage, BookStatus, BookApi (@ServerApi)
server/   BookEntity, BookRepository, BookService (implements BookApi), SampleData, Application
client/   src/main/jrx/...: App (routes), Layout, BookList, BookDetail, BookForm, WebMain
e2e.js    clicks through the running app in jsdom
```

## Run

From this directory (needs JDK 17+, Maven, and `jreact` from the repository's `bin/`):

```
../../bin/jreact dev          # dev server with reload (SSR is off in dev)
../../bin/jreact build        # tests + one jar
java -jar server/target/library-server-0.1.0-SNAPSHOT.jar --server.port=8099
node e2e.js http://localhost:8099
```

Open http://localhost:8080 (or your port). The database is in memory and starts with 23 books.

## Tests

`jreact build` runs `server/src/test/.../BookApiTest`: the API over HTTP against the real database (paging, search, CRUD, validation, counts) and the server rendered page. `e2e.js` covers the browser side.
