# JReact

**Java'da React.** UI'ni Java klasslari va JSX'ga o'xshash `.jsxj` sintaksisi bilan yozasiz, brauzer uchun `app.js` olasiz. React'ning ichki mexanizmi (Fiber, reconciler, hooks, scheduler) Java OOP asosida qayta qurilgan.

```java
public class Counter extends Component<Counter.Props> {

    public record Props(String label) {}

    @Override
    public Element render(Props props) {
        State<Integer> count = useState(0);
        return (
            <button onClick={e -> count.update(c -> c + 1)}>
                {props.label()}: {count.get()}
            </button>
        );
    }
}
```

```
.jsxj ──jsxjc──▶ .java ──javac──▶ .class ──TeaVM──▶ app.js (brauzer)
                                      └──▶ JVM'da HTML (ServerRenderer)
```

> **Holat: erta bosqich.** Ishlaydi: `.jsxj` kompilyatori, Fiber reconciler (state, hooks, key'li diff, context, memo, error boundary), scheduler (ustuvorlik, bo'laklab render), DOM host, TeaVM bilan brauzer build, dev server, VS Code qo'llab-quvvatlashi. Brauzer build'i jsdom'da sinalgan, haqiqiy brauzerda hali emas. Batafsil: [Cheklovlar](#cheklovlar) va [Yo'l xaritasi](#yol-xaritasi).

## Mundarija

- [Talablar](#talablar)
- [Tez boshlash: namunani ishga tushirish](#tez-boshlash-namunani-ishga-tushirish)
- [O'z ilovangizni yaratish](#oz-ilovangizni-yaratish) (brauzer)
- [`.jsxj` sintaksisi](#jsxj-sintaksisi)
- [Komponentlar va props](#komponentlar-va-props)
- [Hooks](#hooks)
- [Context va memo](#context-va-memo)
- [Eventlar](#eventlar)
- [Router](#router)
- [Stillar: CSS, Sass, Tailwind](#stillar-css-sass-tailwind)
- [Namunalar: Tailwind bilan 6 ta ilova](#namunalar-tailwind-bilan-5-ta-ilova)
- [Error boundary](#error-boundary)
- [Ilovani ishga tushirish va ustuvorlik](#ilovani-ishga-tushirish-va-ustuvorlik)
- [JVM'da ishlatish (HTML render, test)](#jvmda-ishlatish-html-render-test)
- [Ishlab chiqish jarayoni](#ishlab-chiqish-jarayoni): dev server, VS Code, xatolar, dev ogohlantirishlar
- [Buyruqlar ma'lumotnomasi](#buyruqlar-malumotnomasi)
- [Qanday ishlaydi](#qanday-ishlaydi)
- [Xavfsizlik (XSS)](#xavfsizlik-xss)
- [Unumdorlik (benchmark)](#unumdorlik-benchmark)
- [Maven Central'ga chiqarish](#maven-centralga-chiqarish)
- [Muammolarni hal qilish](#muammolarni-hal-qilish)
- [Loyiha tuzilmasi](#loyiha-tuzilmasi)
- [Cheklovlar](#cheklovlar)
- [Yo'l xaritasi](#yol-xaritasi)

## Talablar

| Nima | Qachon kerak |
|---|---|
| **JDK 17+** (JDK 21 va 26'da sinalgan) | doim. `javac` diagnostikasi uchun JRE emas, JDK kerak |
| **Maven 3.x** (3.10 da sinalgan) | brauzer build'i (TeaVM) uchun. Internet kerak (TeaVM yuklanadi) |
| **Node.js** (v26 da sinalgan) | `./build.sh web-test`, `./build.sh dev`, VS Code kengaytmasi uchun |
| VS Code | ixtiyoriy, `.jsxj` ranglash va language server uchun |

macOS, Linux va Windows'da (Git Bash yoki WSL) ishlaydi. Maven faqat brauzer build'i uchun kerak, qolgan hammasi (`./build.sh`, testlar) faqat JDK bilan ishlaydi.

## Tez boshlash: namunani ishga tushirish

```bash
git clone <repo-url> jreact
cd jreact

./build.sh          # kompilyator -> runtime -> .jsxj -> .java -> javac -> Todo namunasi HTML chiqaradi
./build.sh test     # barcha testlar (kompilyator 41, scheduler 14, reconciler 57, dom 24, router 20 + 31 (data router), language server 12)
./build.sh dev      # galereya + 5 ilova: http://localhost:5173 (o'zgarsa o'zi qayta yig'iladi)
```

`./build.sh` Todo'ning HTML'ini terminalga chiqaradi (`<section class="todo-app"><h1>JReact Todo</h1>...`). Har ishga tushganda `build/` noldan yaratiladi.

`./build.sh dev` birinchi marta Maven orqali TeaVM'ni yuklaydi (bir necha daqiqa), keyingi qayta yig'ishlar ~4 soniya. `/` da galereya, `/todo/` da Todo ilovasi ("Xato chiqar" tugmasi error boundary namunasi), `/kanban/`, `/shop/`, `/dashboard/`, `/landing/`, `/blog/`, `/contacts/` da Tailwind ilovalari.

## O'z ilovangizni yaratish

Brauzer ilovasi uchun tayyor andoza bor: [`templates/web-app`](templates/web-app). Qadamlar:

**1. JReact'ni bir marta o'rnating** (modullarni lokal Maven repoga qo'yadi):

```bash
cd jreact
mvn -B -q -pl jreact-web,jreact-maven-plugin -am install
```

**2. Andozani nusxalang:**

```bash
cp -R templates/web-app ../mening-ilovam
cd ../mening-ilovam
```

Fayllar:

```
mening-ilovam/
├── pom.xml                              # jsxj:compile -> TeaVM -> app.js zanjiri (o'zgartirish shart emas)
└── src/main
    ├── jsxj/app/
    │   ├── Counter.jsxj                 # komponent
    │   └── WebMain.jsxj                 # kirish nuqtasi (mainClass app.WebMain)
    └── webapp/index.html                # sahifa
```

`pom.xml` da faqat `groupId`/`artifactId` ni o'zgartiring. Oddiy Java fayllaringiz bo'lsa `src/main/java` ga qo'ying (papka bo'lmasa kompilyator uni o'tkazib yuboradi).

**3. Kirish nuqtasi** (`WebMain.jsxj`): `JReactWeb.createRoot("root")` sahifadagi `id="root"` elementga render qiladi:

```java
package app;

import jreact.web.JReactWeb;

public class WebMain {
    public static void main(String[] args) {
        JReactWeb.createRoot("root").render(
            <div>
                <h1>Salom, JReact!</h1>
                <Counter label="Bosildi" />
            </div>
        );
    }
}
```

**4. Sahifa** (`index.html`): TeaVM `main()` funksiyasini yaratadi, uni o'zingiz chaqirasiz:

```html
<div id="root"></div>
<script src="app.js"></script>
<script>main();</script>
```

**5. Yig'ing va oching:**

```bash
mvn -B -q package
python3 -m http.server -d target/web 8080     # yoki istalgan statik server
# http://localhost:8080
```

Natija: `target/web/{index.html, app.js}`. `app.js` ~120 KB (gzip bilan ~45 KB), minifikatsiya qilingan. `index.html` va `app.js` ni istalgan statik hostingga qo'ying.

**`jsxj:compile` plagini:** `pom.xml` dagi `jreact-maven-plugin` `generate-sources` bosqichida `src/main/jsxj` ichidagi `.jsxj` ni `target/generated-sources/jsxj` ga `.java` qilib yozadi va uni compile papkalariga qo'shadi. Komponent `Props`'lari loyihaning `.jsxj`/`.java` fayllaridan va `jreact-core` jar'i ichidagi manbalardan (u o'z `.java` fayllarini jar'ga qo'shadi) olinadi, shuning uchun `ErrorBoundary` qo'shimcha sozlamasiz ishlaydi. Xato bo'lsa build `jsxjc` xabari bilan (fayl:qator:ustun) to'xtaydi. Sozlamalar:

```xml
<plugin>
    <groupId>dev.jreact</groupId>
    <artifactId>jreact-maven-plugin</artifactId>
    <version>${jreact.version}</version>
    <executions>
        <execution>
            <id>jsxj</id>
            <goals><goal>compile</goal></goals>
            <configuration>
                <!-- ixtiyoriy -->
                <sourceDirectories><directory>${project.basedir}/src/main/jsxj</directory></sourceDirectories>  <!-- .jsxj papkalari -->
                <indexDirectories><directory>../shared/src/main/java</directory></indexDirectories>             <!-- faqat Props qidiriladi -->
                <outputDirectory>${project.build.directory}/generated-sources/jsxj</outputDirectory>
                <skip>false</skip>
            </configuration>
        </execution>
    </executions>
</plugin>
```

> Dev server (`./build.sh dev`) faqat `examples/` ilovalari uchun. O'z ilovangizda o'zgartirgach `mvn -B -q package` qilib sahifani yangilang.

## `.jsxj` sintaksisi

`.jsxj` fayl oddiy Java fayl, ichida JSX ifodalari bor. Kompilyator Java kodini o'zgartirmay nusxalaydi, faqat JSX'ni runtime chaqiruvlariga aylantiradi (Babel JSX'ni JS'ga aylantirgani kabi).

JSX ifoda yozish mumkin bo'lgan joyda yoziladi. Aniqrog'i, `<` quyidagilardan keyin kelsa JSX deb o'qiladi: `=`, `(`, `,`, `?`, `:`, `{`, `[`, `&`, `|`, `->`, `return`, `yield`. Ko'p qatorli JSX'ni qavs ichiga oling: `return ( <div>...</div> );`. `List<String>`, `a < b`, satr va izohlar ichidagi `<` JSX hisoblanmaydi.

| Yozuv | Ma'nosi |
|---|---|
| `<div className="a">` | Oddiy atribut (satr qiymati) |
| `<div title={expr}>` | Atribut qiymati Java ifodasi |
| `<input disabled />` | Qiymatsiz atribut, ya'ni `true` |
| `<li key={item.id()}>` | `key`: ro'yxat elementlarini aniqlash uchun |
| `<div ref={ref}>` | `Ref` ni elementga bog'lash |
| `<div {...attrs}>` | `Attrs` ni yoyish (faqat oddiy HTML elementlarda) |
| `<>...</>` | Fragment |
| `{expr}` | Bola sifatida Java ifodasi (satr, element, `List`, `null`) |
| `{/* izoh */}` | JSX izohi, natijaga tushmaydi |
| `&lt; &gt; &amp; &quot; &apos; &nbsp; &times; &copy; &hellip; &mdash; &ndash; &middot; &laquo; &raquo; &larr; &rarr; &#65; &#x41;` | HTML entity'lar dekodlanadi |

HTML atributlari DOM'ga React nomlari bilan beriladi: `className` (`class` bo'ladi), `htmlFor` (`for`), `style`, `value`, `checked`, `disabled` va hokazo. Boolean atribut `true` bo'lsa yoziladi, `false` bo'lsa olib tashlanadi.

**Shartli render va ro'yxatlar** oddiy Java bilan:

```java
{todos.isEmpty()
    ? <p>Hozircha vazifa yo'q</p>
    : <ul>
        {todos.stream().map(t -> <TodoItem key={t.id()} todo={t} />).toList()}
      </ul>}

{left == 0 ? <em>Hammasi bajarildi</em> : null}
```

**Bo'sh joy qoidasi React'dagi kabi:** matn qatorlari trim qilinadi, bo'sh qatorlar tashlanadi, qolgan qatorlar bitta probel bilan birlashtiriladi. Qator ichidagi probellar saqlanadi (`a <b>b</b> c`).

## Komponentlar va props

Komponent `Component<P>` dan meros oladi va `render(P props)` ni yozadi. Props **komponent klassi ichidagi `public record Props(...)`** bo'lishi shart. Kompilyator shu e'londan prop nomlari va tiplarini oladi.

```java
public class TodoItem extends Component<TodoItem.Props> {

    public record Props(Todo todo, Consumer<Long> onToggle, Consumer<Long> onDelete) {}

    @Override
    public Element render(Props props) { ... }
}
```

Ishlatilishi:

```java
<TodoItem key={t.id()} todo={t}
          onToggle={id -> toggle(id)}
          onDelete={id -> remove(id)} />
```

Qoidalar:

- Propslar record konstruktoriga **e'lon tartibida** uzatiladi. Berilmagan propga default qiymat qo'yiladi (`null`, `0`, `false`...).
- Prop tipi noto'g'ri bo'lsa (masalan `value={"uch"}` `int` o'rniga), buni `javac` tutadi. Xabardagi qator raqami `.jsxj` fayldagi qator bilan bir xil.
- Komponentda **argumentsiz konstruktor** bo'lishi kerak (kompilyator `TodoItem::new` ishlatadi). Maxsus konstruktor yozmasangiz, bu o'z-o'zidan bajariladi.
- Komponent nomi bosh harf bilan (`<Card>`), HTML teglar kichik harf bilan (`<div>`).
- `key` va `{...spread}` props'ga kirmaydi. `{...spread}` komponentlarda qo'llab-quvvatlanmaydi, propslarni aniq yozing.
- Komponentlar `.jsxj` yoki oddiy `.java` faylda bo'lishi mumkin. Ammo JSX faqat `.jsxj` da ishlaydi. `.java` da elementni `Jsx.el("div", null, null, "matn")` bilan qo'lda yasaysiz.

### Children

Ichma-ich JSX qabul qilish uchun Props'ga `List<Object> children` nomli maydon qo'shing:

```java
public class Card extends Component<Card.Props> {

    public record Props(String title, List<Object> children) {}

    @Override
    public Element render(Props props) {
        return (
            <div className="card">
                <h2>{props.title()}</h2>
                <div className="card-body">{props.children()}</div>
            </div>
        );
    }
}
```

```java
<Card title="Statistika">
    <Counter label="Jami" value={3} />
    <em>Salom</em>
</Card>
```

Props'da `children` bo'lmasa va komponentga bola berilsa, kompilyator xato beradi.

## Hooks

Hooks `Component` ichidagi `protected` metodlar. Ular `render()` ichida, **shartsiz va har safar bir xil tartibda** chaqirilishi kerak (React qoidasi bilan bir xil).

| Hook | Tavsif |
|---|---|
| `State<T> useState(T initial)` | State. O'qish: `get()`, yozish: `set(v)`, funksiya bilan: `update(old -> new)` |
| `void useEffect(Runnable effect, Object... deps)` | Effekt. `deps` yo'q = har renderdan keyin, `ONCE` = faqat birinchi renderdan keyin, qiymatlar = o'zgarganda |
| `void useEffect(Effect effect, Object... deps)` | Cleanup qaytaradigan effekt: `() -> { sub(); return () -> unsub(); }` |
| `T useMemo(Supplier<T> compute, Object... deps)` | Qimmat hisobni eslab qolish. `deps` qoidasi `useEffect` bilan bir xil |
| `Ref<T> useRef(T initial)` | O'zgaruvchan quti: `ref.current` |
| `T useContext(Context<T> ctx)` | Eng yaqin provider qiymati, bo'lmasa default. Hook tartibiga kirmaydi, shartli chaqirsa bo'ladi |

```java
State<Integer> count = useState(0);

useEffect(() -> System.out.println("count = " + count.get()), count.get());

return <button onClick={e -> count.update(c -> c + 1)}>+1 ({count.get()})</button>;
```

Qoidalar:

- `deps` React'dagi kabi: berilmasa har renderda, `ONCE` (React'dagi `[]`, bo'sh massiv) berilsa faqat birinchi renderda, qiymatlar berilsa `equals` bilan solishtiriladi. `useEffect(fn, ONCE)` mount'da ishlaydi, cleanup'i unmount'da chaqiriladi.
- `State.get()` render paytidagi qiymatni qaytaradi (React closure kabi).
- Render paytida `set` chaqirish xato. Bir xil qiymatga `set` qilish render'ni ishga tushirmaydi.
- Effektlar commit'dan keyin alohida vazifada ishlaydi. Keyingi render boshlanishidan oldin kutayotgan effektlar yakunlanadi.
- `useEffect` ichida har renderda `set` chaqirsangiz, 50 marta takrorlangach `Maximum update depth exceeded` xatosi chiqadi.
- `ServerRenderer` bir martalik render qiladi: `useState` boshlang'ich qiymatini qaytaradi, `set/update` hech narsa qilmaydi, effektlar ishlamaydi.

## Context va memo

**Context** props'ni har bir darajadan o'tkazmasdan qiymat berish uchun. Bir marta e'lon qilinadi, tepada `provide` qilinadi, pastda `useContext` bilan o'qiladi. Provider oddiy komponent, shuning uchun `.jsxj` da ham ishlaydi:

```java
public static final Context<Theme> THEME = Context.create(Theme.LIGHT);

public class ThemeProvider extends Component<ThemeProvider.Props> {
    public record Props(Theme value, List<Object> children) {}

    @Override
    public Element render(Props p) { return THEME.provide(p.value(), p.children()); }
}
```

```java
<ThemeProvider value={Theme.DARK}>
    <Page />
</ThemeProvider>
```

```java
Theme theme = useContext(THEME);   // Page ichidagi istalgan komponentda
```

Provider qiymati o'zgarsa (`equals`), uni o'qigan komponentlar qayta render bo'ladi, oraliqdagi `MemoComponent` render bo'lmasa ham. Ichma-ich provider o'z ichki daraxti uchun qiymatni almashtiradi.

**memo.** `MemoComponent<P>` dan meros oling: props oldingisiga teng bo'lsa render o'tkazib yuboriladi. Props record bo'lgani uchun `equals` maydonma-maydon solishtiradi.

```java
public class Row extends MemoComponent<Row.Props> {
    public record Props(String title, int count) {}
    ...
}
```

React'dagi kabi: har renderda yangi obyekt bo'ladigan props (inline lambda, `children`) taqqoslashni buzadi, barqaror qiymat bering. Boshqacha solishtirish uchun `arePropsEqual(prev, next)` ni override qiling. Memo komponent o'z state'i va o'qigan context'i o'zgarganda baribir render bo'ladi.

## Eventlar

`on` + bosh harf bilan boshlanadigan atributlar event handler hisoblanadi (`onClick`, `onChange`, `onInput`, `onSubmit`...). Qiymati lambda bo'lishi shart (`EventHandler`). Satr yozsangiz (`onClick="go()"`) kompilyator xato beradi.

```java
<button onClick={e -> save()}>Saqlash</button>
<input onInput={e -> draft.set(e.value())} />
<form onSubmit={e -> { e.preventDefault(); add(); }}> ... </form>
```

Handlerga beriladigan `Event`:

| Metod | Ma'nosi |
|---|---|
| `type()` | Event turi |
| `value()` | Target input'ning joriy qiymati (yoki `null`) |
| `checked()` | Checkbox holati |
| `preventDefault()` | Standart xatti-harakatni to'xtatish |
| `stopPropagation()` | Yuqoriga tarqalishni to'xtatish |

Qo'llab-quvvatlanadi: `onClick`, `onDoubleClick`, `onContextMenu`, `onMouseDown/Up/Move/Over/Out`, `onPointerDown/Up/Move/Cancel`, `onTouchStart/Move/End`, `onKeyDown/Up/Press`, `onInput`, `onChange`, `onSubmit`, `onReset`, `onFocus`, `onBlur`, `onScroll`, `onWheel`, `onCopy/Cut/Paste`, `onDrag`, `onDragStart/End/Enter/Leave/Over`, `onDrop`. Eventlar konteynerda bitta listener bilan ushlanadi (delegation).

`onChange` React'dagi kabi: matn maydonida har bosishda (`input`), checkbox va select'da `change` da ishlaydi.

## Router

`jreact-router` react-router v6'ga o'xshash: sahifani qayta yuklamasdan manzil bo'yicha komponent almashtiradi. `jreact-web` unga bog'liq, shuning uchun alohida bog'liqlik qo'shish shart emas. Kompilyator `<Routes>`, `<Link>` kabi teglarning `Props`'ini `jreact-router` jar'idan o'zi topadi.

```java
import jreact.router.*;
import jreact.web.HashRouter;           // yoki BrowserRouter

JReactWeb.createRoot("root").render(
    <HashRouter>
        <Routes>
            <Route path="/" element={<Layout />}>
                <Route index element={<Home />} />
                <Route path="posts" element={<PostList />} />
                <Route path="posts/:id" element={<PostPage />} />
                <Route path="*" element={<NotFound />} />
            </Route>
        </Routes>
    </HashRouter>
);
```

`Layout` ichida `<Outlet />` bolalar-sahifa chiqadigan joyni ko'rsatadi, `<Link to="/posts">Posts</Link>` sahifani yuklamasdan o'tadi:

```java
public class PostPage extends Component<PostPage.Props> {
    public record Props() {}
    public Element render(Props props) {
        String id = useParams().get("id");            // import static jreact.router.RouterHooks.*;
        Navigator go = useNavigate();
        return <button onClick={e -> go.navigate("/posts")}>Back to {id}</button>;
    }
}
```

### Router turlari

| Router | Manzil | Qachon |
|---|---|---|
| `<HashRouter>` | `index.html#/posts/7` | Istalgan statik hosting (GitHub Pages, `python3 -m http.server`). Server `#` dan keyingisini ko'rmaydi |
| `<BrowserRouter>` | `/posts/7` | Chiroyli manzil. **Server har bir yo'lga `index.html` qaytarishi kerak** (SPA fallback). Dev server (`./build.sh dev`) buni qiladi |
| `<Router history={...}>` | xotirada | Testlar va JVM'da HTML render: `new MemoryHistory("/posts/7")` |

`basename` ilova pastki papkada turganda: `<BrowserRouter basename="/docs">`. Marshrutlar va `<Link>`lar uni ko'rmaydi, brauzer manzilida esa bor. Boshqa yo'lda router hech narsa chiqarmaydi.

### Komponentlar

| Komponent | Vazifasi |
|---|---|
| `<Routes>` | `<Route>`lardan eng mos keladiganini tanlab `element`'ini chiqaradi. Mos kelmasa hech narsa (404 uchun `path="*"`) |
| `<Route path element>` | Marshrut tavsifi, o'zi hech narsa chiqarmaydi. `index` (ota yo'lida), `path` yo'q (layout), ichiga boshqa `<Route>` (ichma-ich) |
| `<Outlet />` | Ota marshrut elementida mos kelgan bola marshrut chiqadigan joy |
| `<Link to>` | `<a>`: haqiqiy `href` bor (yangi tabda ochish ishlaydi), oddiy bosish sahifani yuklamaydi. Ctrl/Cmd/Shift-bosish va `target="_blank"` brauzerga qoldiriladi. `replace`, `className`, `id`, `title` |
| `<NavLink to>` | `<Link>` + joriy sahifada `activeClassName` qo'shadi va `aria-current="page"` qo'yadi. O'z yo'li va uning ostidagilar uchun faol, `end` bilan faqat aniq yo'l. `to="/"` doim `end` |
| `<Navigate to replace />` | Chiqarilganda yo'naltiradi (kirmagan foydalanuvchini `/login`ga). `replace` bilan "orqaga" yo'naltirgan sahifaga qaytarmaydi |

### Hooks

`import static jreact.router.RouterHooks.*;` bilan ishlatiladi, render paytida chaqiriladi.

| Hook | Qaytaradi |
|---|---|
| `useLocation()` | `Location(pathname, search, hash)`, basename'siz |
| `useParams()` | `Map<String,String>`: `posts/:id` dan `id`, splat uchun `"*"`. Ota marshrutlarnikini ham o'z ichiga oladi |
| `useNavigate()` | `Navigator`: `navigate(to)`, `navigate(to, replace)`, `back()`, `forward()`, `go(n)`. Render'lar orasida bir xil obyekt (`useEffect` deps'iga bersa bo'ladi) |
| `useSearchParams()` | `SearchState`: `params()` (`get`, `getAll`, `has`, `with`, `without`) va `set(params)` / `replace(params)` |

### Yo'l qoidalari

- **Naqsh:** statik bo'lak (katta-kichik harf farqlanmaydi), `:param`, oxirida `*` (qolgan hammasini `"*"` param'iga oladi).
- **Tanlash tartibi:** yozilish tartibi emas, o'ziga xoslik: `users/new` > `users/:id` > `users/*` > `*`. Teng bo'lsa avval yozilgani. Index marshrut layout'dan kuchli.
- **Nisbiy havolalar** joriy **marshrut** yo'liga nisbatan: `posts/:id` ichida `to="edit"` bu `/posts/7/edit`, `to="../8"` bu `/posts/8`, `to="."` bu `/posts/7`. `/` bilan boshlansa mutlaq.
- **Faqat query:** `to="?tag=java"` yo'lni saqlab query'ni almashtiradi: `<Link to={"?" + params.with("tag", "java")}>`. Bo'sh query (`"?"`) olib tashlanadi.
- **Ichma-ich `<Routes>`:** `path="account/*"` marshrutning elementi ichida yana `<Routes>` bo'lsa, u yo'lning qolgan qismini moslaydi.
- **Ota yo'li:** ichki marshrut mutlaq yo'l (`/users/:id`) yozsa u ota yo'li ostida bo'lishi shart, aks holda `IllegalArgumentException`.
- **Holat saqlanishi:** bir layout ostidagi bola marshrutlar orasida o'tganda layout qayta yaratilmaydi, state saqlanadi.
- **URL'dagi qiymat:** param'lar va query foydalanuvchi kiritmasi: `%20` kabi kodlar ochiladi (UTF-8). Matn sifatida chiqarish xavfsiz ([Xavfsizlik](#xavfsizlik-xss)).

### Himoyalangan sahifa (redirect)

```java
public class RequireAuth extends Component<RequireAuth.Props> {
    public record Props(List<Object> children) {}
    public Element render(Props props) {
        Auth auth = useContext(Auth.CONTEXT);
        String wanted = useLocation().pathname();
        if (!auth.signedIn()) return <Navigate to={"/login?next=" + Auth.encodePath(wanted)} replace />;
        return <>{props.children()}</>;
    }
}
// <Route path="account" element={<RequireAuth><Account /></RequireAuth>}> ...
```

Login'dan keyin `go.navigate(useSearchParams().params().get("next", "/account"), true)`.

### Namuna va testlar

`examples/blog` ("Dispatch"): ichma-ich marshrutlar, `:id`, query'dagi filtr, `NavLink`, himoyalangan `/account` (layout + `Outlet`), `/login?next=` redirect, ikki xil 404. `./build.sh dev` da `/blog/`. `./build.sh test` da `RouterTest` (20 ta: moslash va tartib, nisbiy yo'llar, `SearchParams`, SSR, soxta DOM'da bosish/orqaga/redirect/basename) va `examples-test` da blog uchun 30 dan ortiq jsdom tekshiruvi.

JVM'da (SSR, test): `ServerRenderer.renderToString(<Router history={new MemoryHistory("/posts/7")}>...</Router>)`. Effektlar ishlamagani uchun `<Navigate>` SSR'da yo'naltirmaydi.

### Data router: `createBrowserRouter`, loader, action

Yuqoridagi `<Routes>` sahifani avval chizadi, ma'lumotni keyin yuklaydi (spinner, bo'sh holat). **Data router** teskari: marshrutning `loader`'i sahifa chizilishidan *oldin* ma'lumotni olib keladi, forma `action`'ga boradi, action tugagach loader'lar qayta ishlaydi. Eski sahifa ekranda turadi va URL faqat yangi ma'lumot tayyor bo'lganda o'zgaradi. API react-router v6.4+ ga o'xshash.

```java
import jreact.router.*;
import jreact.web.Routers;

List<RouteObject> routes = List.of(
    RouteObject.of("/").element(<Root />).errorElement(<ErrorPage />)
        .loader(a -> Contacts.search(a.request().searchParams().get("q")))     // useLoaderData()
        .action(a -> Redirect.to("/contacts/" + Contacts.create().id() + "/edit"))
        .children(
            RouteObject.indexRoute().element(<Welcome />),
            RouteObject.of("contacts/:id").element(<ContactPage />)
                .loader(a -> Contacts.find(a.params().get("id")))
                .action(a -> { Contacts.update(a.params().get("id"), a.request().form()); return null; })));

JReactWeb.createRoot("root").render(<RouterProvider router={Routers.createBrowserRouter(routes)} />);
```

JSX'ni yoqtirsangiz: `Routers.createRoutesFromElements(<Route path="/" loader={...} ...>...</Route>)`. `<Route>`ga `id`, `loader`, `action`, `errorElement`, `shouldRevalidate`, `handle` berish mumkin.

| Nima | Vazifasi |
|---|---|
| `Routers.createBrowserRouter(routes[, basename])`, `createHashRouter(routes)` | `jreact-web`: brauzer manzili. JVM'da va testda: `Routers.createMemoryRouter(routes, "/start")` |
| `<RouterProvider router fallbackElement>` | Router'ni chizadi. Birinchi sahifa loader'lari tugaguncha `fallbackElement` |
| `Loader` / `Action` | `a -> ...`: `a.params()`, `a.request()` (`method`, `url`, `searchParams()`, `form()`), `a.signal()`. Qiymat, `Promise`, `Redirect` qaytaradi yoki istisno tashlaydi |
| `Promise<T>` | Kutadigan loader uchun: `Promise.pending()`, keyin `resolve(v)` / `reject(e)` (timer yoki fetch ichidan). `then`, `thenCompose`, `recover`, `all` |
| `Redirect.to("/login")` | Loader/action'dan qaytarilsa yoki tashlansa navigatsiya o'sha yerda tugaydi, yo'naltirgan sahifa tarixda qolmaydi |
| `ErrorResponse(404, "Not Found", data)` | "Topilmadi", "Ruxsat yo'q" sahifalari. Router o'zi 404 (marshrut yo'q) va 405 (action yo'q) beradi |
| `errorElement` | Marshrut, bolasi yoki ularning loader/action'i xato qilsa elementi o'rniga chiqadi. Eng yaqin `errorElement` olinadi, yo'q bo'lsa ildiz marshrut. Ota-ona ma'lumoti saqlanadi, tartibsiz qismlar chizilmaydi |
| `<Form method="post" action>` | Sahifasiz yuboradi. `post/put/patch/delete` action'ni ishlatib, keyin loader'larni qayta yuklaydi. `get` (standart) maydonlarni query'ga qo'yib o'tadi (qidiruv formasi). `action` Link kabi nisbiy |

Hook'lar (`import static jreact.router.RouterHooks.*;`):

| Hook | Qaytaradi |
|---|---|
| `useLoaderData()`, `useRouteLoaderData(id)`, `useActionData()` | Joriy marshrutning loader/action natijasi; boshqa marshrutniki `id` bilan |
| `useRouteError()` | `errorElement` ichida: tashlangan narsa (`ErrorResponse` yoki istisno) |
| `useNavigation()` | `state()`: `IDLE`, `LOADING`, `SUBMITTING`, `location()`: qayerga ketayapti. "Yuklanmoqda" belgisi uchun |
| `useSubmit()` | Koddan forma yuborish: `submit.submit(Map.of("q", v), new Submitter.Options("get", "/", true))` |
| `useFetcher()` | Navigatsiyasiz `load(to)` / `submit(fields, action, method)`: yurakcha, "bajarildi" belgisi. `data()`, `state()`, `formData()` (optimistik UI). `<Form fetcher={f}>` bilan ham ishlaydi |
| `useRevalidator()` | `revalidate()`: hamma loader'larni qayta yuklash |
| `useBlocker(shouldBlock)` | Saqlanmagan o'zgarishda chiqishni ushlaydi: `blocked()`, `proceed()`, `reset()`. Faqat ilovadagi navigatsiya (`Link`, `navigate`, forma); brauzerning orqaga tugmasini to'xtatib bo'lmaydi |
| `useMatches()`, `useNavigationType()`, `useMatch(pattern)`, `useOutletContext()` | Mos kelganlar (`handle` bilan: breadcrumb), `POP/PUSH/REPLACE`, naqsh bo'yicha moslash, `<Outlet context={...}/>` qiymati |
| `<ScrollRestoration />` | Ildiz layout'ga bitta: yangi sahifada tepaga (yoki `#hash` elementiga), orqaga/oldinga o'tganda eski joyga |

Qoidalar:

- **Qayta yuklash:** navigatsiyada marshrut loader'i faqat yangi bo'lsa, param'lari yoki query'si o'zgarsa ishlaydi, o'zgarmagan ota marshrutlar eski ma'lumotni saqlaydi. Har bir action'dan keyin (va action'dan redirect qilingan sahifada ham) hammasi qayta ishlaydi. `shouldRevalidate(a -> false)` bilan oldini olish mumkin.
- **Bekor qilish:** yangi navigatsiya eskisini to'xtatadi (`a.signal().onAbort(...)`), kech kelgan natija e'tiborga olinmaydi.
- **Action kimga boradi:** URL'ga mos eng chuqur marshrutga, lekin index marshrut faqat `?index` bilan (`/` ga POST ota layout'ning action'iga boradi, react-router kabi; `<Form>` index marshrut ichida o'zi `?index` qo'shadi). Marshrutda action bo'lmasa va tepada yo'lsiz layout'lar bo'lsa, shu yo'ldagi eng yaqin action topiladi.
- **Tashqi bog'lanish:** `Event.formData()` formaning maydonlarini URL-kodlangan qilib beradi (fayl yo'q). Brauzerda `FormData`, JVM'da testda o'zingiz berasiz.
- **Tarix:** URL navigatsiya tugaganda yoziladi; redirect eski sahifani tarixda qoldirmaydi.

Misol: `examples/contacts` (`/contacts/`): qidiruv (GET forma, tarixni to'ldirmaydi), yurakcha (fetcher, optimistik), tahrirlash (action + redirect), yaratish (ildiz action), o'chirish, `errorElement` bilan 404, yuklanayotganda xira sahifa. `./build.sh test` da `DataRouterTest` (31 ta, DOM'siz `DataRouter` va soxta DOM'da `RouterProvider`) va `examples-test` da contacts uchun 23 jsdom tekshiruvi.

### Lazy marshrutlar va bundle hajmi

`RouteObject.of("reports").lazy(() -> Promise.resolved(Reports.route()))` (yoki `<Route lazy={...}>`): marshrutning `element`, `loader`, `action`, `errorElement`, `handle`, `shouldRevalidate` qismlari marshrut birinchi marta mos kelganda olinadi (bir marta ishlaydi, natija saqlanadi). Kutish paytida `useNavigation()` `LOADING`. O'zida bor xossa lazy'dagidan ustun; `path`, `index`, `id`, `children` lazy'dan kelmaydi (router URL moslash uchun daraxt shaklini oldindan bilishi kerak).

**Bu bundle hajmini kamaytirmaydi.** TeaVM bitta `app.js` chiqaradi (chunk'larga bo'lib, kerak bo'lganda yuklash yo'q), lazy marshrut kodi ham hamma narsa bilan birga yuklanadi. Foyda: og'ir qismlarni yasashni yoki asinxron olishni kechiktirish. Haqiqiy kod bo'lish uchun faqat sahifalarni alohida TeaVM dastur qilib `<script>` bilan yuklash qoladi, lekin har biri o'z runtime'ini (~150 KB) olib keladi va ular orasida obyekt almashib bo'lmaydi, shuning uchun qo'shilmadi.

**Tree shaking** avtomatik: TeaVM butun dasturni tahlil qilib ishlatilmagan sinf va metodlarni tashlaydi (`optimizationLevel=FULL`, `minifying=true`). Hajmga ta'sir qiladigan narsa: `String.split`/`replaceAll`/`matches` (regex, ~100 KB), `getBytes`/`new String(bytes, charset)` (charset'lar, ~50 KB), `String.format` (TeaVM'da yo'q). Router shularsiz yozilgan: router bor ilova (blog, contacts) ~310 KB, routersiz (kanban) ~164 KB. O'z kodingizda ham shulardan qoching.

**Hali yo'q:** `defer`/`<Await>`/Suspense (loader'ning bir qismini kutmay chizish), orqaga tugmasini bloklash (`useBlocker` faqat ilova ichidagini ushlaydi), `Location.state` / `key` (`Link state`), `caseSensitive`, ixtiyoriy param (`:id?`), `useFormAction`, fayl yuklash formalari. Scroll tiklash joyni URL bo'yicha saqlaydi (bir URL'ga ikki marta kirsangiz bir xil joy).

## Stillar: CSS, Sass, Tailwind

Ha, hammasini ishlatsa bo'ladi. JReact CSS'ni bilmaydi va unga tegmaydi: u faqat `className` va `style` ni DOM'ga beradi. CSS esa oddiy sahifa resursi, shuning uchun istalgan CSS vositasi ishlaydi.

| Usul | Holat |
|---|---|
| Oddiy CSS | ishlaydi |
| Sass / SCSS | ishlaydi (`sass` bilan `.css` ga yig'iladi) |
| Tailwind CSS (v4) | ishlaydi (Tailwind CLI) |
| Inline `style` | ishlaydi |
| CSS Modules, CSS-in-JS | yo'q (JS bundler kerak, bu yerda bundler yo'q) |

Sass va Tailwind'ni haqiqatan sinab ko'rdim: andoza ilovada `.scss` ning o'zgaruvchi va ichma-ich qoidalari to'g'ri `.css` ga aylandi, Tailwind CLI `.jsxj` dagi klasslarni topib CSS yaratdi, DOM'da klasslar to'g'ri qo'yildi va state o'zgarganda almashdi (jsdom'da; haqiqiy brauzerda tashqi ko'rinish ko'rilmagan).

### Oddiy CSS

`.css` faylni `src/main/webapp/` ga qo'ying (Maven shu papkani butunlay `target/web` ga ko'chiradi) va `index.html` ga ulang:

```html
<link rel="stylesheet" href="app.css">
```

```java
<div className="card"> <h2 className="title">Salom</h2> </div>
```

`className` DOM'da `class` bo'ladi, `htmlFor` esa `for`. Shartli klass oddiy Java bilan: `className={done ? "todo done" : "todo"}`.

### Inline style

`style` ga `Map` bering. Nomlar camelCase yoziladi, sonlar `px` oladi (`0` va `opacity` kabilar yo'q), React kabi:

```java
<p style={Map.of("color", "tomato", "fontSize", 20)}>matn</p>
// <p style="color: tomato; font-size: 20px;">
```

Satr ham ishlaydi: `style="color:red"` (u atribut sifatida qo'yiladi). Map ko'rinishi yangilanishda faqat o'zgargan xususiyatlarni almashtiradi, shuning uchun dinamik stil uchun Map afzal.

### Sass / SCSS

```bash
npm install --save-dev sass
npx sass src/main/styles/app.scss target/web/app.css          # bir marta
npx sass --watch src/main/styles/app.scss target/web/app.css  # o'zgarsa o'zi
```

`index.html` ga `<link rel="stylesheet" href="app.css">` qo'shing. `.sass` (chekinish sintaksisi) ham shu buyruq bilan yig'iladi.

### Tailwind CSS (v4)

```bash
npm install --save-dev tailwindcss @tailwindcss/cli
```

`src/main/styles/tw.css`:

```css
@import "tailwindcss";
@source "../jsxj";
```

```bash
npx @tailwindcss/cli -i src/main/styles/tw.css -o target/web/tw.css --minify
npx @tailwindcss/cli -i src/main/styles/tw.css -o target/web/tw.css --watch    # ishlab chiqishda
```

```java
<button className="px-4 py-2 rounded bg-blue-500 text-white hover:bg-blue-600">Saqlash</button>
```

- Tailwind klass nomlarini manba fayldan **matn sifatida** qidiradi, shuning uchun `.jsxj` ham, `.java` ham ishlaydi (`@source` papkasini ko'rsatish kifoya).
- JS'dagi kabi klass **to'liq satr** bo'lishi kerak: `cond ? "bg-blue-500" : "bg-red-500"` topiladi, `"bg-" + color` topilmaydi (CSS'ga tushmaydi).
- Tailwind v3 uchun `tailwind.config.js` da `content: ["./src/main/jsxj/**/*.jsxj"]` beriladi (v3 sinab ko'rilmagan).

### Build bilan birga ishlatish

Sass/Tailwind Maven build'iga hali ulanmagan, ularni `mvn package` dan **keyin** yurgizing. `mvn clean package` `target/web` ni o'chiradi, CSS'ni undan keyin qayta yig'ing:

```bash
mvn -B -q clean package && npx sass src/main/styles/app.scss target/web/app.css
```

Maven ichiga olish uchun `exec-maven-plugin` yoki `frontend-maven-plugin` ishlatiladi. Tailwind namunalarida (`examples/kanban` va boshqalar) `./build.sh dev` Maven'dan keyin Tailwind CLI'ni o'zi yurgizadi. Sass ulanmagan.

## Namunalar: Tailwind bilan 6 ta ilova

`examples/` ichida Tailwind CSS v4 bilan yozilgan 5 ta real ilova bor. Hammasi `.jsxj`, brauzerda TeaVM orqali ishlaydi.

| Ilova | Nima ko'rsatadi |
|-------|-----------------|
| `examples/kanban` | 3 ustunli doska: `useState` bilan ro'yxat, controlled input va select, kartani ko'chirish/o'chirish, `style` bilan progress bar |
| `examples/shop` | Do'kon: 12 mahsulot, qidiruv, kategoriya, saralash (`useMemo`), savat `Context` orqali (`Cart.CONTEXT`), yon panel, yetkazish narxi hisobi |
| `examples/dashboard` | Admin panel: statistika kartalari, bar chart, saralanadigan/filtrlanadigan jadval, dark mode (`dark:` variantlari), `ErrorBoundary` ichidagi vidjet |
| `examples/landing` | Landing sahifa: gradient hero, narx jadvali (oylik/yillik), FAQ akkordeon, mobil menyu, validatsiyali kontakt forma |

Yig'ish va ko'rish:

```bash
./build.sh examples        # Maven + TeaVM, keyin Tailwind CLI -> examples/dist/
./build.sh examples-test   # shuni yig'ib, 6 ilovani jsdom'da bosib tekshiradi (128 tekshiruv)
python3 -m http.server -d examples/dist 8080     # http://localhost:8080
```

Har ilova: `src/main/jsxj` (komponentlar), `src/main/java` (ma'lumot modellari), `src/main/styles/app.css` (`@import "tailwindcss"; @source ...`), `src/main/webapp/index.html`. Maven `.jsxj` ni kompilyatsiya qilib `app.js` yasaydi, Tailwind CLI esa `.jsxj`/`.java` ichidagi klass satrlarini topib `app.css` yasaydi. Umumiy sozlama `examples/pom.xml` da (4 ilova shuni meros qiladi), Tailwind va jsdom `examples/package.json` da.

## Error boundary

Bitta komponent render paytida xato tashlasa, butun ilova yiqilmasligi uchun `ErrorBoundary` ishlatiladi. U ichidagi bolalar xatosini tutadi va o'rniga `fallback` ko'rsatadi, qolgan ilova (aka-uka komponentlar va ularning state'i) tegmasdan qoladi:

```jsx
import jreact.ErrorBoundary;

<ErrorBoundary fallback={(err, reset) ->
    <div className="error">
        Xato: {err.getMessage()}
        <button onClick={e -> reset.run()}>Qayta urinish</button>
    </div>
}>
    <Widget />
</ErrorBoundary>
```

| Prop | Vazifasi |
|---|---|
| `fallback` | `(xato, reset) -> Element`. `null` bo'lsa hech narsa ko'rsatilmaydi |
| `onError` | `(xato, komponentStek) -> void`, log yuborish uchun. Commit'dan keyin bir marta chaqiriladi |
| `resetKeys` | `List<Object>`. Ro'yxat o'zgarsa xato holati tozalanadi va bolalar qayta uriniladi |
| `children` | himoyalanadigan daraxt |

- `reset.run()` xatoni tozalab, bolalarni qayta render qiladi.
- Boundary o'z `fallback`idagi xatoni tutolmaydi: u bir daraja yuqoridagi boundary'ga o'tadi. Boundary yo'q bo'lsa xato avvalgidek `flush()` dan chiqadi, commit qilingan daraxt buzilmaydi.
- Xato dev konsolga ham yoziladi (`Error caught by an error boundary: ... in Bomb > ErrorBoundary`), `setDevMode(false)` uni o'chiradi.
- Tutiladi: `render()`, hooks xatolari, `useEffect` (yaratish va tozalash). Tutilmaydi: event handler'lar, taymerlar.
- O'z boundary'ingiz uchun `Component` da `isErrorBoundary()`, `renderError(...)`, `didCatch(...)`, `shouldResetError(...)` ni override qiling.
- `ErrorBoundary` `jreact-core` ichida, shuning uchun kompilyatorga `jreact-core/src/main/java` ham indeks sifatida berilishi kerak (`build.sh`, `todo-web` va andoza `pom.xml` shunday qiladi).

## Ilovani ishga tushirish va ustuvorlik

`JReactWeb.createRoot(...)` `DomRoot` qaytaradi:

| Metod | Vazifasi |
|---|---|
| `render(element)` | UI'ni belgilaydi (async, batchlanadi) |
| `renderSync(element)` | darhol render va commit, effektlargacha |
| `unmount()` | hammasini olib tashlaydi, effekt cleanup'lari ishlaydi |
| `startTransition(Runnable)` | ichidagi `set` lar shoshilinch emas, bo'laklab render qilinadi |
| `reconciler()` | past darajadagi API: `runWithPriority`, `discreteUpdates`, `setDevMode`, `setWarningHandler` |

```java
DomRoot<JsNode> root = JReactWeb.createRoot("root");
root.render(<App />);

// shoshilinch bo'lmagan yangilanish (masalan katta ma'lumot kelganda): bo'laklab, yozishni to'smasdan
root.startTransition(() -> state.set(newData));
```

Hozircha komponent ichidan transition boshlash API'si yo'q: `startTransition` va `runWithPriority` `DomRoot`/`Reconciler` orqali tashqaridan chaqiriladi. Komponentdagi `set` lar NORMAL, eventlar ichidagilari `USER_BLOCKING` ustuvorlikda ishlaydi.

Ustuvorlik (`jreact.scheduler.Priority`): `IMMEDIATE`, `USER_BLOCKING` (click/input kabi diskret eventlar), `NORMAL` (default), `LOW` (transition), `IDLE`. Faqat `LOW`/`IDLE` bo'laklab render qilinadi (har bo'lak ~5 ms), brauzer orada eventlarni qayta ishlaydi. Shoshilinch update kelsa, to'xtatilgan render darhol yakunlanadi. Ustuvorlik root bo'yicha, update bo'yicha emas ([Cheklovlar](#cheklovlar)).

## JVM'da ishlatish (HTML render, test)

Brauzersiz ham ishlatish mumkin:

- **Bir martalik HTML:** `ServerRenderer.renderToString(element)` (SSR, snapshot testlar). `useState` boshlang'ich qiymatda qoladi, effektlar ishlamaydi. Namuna: `examples/todo` (`./build.sh`).
- **Jonli daraxt, o'z host'ingiz bilan:** `jreact-reconciler` platformadan mustaqil. `HostConfig<N>` ni amalga oshirsangiz (`createInstance`, `appendChild`, `commitUpdate`...), `new Reconciler<>(host, new Scheduler(schedulerHost))` bilan istalgan nishonga (terminal, test daraxti, boshqa UI) render qilasiz. `ManualSchedulerHost` virtual soat beradi: testlarda `clock.runUntilIdle()` bilan hamma vazifa bajariladi. Namuna: `jreact-reconciler/src/test/.../ReconcilerTest.java` (xotiradagi host).
- **DOM'ga o'xshash host:** `DomRoot.create(ops, container, schedulerHost)` (`jreact-dom`), brauzerda `DomOps` ni TeaVM JSO bajaradi (`jreact-web`).

## Ishlab chiqish jarayoni

### Dev server

```bash
./build.sh dev                    # hamma ilova: http://localhost:5173
./build.sh dev 3000               # boshqa port
./build.sh dev kanban shop        # faqat tanlangan ilovalar (tezroq boshlanadi)
```

Bitta portda galereya (`/`) va ilovalarni (`/todo/`, `/kanban/`, `/shop/`, `/dashboard/`, `/landing/`, `/blog/`, `/contacts/`) ko'rsatadi. Ilovaning `.jsxj`, `.java`, `.css` yoki `index.html` fayli o'zgarsa faqat shu ilova qayta yig'iladi (Maven + Tailwind, ~4 s) va uning sahifasi yangilanadi. `jreact-*` (framework) o'zgarsa hammasi qayta yig'iladi. Build xatosi brauzerda qizil sahifa bo'lib chiqadi, tuzatsangiz o'zi tiklanadi.

### VS Code

```bash
./editors/vscode-jsxj/install.sh      # keyin: Cmd+Shift+P -> Developer: Reload Window
```

- **Ranglash:** Java'ni VS Code'ning o'z grammar'i, JSX teglar, atributlar va `{...}` ni kengaytma ranglaydi.
- **Language server** (`jreact-lsp`, Java jarayoni, JDK'dan boshqa qaramligi yo'q):

| Imkoniyat | Qanday ishlaydi |
|---|---|
| Diagnostika | JSX xatolari (`jsxjc`) har bosishda aniq qator/ustun bilan. Java xatolari (`javac`) yozish to'xtagandan ~0.5 s keyin va saqlaganda. Generatsiya qilingan Java'da qator raqami `.jsxj` bilan bir xil, JSX'siz qatorlarda ustun ham aniq |
| Completion | `<` dan keyin komponentlar (`record Props` bor sinflar) va HTML teglar. Tag ichida prop'lar (berilganlari chiqmaydi, `key`, `ref` bilan), `name={}` shaklida qo'yiladi. `{...}` va string ichida taklif yo'q |
| Hover | Tag ustida `record Props(...)`, atribut ustida uning turi |
| Definition | Komponent tegidan sinf e'loniga o'tadi |

Sozlamalar (VS Code *Settings* da `jsxj.*`):

| Sozlama | Ma'nosi |
|---|---|
| `jsxj.javaPath` | `java` fayli (default `java`) |
| `jsxj.server.jar` | server jar'i (default: kengaytma ichidagi `server/jsxj-lsp.jar`) |
| `jsxj.sourceRoots` | javac va komponent qidiruvi uchun qo'shimcha `.java` papkalar. Workspace ichidagilar o'zi topiladi (`package` qatorlaridan) |
| `jsxj.classpath` | javac uchun jar/papkalar, masalan `jreact-core` jar'i, manbasi workspace'da bo'lmasa |

Loyihadagi barcha `.jsxj`/`.java` fayllar indekslanadi (`target`, `build`, `node_modules`, `test` papkalaridan tashqari: test sinflari bir xil nomlar bilan indeksni buzadi). Java ifodalari (`{...}` ichi) uchun avtokomplit va hover yo'q, bu Java til serveri ishi. Server `java -jar build/jsxj-lsp.jar` (stdio), LSP so'zlashadigan boshqa muharrir ham ishlata oladi. Batafsil: [`editors/vscode-jsxj`](editors/vscode-jsxj/README.md).

**IntelliJ IDEA:** *Settings → Editor → File Types → Java* ga `*.jsxj` naqshini qo'shing. Java qismlari rangli bo'ladi, JSX qismlarini IDE xato deb ko'rsatishi mumkin (plagin yo'q). Haqiqiy xatolarni `jsxjc` va `javac` beradi.

### Kompilyator xatolari

`jsxjc` xatolarni `javac` uslubida, fayl, qator va ustun bilan chiqaradi:

```
app/Bad.jsxj:3:1: error: expected </ul> to close <ul> opened at 1:5, but found </ol>
    </ol>;
    ^
```

Kompilyator topadigan xatolar: noto'g'ri yopilgan yoki yopilmagan teg, noma'lum prop (mavjud propslar ro'yxati bilan), noma'lum komponent, bola qabul qilmaydigan komponentga bola berish, takrorlangan prop, satr ko'rinishidagi event handler, bo'sh `{}` atribut qiymati, JSX matnidagi yakka `}`, yopilmagan `{`.

Tip xatolari (masalan `int` o'rniga `String`) `javac`dan keladi. U generatsiya qilingan `.java` faylni ko'rsatadi, lekin qator raqami `.jsxj` fayldagi bilan bir xil:

```
build/gen/app/TodoApp.java:43: error: incompatible types: String cannot be converted to int
```

### Dev-mode ogohlantirishlar

Reconciler ishga tushirish vaqtida ham React kabi tipik xatolarni xabar qiladi. Ular xatti-harakatni o'zgartirmaydi, har bir muammo bir marta chiqadi va komponent zanjirini ko'rsatadi:

```
Warning: Each child in a list should have a unique "key" prop, but <li> has none. Keys let the reconciler keep state and DOM nodes when the list changes.
    in TodoList > TodoApp
```

- Ma'lumotdan yasalgan ro'yxatda (`.map(...).toList()`) `key` yo'q.
- Bir xil ro'yxatda takrorlangan `key`.

`<Card>...</Card>` ichidagi qo'lda yozilgan JSX bolalari (`props.children()`) ro'yxat hisoblanmaydi, ularga `key` shart emas. Brauzerda ogohlantirishlar konsolga tushadi.

```java
root.reconciler().setDevMode(false);                      // production: o'chirish
root.reconciler().setWarningHandler(msg -> log.warn(msg)); // default: System.err (brauzerda console)
```

## Buyruqlar ma'lumotnomasi

| Buyruq | Vazifasi |
|---|---|
| `./build.sh` | JDK bilan hammasini yig'ib, `examples/todo` HTML'ini chiqaradi |
| `./build.sh test` | barcha testlar (bog'liqliksiz `main()` runner'lar, JUnit kerak emas) |
| `./build.sh web` | Todo'ni brauzer uchun yig'adi: `examples/todo-web/target/web/{index.html,app.js}` (Maven + TeaVM) |
| `./build.sh web-test` | shuni jsdom'da ochib, ilovada yozib/bosib tekshiradi (node + npm) |
| `./build.sh dev [port] [ilova...]` | dev server (`tools/dev-server.js`): qayta yig'ish + sahifani yangilash |
| `./build.sh examples` | Tailwind namunalari (kanban, shop, dashboard, landing, blog, contacts) -> `examples/dist/` (Maven + node) |
| `./build.sh examples-test` | shuni yig'ib, jsdom'da bosib tekshiradi |
| `./build.sh bench [n]` | brauzerda benchmark: JReact vs React 18 vs vanilla (Maven, node, Brave/Chrome) |
| `./build.sh lsp` | language server jar'i: `build/jsxj-lsp.jar` |
| `./editors/vscode-jsxj/install.sh` | VS Code kengaytmasini o'rnatadi (jar, npm, symlink) |
| `mvn -B -q -pl jreact-web,jreact-compiler -am install` | JReact modullarini lokal Maven repoga qo'yadi (o'z ilovangiz uchun) |

**Kompilyatorni to'g'ridan-to'g'ri chaqirish** (Maven'siz, JVM'da ishlatish uchun):

```bash
# 1. kompilyator va runtime (bir marta)
javac -encoding UTF-8 -d build/compiler $(find jreact-compiler/src/main/java -name '*.java')
javac -encoding UTF-8 -d build/core     $(find jreact-core/src/main/java -name '*.java')

# 2. .jsxj -> .java:  jsxjc <chiqish-papka> <manba-papka> [<manba-papka>...]
java -cp build/compiler jreact.compiler.JsxjCompiler build/gen src/main/jsxj src/main/java

# 3. Java -> .class va ishga tushirish
javac -encoding UTF-8 -cp build/core -d build/app $(find build/gen src/main/java -name '*.java')
java -cp build/core:build/app app.Main        # Windows'da ':' o'rniga ';'
```

- Har bir manba papkasi rekursiv o'qiladi. `.jsxj` va `.java` fayllari komponent indeksiga kiradi, faqat `.jsxj` fayllar `.java` ga aylantiriladi. Mavjud bo'lmagan papka o'tkazib yuboriladi.
- Chiqish fayllari paket yo'lini saqlaydi: `src/app/Card.jsxj` → `<chiqish>/app/Card.java`.
- Xato bo'lsa chiqish kodi `1`, hammasi joyida bo'lsa `0`.
- Komponent faqat kompilyatorga berilgan manba papkalarida bo'lsa topiladi. Indeks klassning **oddiy nomi** bo'yicha ishlaydi, shuning uchun turli paketlarda bir xil nomli ikki komponent yozmang.

## Qanday ishlaydi

| Modul | Vazifasi |
|---|---|
| `jreact-compiler` | `.jsxj` → `.java` kompilyatori (`jsxjc`) |
| `jreact-core` | Runtime: `Component`, `Element`, `Attrs`, hooks API, `Jsx`, `ErrorBoundary`, `ServerRenderer` |
| `jreact-scheduler` | Vazifalar navbati: ustuvorlik, muddat (timeout), `shouldYield`, delay |
| `jreact-reconciler` | Fiber reconciler: work loop, key'li diff, commit, hooks dispatcher, `HostConfig` |
| `jreact-dom` | DOM host: `DomHost`, `DomRoot`, event delegation, atribut/style qoidalari (JSO'siz) |
| `jreact-router` | Router: `Routes`, `Route`, `Link`, `NavLink`, `Outlet`, `Navigate`, hooks, `MemoryHistory` (DOM'siz); data router: `DataRouter`, `RouterProvider`, loader/action, `Form`, fetcher |
| `jreact-web` | Brauzer bog'lamalari (TeaVM JSO): `JsDomOps`, `JsTimers`, `JReactWeb.createRoot`, `BrowserRouter`, `HashRouter`, `Routers.createBrowserRouter` |
| `jreact-maven-plugin` | Maven plagini: `jsxj:compile` |
| `jreact-lsp` | `.jsxj` language server (LSP) |
| `examples/todo`, `examples/todo-web` | namunaviy ilova: JVM'da HTML va brauzerda |
| `examples/{kanban,shop,dashboard,landing,blog}` | Tailwind bilan 6 ta real ilova + `gallery` (blog: router) |
| `templates/web-app` | o'z brauzer ilovangiz uchun andoza |
| `editors/vscode-jsxj` | VS Code kengaytmasi |

### 1. Kompilyatsiya

Kompilyator Java kodini o'zgartirmay nusxalaydi (Java'ni haqiqatan `javac` tekshiradi), faqat JSX ifodalarini topib, runtime chaqiruvlariga aylantiradi.

`.jsxj` (kirish):

```java
return <span className={cls}>{props.label()}: <b>{props.value()}</b></span>;
```

`.java` (chiqish):

```java
return jreact.Jsx.el("span", null, new jreact.Attrs().attr("className", cls),
        props.label(), ": ", jreact.Jsx.el("b", null, null, props.value()));
```

Ikki o'tish: (1) barcha `.jsxj`/`.java` fayllardan komponentlarning `record Props(...)` e'lonlari yig'iladi, (2) har bir `.jsxj` `.java` ga yoziladi: `<TodoItem todo={t} />` Props indeksi bo'yicha tipli `new TodoItem.Props(t, ...)` chaqiruviga aylanadi.

### 2. Runtime

- `Jsx.el(...)` oddiy HTML element (`div`, `span`...) tavsifini, `Jsx.c(...)` komponent elementini (`TodoItem.class` bilan: reconciler fiber'ni qayta ishlatish mumkinmi, shuni hal qiladi), `Jsx.fragment(...)` `<>...</>` ni yaratadi.
- `Element` o'zgarmas (immutable) daraxt tuguni. UI har render'da shunday daraxt sifatida qaytariladi.
- Reconciler eski va yangi daraxtni solishtiradi (key'li diff, fiber'lar `alternate` bilan ikki nusxada), farqni commit fazasida host'ga qo'llaydi, so'ng `useEffect`larni ishga tushiradi. Scheduler render'ni bo'laklarga bo'ladi.

### 3. Qatorlar mosligi

`.jsxj` faylning N-qatori generatsiya qilingan `.java` faylda ham N-qator bo'lib qoladi (sarlavha izohi birinchi qatorda, yangi qatorsiz yoziladi). Shu sababli `javac` xatolari va stack trace'lar asl `.jsxj` faylning to'g'ri qatorini ko'rsatadi.

## Xavfsizlik (XSS)

Foydalanuvchi ma'lumotini (`<p>{comment}</p>`, `<a href={url}>`) ishonchli chiqarish uchun qoidalar bir joyda: `jreact.Safety` (DOM host va `ServerRenderer` ikkalasi ishlatadi).

**Kafolatlanadi:**

| Xavf | Himoya |
|---|---|
| Matn ichida HTML (`<img onerror=...>`) | Matn hech qachon HTML sifatida o'qilmaydi: brauzerda `createTextNode`, `ServerRenderer`'da escape |
| Atribut qiymatidan chiqib ketish (`"><script>`) | `setAttribute` / escape (`& < > "`) |
| `javascript:` va `vbscript:` URL | `href`, `src`, `action`, `formaction`, `poster`, `cite`, `data`... atributlarida `about:blank` ga almashtiriladi (`"java\tscript:"`, bosh bo'shliq, bosh/kichik harf ham tutiladi), ogohlantirish chiqadi |
| `onclick="..."` kabi satr handler | Ignor qilinadi (handler funksiya bo'lishi kerak: `onClick={e -> ...}`) |
| `<script>` element | `IllegalArgumentException` (skriptni sahifaning HTML'iga qo'ying). `ErrorBoundary` tutadi |
| Noto'g'ri tag/atribut nomi (`"x onmouseover=..."`) | Tag: xato, atribut: ignor + ogohlantirish |
| `style` obyekti orqali qiymat ichiga yangi qoida (`"red; position: fixed"`) | Brauzerda `style.setProperty`, `ServerRenderer`'da shunday deklaratsiya tashlab yuboriladi |
| `dangerouslySetInnerHTML` / `innerHTML` | Umuman yo'q |

**Kafolatlanmaydi (o'zingiz javobgarsiz):** `style="..."` **satr** qiymati (CSS), `srcdoc`, `<iframe src>`/`<object data>` manzili, `Ref` orqali olgan DOM tuguniga yozganingiz, o'zingiz yozgan `href` ichidagi `data:` URL.

Ogohlantirishlarni ushlash: `root.host().setWarningHandler(msg -> ...)`.

**Audit (2026-11-12)** quyidagilarni topdi va tuzatdi: (1) `javascript:` URL tekshirilmagan edi; (2) `onclick` satr atributi va noto'g'ri atribut nomlari o'tkazib yuborilardi; (3) `<script>`/noto'g'ri tag nomi yaratilardi; (4) `ServerRenderer` `style` obyektini `{color=red}` deb chiqarardi (xato, endi CSS); (5) dev server hamma tarmoq interfeyslarida tinglardi va noto'g'ri URL (`/%E0`) uni qulatardi: endi `127.0.0.1` (boshqasi uchun `HOST=0.0.0.0`) va 400; (6) VS Code'da `jsxj.javaPath`/`jsxj.server.jar` ni begona reponing `.vscode/settings.json` i orqali o'zgartirib ixtiyoriy fayl ishga tushirish mumkin edi: endi `machine` doirasi.

**Tekshirilgan va xavfsiz:** matn va atribut yo'llari, kompilyatorning satr literallari (`\u0022` kabi kiritma qochadi), LSP'ning `javac` chaqiruvi (`-proc:none`, annotatsiya protsessorlari ishlamaydi), `npm audit` (examples, smoke, VS Code kengaytmasi: 0 zaiflik).

**Qilinmagan:** Maven bog'liqliklari uchun CVE skaneri (masalan OWASP dependency-check), fuzzing, haqiqiy brauzerda XSS testi (himoya mantig'i Java'da, testlar soxta DOM va `ServerRenderer` bilan: `DomTest`).

## Unumdorlik (benchmark)

`./build.sh bench [aylanishlar]` js-framework-benchmark'dagi jadval senariylarini (1000 qator yaratish, almashtirish, har 10-sini yangilash, 2 qator o'rnini almashtirish, o'chirish, 1000 qo'shish, tozalash, 10 000 yaratish) haqiqiy brauzerda (headless Brave/Chrome) yurgizadi va JReact'ni React 18 (production) hamda qo'lda yozilgan vanilla DOM bilan solishtiradi. Vaqt `performance.now()` bilan o'lchanadi, har o'lchovdan keyin layout majburlanadi (`offsetHeight`), har senariy 3 isitish + 10 o'lchov, jadvalda 3 sahifa yuklash medianasi. Qatorlar `key` bilan, `memo`siz, shuning uchun hamma qator har yangilanishda qayta render qilinadi (ikkala freymvorkda ham).

Natija (Apple M5, Brave/Chrome 157, ms, kam = yaxshi):

| senariy | vanilla DOM | React 18 | JReact | JReact / React | JReact dev rejimi |
|---|---:|---:|---:|---:|---:|
| create1k | 8.3 | 10.2 | 18.5 | 1.81x | 18.7 |
| replace1k | 8.3 | 13.6 | 19.5 | 1.43x | 21.2 |
| update10th | 1.9 | 2.5 | 5.1 | 2.04x | 8.3 |
| swap | 0.5 | 9 | 12.8 | 1.42x | 16.6 |
| remove | 0.4 | 1 | 5.8 | 5.80x | 3.9 |
| append1k | 9.4 | 11.2 | 27.1 | 2.42x | 25.7 |
| clear | 0.7 | 1.6 | 1.1 | 0.69x | 1 |
| create10k | 79.1 | 226.6 | 305.1 | 1.35x | 321.7 |

Bundle: JReact (TeaVM, shu jadval ilovasi) `app.js` 166 KB, gzip 58 KB. React 18 `react` + `react-dom` 139 KB, gzip 46 KB (ilovasiz). Kichik ilovada (Todo) `app.js` ~120 KB (gzip ~45 KB).

Xulosa: JReact shu jadvalda React 18'dan taxminan 1.4 dan 2.4 baravargacha sekinroq (`remove` da ~6x; sababi o'rganilmagan, ikkalasi ham 999 ta o'zgarmagan qatorni qayta render qiladi). Qo'lda yozilgan vanilla DOM'dan 2 dan 30 baravargacha sekin (`swap`, `remove` da eng katta farq). 60 FPS uchun kadr 16 ms: oddiy ilovada (yuzlab qator) bu yetarli, 10 000 qatorli jadvalda (300 ms) emas. Dev rejimi (`setDevMode(true)`, standart) ishlashga sezilarli ta'sir qilmaydi, lekin ishlab chiqarishda `root.reconciler().setDevMode(false)` qiling (ogohlantirishlar kerak emas).

Qanday o'qish kerak: bu bitta mashina va bitta jadval, shuning uchun mutlaq raqamlarga emas, nisbatlarga qarang; ikkala tomonda ham `memo` ishlatilmagan (`React.memo`, `MemoComponent`), shuning uchun ularning ta'siri o'lchanmagan. Profil qilinmagan va optimallashtirilmagan: sekinlikning aniq sababi (fiber ajratish, `Attrs` farqlash, TeaVM JSO chaqiruvlari) hali ma'lum emas.

## Maven Central'ga chiqarish

Sozlama tayyor (`pom.xml`: metadata, `release` profili), lekin haqiqiy chiqarish uchun sizdan 4 narsa kerak:

1. **Namespace.** Hozir `groupId` = `dev.jreact`. Central'da buni tasdiqlash uchun `jreact.dev` domeni sizniki bo'lishi kerak. Aks holda GitHub orqali tasdiqlanadigan `io.github.ogabekyuldoshev` ni ishlating (`groupId` ni hamma `pom.xml` da almashtirish kerak).
2. **Metadata.** `pom.xml` dagi `url`, `scm`, `developers` GitHub nomingizdan taxmin qilib yozilgan (`github.com/OgabekYuldoshev/jreact`). Haqiqiy repo manziliga to'g'rilang. Litsenziya: Apache-2.0 (`LICENSE`), xohlasangiz o'zgartiring.
3. **Hisob va kalit.** [central.sonatype.com](https://central.sonatype.com) da token oling va `~/.m2/settings.xml` ga `<server><id>central</id>...` qilib qo'ying. GPG kalit yarating va uni ochiq serverga yuklang.
4. **Versiya.** `0.1.0-SNAPSHOT` ni release versiyaga o'zgartiring: `mvn versions:set -DnewVersion=0.1.0` (`templates/web-app` va README'dagi `jreact.version` ni ham).

Tekshirish (imzosiz, yuklamaydi): `mvn -Prelease -Dgpg.skip package` hamma modul uchun `-sources.jar` va `-javadoc.jar` yasaydi. Chiqarish: `mvn -Prelease deploy`. `release` profili namunalarni (`examples`) o'chiradi, shuning uchun faqat kutubxona modullari yuklanadi. `autoPublish` o'chiq: yuklangach Central portalida natijani ko'rib, qo'lda "Publish" bosasiz.

Imzo va yuklash bosqichi shu loyihada sinalmagan (kalit va token yo'q), faqat `package` bosqichigacha tekshirilgan.

## Muammolarni hal qilish

| Belgi | Sabab va yechim |
|---|---|
| `unknown component <Foo>: no 'record Props(...)' found` | `Foo` kompilyatorga berilgan papkalarda yo'q yoki `record Props` yo'q. `ErrorBoundary` bo'lsa `jreact-core/src/main/java` ni ham bering (`build.sh`/qo'lda ishlatganda). Maven plagini buni o'zi qiladi |
| `<Foo> has no prop 'x'; its props are: ...` | prop nomi `record Props` da yo'q. Ro'yxatdan to'g'ri nomni oling |
| `<Foo> does not accept children` | `Props` ga `List<Object> children` qo'shing |
| TeaVM class faylni o'qiy olmaydi (yangi JDK bytecode'i) | `pom.xml` da `maven.compiler.release` = `17` bo'lsin (andozada shunday) |
| `main is not defined` brauzerda | `app.js` yuklanmagan yoki yo'li noto'g'ri; `<script src="app.js">` `main();` dan oldin bo'lsin |
| `no element with id 'root'` | `createRoot("root")` dagi id sahifadagi element bilan mos emas |
| VS Code'da javac xatolari chiqmaydi | `java` JRE yoki PATH'da yo'q: `jsxj.javaPath` ga JDK bering. Eslatma: jsxjc xatosi bo'lsa javac ishlamaydi, avval uni tuzating |
| VS Code'da `cannot find symbol` (jreact...) | `jreact-core` manbasi workspace'da yo'q: `jsxj.sourceRoots` yoki `jsxj.classpath` bering |
| `Maximum update depth exceeded` | effekt har renderda `set` chaqiryapti: `deps` bering yoki shart qo'ying |
| `State cannot be updated while rendering` | `render()` ichida `set` chaqirmang, event handler yoki `useEffect` da chaqiring |
| `Hooks were called in a different order` | hook shartli/tsiklda chaqirilgan, har renderda bir xil tartibda bo'lsin |

## Loyiha tuzilmasi

```
jreact/
├── build.sh                          # build va test skripti
├── pom.xml                           # Maven: brauzer build'i uchun (modullar ./build.sh bilan ham yig'iladi)
├── jreact-compiler/                  # .jsxj kompilyatori
├── jreact-maven-plugin/              # Maven plagini: jsxj:compile
├── jreact-router/                    # router: Routes, Route, Link, NavLink, Outlet, hooks
│   └── src/
│       ├── main/java/jreact/compiler/
│       │   ├── JsxjCompiler.java     # CLI, 2 o'tishni boshqaradi
│       │   ├── PropsIndex.java       # 1-o'tish: komponent Props indeksi
│       │   ├── JsxTranspiler.java    # 2-o'tish: JSX → Java
│       │   └── CompileError.java     # fayl:qator:ustun xatolari
│       └── test/.../TranspilerTest.java   # 38 ta test
├── jreact-core/                      # runtime (Component, Element, Attrs, Jsx, Hooks, ErrorBoundary, ServerRenderer...)
├── jreact-scheduler/                 # scheduler (paket jreact.scheduler, core'ga bog'liq emas)
│   └── src/test/.../SchedulerTest.java    # 14 ta test
├── jreact-reconciler/                # Fiber reconciler (paket jreact.reconciler)
│   └── src/
│       ├── main/java/jreact/reconciler/
│       │   ├── Reconciler.java       # ochiq API, work loop, setState dispatch, error boundary
│       │   ├── Root.java             # mount qilingan daraxt (FiberRoot)
│       │   ├── HostConfig.java       # platforma interfeysi (DOM, test, ...)
│       │   ├── Fiber.java            # daraxt tuguni, alternate (double buffering)
│       │   ├── Render.java           # render fazasi: beginWork / completeWork
│       │   ├── ChildReconciler.java  # key'li bolalar diff'i
│       │   ├── Commit.java           # commit: mutation, ref, passive effects
│       │   ├── FiberHooks.java       # hooks dispatcher (mount/update)
│       │   ├── Diagnostics.java      # dev-mode ogohlantirishlar
│       │   └── AttrsDiff.java        # atribut/event o'zgarishi
│       └── test/.../ReconcilerTest.java   # 56 ta test (xotiradagi test host)
├── jreact-dom/                       # DOM host (paket jreact.dom), TeaVM'siz qismi
│   └── src/test/.../DomTest.java     # 19 ta test (soxta DOM)
├── jreact-web/                       # brauzer bog'lamalari (TeaVM JSO): JReactWeb, JsDomOps, JsTimers...
├── jreact-lsp/                       # language server: Server, Analyzer, Features, Workspace, Json
│   └── src/test/.../LspTest.java     # 12 ta test, haqiqiy JSON-RPC oqimi bilan
├── editors/vscode-jsxj/              # VS Code kengaytmasi: grammar + LSP klienti
├── tools/dev-server.js               # dev server: hamma namuna ilovalar, live reload
├── templates/web-app/                # o'z ilovangiz uchun andoza (pom.xml, Counter, WebMain, index.html)
└── examples/
    ├── todo/                         # namunaviy ilova (.jsxj), JVM'da HTML chiqaradi
    ├── todo-web/                     # shu ilova brauzerda + jsdom smoke testi
    ├── kanban/ shop/ dashboard/ landing/ blog/ contacts/   # Tailwind bilan 6 ta ilova (pom.xml, src/main/{jsxj,java,styles,webapp})
    ├── gallery/                      # to'rttasini bog'lovchi bosh sahifa
    ├── pom.xml, package.json         # umumiy Maven sozlamasi; Tailwind CLI + jsdom
    └── smoke.js                      # 4 ilova uchun jsdom testi
```

## Cheklovlar

- Brauzer build'i faqat jsdom'da sinalgan, haqiqiy brauzerda emas. Dev server'ning live reload'i va VS Code klienti ham haqiqiy muhitda hali ko'rilmagan (server protokoli `vscode-jsonrpc` bilan sinalgan).
- Dev server `examples/` ilovalari uchun qattiq yozilgan (`tools/dev-server.js` boshidagi ro'yxat).
- JReact hali Maven Central'ga chiqarilmagan: o'z ilovangiz uchun `mvn install` bilan lokal o'rnatiladi.
- CSS Modules va CSS-in-JS yo'q. Sass/Tailwind Maven build'iga ulanmagan, alohida buyruq bilan yig'iladi.
- DOM: SVG (namespace), `onMouseEnter/Leave`, `dangerouslySetInnerHTML` yo'q (xavfsizlik uchun ham, qarang [Xavfsizlik](#xavfsizlik-xss)).
- Tezlik: React 18'dan 1.4-2.4x sekinroq (qarang [Unumdorlik](#unumdorlik-benchmark)), profil qilinmagan.
- Ustuvorlik root bo'yicha, update bo'yicha emas (lane yo'q): bitta render barcha kutayotgan update'larni oladi. Bo'laklab render faqat `LOW`/`IDLE` (`startTransition`) uchun, uni boshqa update to'xtatib qayta boshlamaydi, faqat tezroq tugatadi.
- Error boundary render va `useEffect` xatolarini tutadi, event handler xatolarini tutmaydi (React'dagi kabi). `ServerRenderer` (SSR) boundary'da xatoni tutmaydi.
- `{...spread}` komponentlarda qo'llab-quvvatlanmaydi.
- Bir xil nomli komponentlar turli paketlarda bo'lsa indeks adashadi.
- IntelliJ uchun plagin yo'q. VS Code'da `{...}` ichidagi Java uchun avtokomplit yo'q.
- Litsenziya: Apache-2.0 (`LICENSE`, `pom.xml`). Maven Central'ga hali chiqarilmagan, qarang: [Maven Central'ga chiqarish](#maven-centralga-chiqarish).

## Yo'l xaritasi

1. ~~Fiber reconciler: work loop, key'li diff, commit, hooks~~ (tayyor)
2. ~~Scheduler: bo'laklab render, ustuvorlik~~ (tayyor, root darajasida). Qolgani: haqiqiy lane'lar (update bo'yicha ustuvorlik, render'ni to'xtatib qayta boshlash)
3. ~~DOM host, event delegation, TeaVM orqali `jreact-web`~~ (tayyor)
4. ~~TeaVM bilan `app.js`, dev server~~ (tayyor). Qolgani: `jsxj:compile` va Central sozlamasi tayyor, haqiqiy chiqarish qoldi. Dev server'ni o'z ilovangiz uchun sozlanadigan qilish
5. ~~`Context`, `memo`, error boundary, key ogohlantirishlari~~ (tayyor). Qolgani: boshqa ogohlantirishlar (masalan `value` bor, `onInput` yo'q)
6. ~~VS Code: ranglash, language server~~ (tayyor). Qolgani: IntelliJ plagini, Java ifodalari uchun autocomplete (jdtls bilan birlashtirish), rename/find references
