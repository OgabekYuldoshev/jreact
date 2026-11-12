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
- [Stillar: CSS, Sass, Tailwind](#stillar-css-sass-tailwind)
- [Namunalar: Tailwind bilan 4 ta ilova](#namunalar-tailwind-bilan-4-ta-ilova)
- [Error boundary](#error-boundary)
- [Ilovani ishga tushirish va ustuvorlik](#ilovani-ishga-tushirish-va-ustuvorlik)
- [JVM'da ishlatish (HTML render, test)](#jvmda-ishlatish-html-render-test)
- [Ishlab chiqish jarayoni](#ishlab-chiqish-jarayoni): dev server, VS Code, xatolar, dev ogohlantirishlar
- [Buyruqlar ma'lumotnomasi](#buyruqlar-malumotnomasi)
- [Qanday ishlaydi](#qanday-ishlaydi)
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
./build.sh test     # barcha testlar (kompilyator 38, scheduler 14, reconciler 57, dom 19, language server 12)
./build.sh dev      # galereya + 5 ilova: http://localhost:5173 (o'zgarsa o'zi qayta yig'iladi)
```

`./build.sh` Todo'ning HTML'ini terminalga chiqaradi (`<section class="todo-app"><h1>JReact Todo</h1>...`). Har ishga tushganda `build/` noldan yaratiladi.

`./build.sh dev` birinchi marta Maven orqali TeaVM'ni yuklaydi (bir necha daqiqa), keyingi qayta yig'ishlar ~4 soniya. `/` da galereya, `/todo/` da Todo ilovasi ("Xato chiqar" tugmasi error boundary namunasi), `/kanban/`, `/shop/`, `/dashboard/`, `/landing/` da Tailwind ilovalari.

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

## Namunalar: Tailwind bilan 4 ta ilova

`examples/` ichida Tailwind CSS v4 bilan yozilgan 4 ta real ilova bor. Hammasi `.jsxj`, brauzerda TeaVM orqali ishlaydi.

| Ilova | Nima ko'rsatadi |
|-------|-----------------|
| `examples/kanban` | 3 ustunli doska: `useState` bilan ro'yxat, controlled input va select, kartani ko'chirish/o'chirish, `style` bilan progress bar |
| `examples/shop` | Do'kon: 12 mahsulot, qidiruv, kategoriya, saralash (`useMemo`), savat `Context` orqali (`Cart.CONTEXT`), yon panel, yetkazish narxi hisobi |
| `examples/dashboard` | Admin panel: statistika kartalari, bar chart, saralanadigan/filtrlanadigan jadval, dark mode (`dark:` variantlari), `ErrorBoundary` ichidagi vidjet |
| `examples/landing` | Landing sahifa: gradient hero, narx jadvali (oylik/yillik), FAQ akkordeon, mobil menyu, validatsiyali kontakt forma |

Yig'ish va ko'rish:

```bash
./build.sh examples        # Maven + TeaVM, keyin Tailwind CLI -> examples/dist/
./build.sh examples-test   # shuni yig'ib, 4 ilovani jsdom'da bosib tekshiradi (75 tekshiruv)
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

Bitta portda galereya (`/`) va ilovalarni (`/todo/`, `/kanban/`, `/shop/`, `/dashboard/`, `/landing/`) ko'rsatadi. Ilovaning `.jsxj`, `.java`, `.css` yoki `index.html` fayli o'zgarsa faqat shu ilova qayta yig'iladi (Maven + Tailwind, ~4 s) va uning sahifasi yangilanadi. `jreact-*` (framework) o'zgarsa hammasi qayta yig'iladi. Build xatosi brauzerda qizil sahifa bo'lib chiqadi, tuzatsangiz o'zi tiklanadi.

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
| `./build.sh examples` | Tailwind namunalari (kanban, shop, dashboard, landing) -> `examples/dist/` (Maven + node) |
| `./build.sh examples-test` | shuni yig'ib, jsdom'da bosib tekshiradi |
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
| `jreact-web` | Brauzer bog'lamalari (TeaVM JSO): `JsDomOps`, `JsTimers`, `JReactWeb.createRoot` |
| `jreact-maven-plugin` | Maven plagini: `jsxj:compile` |
| `jreact-lsp` | `.jsxj` language server (LSP) |
| `examples/todo`, `examples/todo-web` | namunaviy ilova: JVM'da HTML va brauzerda |
| `examples/{kanban,shop,dashboard,landing}` | Tailwind bilan 4 ta real ilova + `gallery` |
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
    ├── kanban/ shop/ dashboard/ landing/   # Tailwind bilan 4 ta ilova (pom.xml, src/main/{jsxj,java,styles,webapp})
    ├── gallery/                      # to'rttasini bog'lovchi bosh sahifa
    ├── pom.xml, package.json         # umumiy Maven sozlamasi; Tailwind CLI + jsdom
    └── smoke.js                      # 4 ilova uchun jsdom testi
```

## Cheklovlar

- Brauzer build'i faqat jsdom'da sinalgan, haqiqiy brauzerda emas. Dev server'ning live reload'i va VS Code klienti ham haqiqiy muhitda hali ko'rilmagan (server protokoli `vscode-jsonrpc` bilan sinalgan).
- Dev server `examples/` ilovalari uchun qattiq yozilgan (`tools/dev-server.js` boshidagi ro'yxat).
- JReact hali Maven Central'ga chiqarilmagan: o'z ilovangiz uchun `mvn install` bilan lokal o'rnatiladi.
- CSS Modules va CSS-in-JS yo'q. Sass/Tailwind Maven build'iga ulanmagan, alohida buyruq bilan yig'iladi.
- DOM: SVG (namespace), `onMouseEnter/Leave`, `dangerouslySetInnerHTML` yo'q.
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
