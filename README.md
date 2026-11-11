# JReact

**Java'da React.** UI'ni Java klasslari va JSX'ga o'xshash `.jsxj` sintaksisi bilan yozasiz. Kompilyator `.jsxj` fayllarni oddiy Java'ga aylantiradi, `javac` esa qolganini bajaradi. Maqsad: React'ning ichki mexanizmini (Fiber, reconciler, hooks) Java OOP asosida qayta qurish va oxirida brauzer uchun `app.js` olish.

```
.jsxj ──jsxjc──▶ .java ──javac──▶ .class ──TeaVM──▶ app.js
      (shu repo)                          (rejada)
```

> **Holat: erta bosqich.** Ishlaydiganlari: `.jsxj` kompilyatori, runtime API, bir martalik HTML render (`ServerRenderer`), Fiber reconciler (state, hooks, key'li diff, commit), scheduler (ustuvorlik, bo'laklab render) va DOM host (atribut/style qoidalari, event delegation). Brauzer uchun build ham bor: Todo namunasi TeaVM bilan `app.js` ga aylanadi va jsdom'da sinaladi (`./build.sh web-test`). Haqiqiy brauzerda hali sinalmagan. Batafsil: [Yo'l xaritasi](#yol-xaritasi).

## Mundarija

- [JReact](#jreact)
  - [Mundarija](#mundarija)
  - [Tez boshlash](#tez-boshlash)
  - [Qanday ishlaydi](#qanday-ishlaydi)
    - [1. Kompilyatsiya](#1-kompilyatsiya)
    - [2. Runtime](#2-runtime)
    - [3. Qatorlar mosligi](#3-qatorlar-mosligi)
  - [Birinchi komponent](#birinchi-komponent)
  - [`.jsxj` sintaksisi](#jsxj-sintaksisi)
  - [Komponentlar va props](#komponentlar-va-props)
    - [Children](#children)
  - [Hooks](#hooks)
  - [Context va memo](#context-va-memo)
  - [Eventlar](#eventlar)
  - [Kompilyator (`jsxjc`)](#kompilyator-jsxjc)
  - [O'z loyihangizda ishlatish](#oz-loyihangizda-ishlatish)
  - [Xatolar](#xatolar)
  - [Dev-mode ogohlantirishlar](#dev-mode-ogohlantirishlar)
  - [Loyiha tuzilmasi](#loyiha-tuzilmasi)
  - [Hozirgi cheklovlar](#hozirgi-cheklovlar)
  - [Yo'l xaritasi](#yol-xaritasi)

## Tez boshlash

**Talablar:** JDK 17 yoki yangiroq (record, switch expression va text block ishlatilgan). JDK 21 va 26'da sinalgan. Brauzer build'idan tashqari Maven shart emas. macOS, Linux va Windows'da (Git Bash yoki WSL) ishlaydi.

```bash
git clone <repo-url> jreact
cd jreact

./build.sh        # kompilyator → runtime → .jsxj → .java → javac → namunani ishga tushiradi
./build.sh test   # kompilyator (38), scheduler (14), reconciler (46) va dom (19) testlari

./build.sh web        # Todo'ni brauzer uchun yig'adi: examples/todo-web/target/web/{index.html,app.js}
./build.sh web-test   # shuni jsdom'da ochib, ilovada bosib/yozib tekshiradi
./build.sh dev        # dev server: http://localhost:5173, manba o'zgarsa o'zi qayta yig'adi va sahifani yangilaydi
./editors/vscode-jsxj/install.sh   # VS Code'da .jsxj ranglash (qarang: editors/vscode-jsxj)
```

`./build.sh` Todo namunasini kompilyatsiya qilib, uning HTML'ini terminalga chiqaradi:

```html
<section class="todo-app"><h1>JReact Todo</h1><form>...</form><ul><li class="todo done">...</li>...</ul>...</section>
```

Har ishga tushganda `build/` papkasi noldan qayta yaratiladi.

## Qanday ishlaydi

Loyiha quyidagi qismlardan iborat:

| Modul | Vazifasi |
|---|---|
| `jreact-compiler` | `.jsxj` → `.java` kompilyatori (`jsxjc`) |
| `jreact-core` | Runtime: `Component`, `Element`, `Attrs`, hooks API, `Jsx`, `ServerRenderer` |
| `jreact-scheduler` | Vazifalar navbati: ustuvorlik, muddat (timeout), `shouldYield`, delay |
| `jreact-dom` | DOM host: `DomHost`, `DomRoot`, event delegation, atribut/style qoidalari (JSO'siz) |
| `jreact-web` | Brauzer bog'lamalari (TeaVM JSO): `JsDomOps`, `JsTimers`, `JReactWeb.createRoot` |
| `jreact-reconciler` | Fiber reconciler: work loop, key'li diff, commit, hooks dispatcher, `HostConfig` |
| `examples/todo-web` | Todo namunasi brauzerda: `.jsxj` → `app.js` (Maven + TeaVM) |
| `examples/todo` | `.jsxj` bilan yozilgan namunaviy ilova |

### 1. Kompilyatsiya

`.jsxj` fayl oddiy Java faylga JSX qo'shilgan holat. Kompilyator Java kodini o'zgartirmay nusxalaydi (Java'ni haqiqatan `javac` tekshiradi), faqat JSX ifodalarini topib, runtime chaqiruvlariga aylantiradi. Babel JSX'ni JS'ga aylantirishi bilan bir xil.

`.jsxj` (kirish):

```java
return <span className={cls}>{props.label()}: <b>{props.value()}</b></span>;
```

`.java` (chiqish):

```java
return jreact.Jsx.el("span", null, new jreact.Attrs().attr("className", cls),
        props.label(), ": ", jreact.Jsx.el("b", null, null, props.value()));
```

Kompilyator ikki o'tishda ishlaydi:

1. **Indekslash.** Barcha `.jsxj` va `.java` fayllardan komponentlarning `record Props(...)` e'lonlari yig'iladi.
2. **Generatsiya.** Har bir `.jsxj` fayl `.java` ga yoziladi. `<TodoItem todo={t} />` yozuvi Props indeksi bo'yicha tipli `new TodoItem.Props(t, ...)` chaqiruviga aylanadi.

### 2. Runtime

- `Jsx.el(...)` oddiy HTML element (`div`, `span`...) tavsifini yaratadi.
- `Jsx.c(...)` komponent elementini yaratadi. Komponent `TodoItem.class` bilan aniqlanadi: reconciler fiber'ni qayta ishlatish mumkinmi yoki yo'qligini shu orqali hal qiladi.
- `Jsx.fragment(...)` `<>...</>` uchun.
- `Element` o'zgarmas (immutable) daraxt tugunidir. UI har render'da shunday daraxt sifatida qaytariladi.
- `ServerRenderer.renderToString(element)` daraxtni HTML satriga aylantiradi.

### 3. Qatorlar mosligi

`.jsxj` faylning N-qatori generatsiya qilingan `.java` faylda ham N-qator bo'lib qoladi (sarlavha izohi birinchi qatorda, yangi qatorsiz yoziladi). Shu sababli `javac` xatolari va stack trace'lar asl `.jsxj` faylning to'g'ri qatorini ko'rsatadi.

## Birinchi komponent

`examples/todo/src/main/jsxj/app/Greeting.jsxj` fayl yarating:

```java
package app;

import jreact.Component;
import jreact.Element;

public class Greeting extends Component<Greeting.Props> {

    public record Props(String name, int age) {}

    @Override
    public Element render(Props props) {
        return (
            <div className="greeting">
                <h2>Salom, {props.name()}!</h2>
                {props.age() >= 18 ? <p>Xush kelibsiz</p> : <p>Kattalar bilan keling</p>}
            </div>
        );
    }
}
```

`Main.jsxj` ichida ishlating:

```java
Element app = <Greeting name="Ali" age={20} />;
System.out.println(ServerRenderer.renderToString(app));
```

Keyin `./build.sh` ni qayta ishga tushiring.

## `.jsxj` sintaksisi

JSX ifoda yozish mumkin bo'lgan joyda yoziladi. Aniqrog'i, `<` quyidagilardan keyin kelsa JSX deb o'qiladi: `=`, `(`, `,`, `?`, `:`, `{`, `[`, `&`, `|`, `->`, `return`, `yield`. Boshqa joyda ko'p qatorli JSX'ni qavs ichiga oling: `return ( <div>...</div> );`.

`List<String>`, `a < b`, satr va izohlar ichidagi `<` JSX hisoblanmaydi.

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
- Prop tipi noto'g'ri bo'lsa (masalan `value={"uch"}` `int` o'rniga), buni `javac` tutadi. Xabardagi qator raqami `.jsxj` fayldagi qator bilan bir xil bo'ladi.
- Komponentda **argumentsiz konstruktor** bo'lishi kerak (kompilyator `TodoItem::new` ishlatadi). Maxsus konstruktor yozmasangiz, bu o'z-o'zidan bajariladi.
- `key` va `{...spread}` props'ga kirmaydi. `{...spread}` komponentlarda qo'llab-quvvatlanmaydi, propslarni aniq yozing.

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
| `void useEffect(Runnable effect, Object... deps)` | Effekt. `deps` bo'lmasa har renderda ishlaydi |
| `void useEffect(Effect effect, Object... deps)` | Cleanup qaytaradigan effekt: `() -> { sub(); return () -> unsub(); }` |
| `T useMemo(Supplier<T> compute, Object... deps)` | Qimmat hisobni eslab qolish |
| `Ref<T> useRef(T initial)` | O'zgaruvchan quti: `ref.current` |
| `T useContext(Context<T> ctx)` | Eng yaqin provider qiymati, bo'lmasa default. Hook tartibiga kirmaydi, shartli chaqirsa bo'ladi |

```java
State<Integer> count = useState(0);

useEffect(() -> System.out.println("count = " + count.get()), count.get());

return <button onClick={e -> count.update(c -> c + 1)}>+1 ({count.get()})</button>;
```

> **Hozirgi holat:** hooks `jreact-reconciler` ichida to'liq ishlaydi. `ServerRenderer` esa bir martalik render qiladi: `useState` boshlang'ich qiymatini qaytaradi, `set/update` hech narsa qilmaydi, effektlar ishlamaydi.
>
> Ba'zi qoidalar: `deps` berilmasa effekt (va `useMemo`) har renderda ishlaydi, `deps` bo'lsa `equals` bilan solishtiriladi. `State.get()` render paytidagi qiymatni qaytaradi (React closure kabi). Render paytida `set` chaqirish xato. Bir xil qiymatga `set` qilish render'ni ishga tushirmaydi.

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

## Kompilyator (`jsxjc`)

```bash
java -cp build/compiler jreact.compiler.JsxjCompiler <chiqish-papka> <manba-papka> [<manba-papka>...]
```

- Har bir manba papkasi rekursiv o'qiladi. `.jsxj` va `.java` fayllari komponent indeksiga kiradi, faqat `.jsxj` fayllar `.java` ga aylantiriladi.
- Chiqish fayllari paket yo'lini saqlaydi: `src/app/Card.jsxj` → `<chiqish>/app/Card.java`.
- Xato bo'lsa chiqish kodi `1`, hammasi joyida bo'lsa `0`.

Komponent faqat kompilyatorga berilgan manba papkalarida bo'lsa topiladi. Indeks klassning **oddiy nomi** bo'yicha ishlaydi, shuning uchun turli paketlarda bir xil nomli ikki komponent yozmang.

## O'z loyihangizda ishlatish

`build.sh` faqat namunani yig'adi. O'z loyihangiz uchun zanjirni qo'lda ishlating:

```bash
# 1. Kompilyator va runtime (bir marta)
javac -encoding UTF-8 -d build/compiler $(find jreact-compiler/src/main/java -name '*.java')
javac -encoding UTF-8 -d build/core     $(find jreact-core/src/main/java -name '*.java')

# 2. .jsxj → .java
java -cp build/compiler jreact.compiler.JsxjCompiler build/gen src/main/jsxj src/main/java

# 3. Java → .class
javac -encoding UTF-8 -cp build/core -d build/app $(find build/gen src/main/java -name '*.java')

# 4. Ishga tushirish
java -cp build/core:build/app app.Main        # Windows'da ':' o'rniga ';'
```

**IDE (IntelliJ IDEA):** *Settings → Editor → File Types → Java* ga `*.jsxj` naqshini qo'shing. Java qismlari rangli bo'ladi. JSX qismlarini IDE xato deb ko'rsatishi mumkin, chunki bu format uchun hali plagin yo'q. Haqiqiy xatolarni `jsxjc` va `javac` beradi.

## Xatolar

`jsxjc` xatolarni `javac` uslubida, fayl, qator va ustun bilan chiqaradi:

```
app/Bad.jsxj:3:1: error: expected </ul> to close <ul> opened at 1:5, but found </ol>
    </ol>;
    ^
```

Kompilyator topadigan xatolar: noto'g'ri yopilgan yoki yopilmagan teg, noma'lum prop, noma'lum komponent, bola qabul qilmaydigan komponentga bola berish, takrorlangan prop, satr ko'rinishidagi event handler, bo'sh `{}` atribut qiymati, JSX matnidagi yakka `}`, yopilmagan `{`.

Tip xatolari (masalan `int` o'rniga `String`) `javac`dan keladi. U generatsiya qilingan `.java` faylni ko'rsatadi, lekin qator raqami `.jsxj` fayldagi bilan bir xil, shuning uchun xatoni darhol topasiz:

```
build/gen/app/TodoApp.java:43: error: incompatible types: String cannot be converted to int
```

## Dev-mode ogohlantirishlar

Reconciler ishga tushirish vaqtida (runtime) ham React kabi tipik xatolarni xabar qiladi. Ular xatti-harakatni o'zgartirmaydi, har bir muammo bir marta chiqadi va komponent zanjirini ko'rsatadi:

```
Warning: Each child in a list should have a unique "key" prop, but <li> has none. Keys let the reconciler keep state and DOM nodes when the list changes.
    in TodoList > TodoApp
```

- Ma'lumotdan yasalgan ro'yxatda (`.map(...).toList()`) `key` yo'q.
- Bir xil ro'yxatda takrorlangan `key`.

`<Card>...</Card>` ichidagi qo'lda yozilgan JSX bolalari (`props.children()`) ro'yxat hisoblanmaydi, ularga `key` shart emas.

```java
reconciler.setDevMode(false);                      // production: o'chirish
reconciler.setWarningHandler(msg -> log.warn(msg)); // default: System.err (brauzerda console)
```

## Loyiha tuzilmasi

```
jreact/
├── build.sh                          # build va test skripti
├── jreact-compiler/                  # .jsxj kompilyatori
│   └── src/
│       ├── main/java/jreact/compiler/
│       │   ├── JsxjCompiler.java     # CLI, 2 o'tishni boshqaradi
│       │   ├── PropsIndex.java       # 1-o'tish: komponent Props indeksi
│       │   ├── JsxTranspiler.java    # 2-o'tish: JSX → Java
│       │   └── CompileError.java     # fayl:qator:ustun xatolari
│       └── test/java/jreact/compiler/
│           └── TranspilerTest.java   # 38 ta test (bog'liqliksiz)
├── jreact-core/                      # runtime
│   └── src/main/java/jreact/
│       ├── Component.java            # asosiy klass + hooks metodlari
│       ├── Element.java              # o'zgarmas UI daraxti
│       ├── Attrs.java                # HTML element atributlari/eventlari
│       ├── Jsx.java                  # kompilyator chiqishining nishoni
│       ├── Hooks.java                # joriy renderning hook dispatcher'i
│       ├── State.java, Ref.java, Effect.java
│       ├── Event.java, EventHandler.java
│       └── ServerRenderer.java       # Element → HTML satr
├── jreact-scheduler/                 # scheduler (paket jreact.scheduler, core'ga bog'liq emas)
│   └── src/
│       ├── main/java/jreact/scheduler/
│       │   ├── Scheduler.java        # min-heap navbat, work loop, continuation
│       │   ├── Priority.java         # IMMEDIATE ... IDLE va ularning timeout'lari
│       │   ├── Task.java             # bekor qilish uchun handle
│       │   ├── SchedulerHost.java    # soat va "keyinroq chaqir" (platforma beradi)
│       │   └── ManualSchedulerHost.java  # virtual soatli host (test, headless)
│       └── test/java/jreact/scheduler/
│           └── SchedulerTest.java    # 14 ta test
├── jreact-reconciler/                # Fiber reconciler (paket jreact.reconciler)
│   └── src/
│       ├── main/java/jreact/reconciler/
│       │   ├── Reconciler.java       # ochiq API, work loop, setState dispatch
│       │   ├── Root.java             # mount qilingan daraxt (FiberRoot)
│       │   ├── HostConfig.java       # platforma interfeysi (DOM, test, ...)
│       │   ├── Fiber.java            # daraxt tuguni, alternate (double buffering)
│       │   ├── Render.java           # render fazasi: beginWork / completeWork
│       │   ├── ChildReconciler.java  # key'li bolalar diff'i
│       │   ├── Commit.java           # commit: mutation, ref, passive effects
│       │   ├── FiberHooks.java       # hooks dispatcher (mount/update)
│       │   └── AttrsDiff.java        # atribut/event o'zgarishi
│       └── test/java/jreact/reconciler/
│           └── ReconcilerTest.java   # 46 ta test (xotiradagi test host)
├── jreact-dom/                       # DOM host (paket jreact.dom), TeaVM'siz qismi
│   └── src/
│       ├── main/java/jreact/dom/
│       │   ├── DomRoot.java          # kirish nuqtasi: container'ga render
│       │   ├── DomHost.java          # HostConfig: DOM yangilash + event delegation
│       │   ├── DomOps.java           # DOM amallari interfeysi (brauzerda JSO bajaradi)
│       │   ├── NativeEvent.java      # platforma event'i
│       │   ├── DomAttributes.java    # className→class, boolean atribut, style qoidalari
│       │   ├── DomEvents.java        # onClick↔click, event ustuvorligi
│       │   ├── Timers.java           # brauzer event loop primitivlari
│       │   └── EventLoopSchedulerHost.java  # Timers asosidagi SchedulerHost
│       └── test/java/jreact/dom/
│           └── DomTest.java          # 19 ta test (soxta DOM)
├── jreact-web/                       # brauzer bog'lamalari (paket jreact.web, TeaVM JSO)
│   └── src/main/java/jreact/web/
│       ├── JReactWeb.java            # JReactWeb.createRoot("root")
│       ├── JsDomOps.java             # DomOps haqiqiy DOM'da
│       ├── JsTimers.java             # performance.now, MessageChannel, setTimeout
│       └── JsNode.java, JsEvent.java, JsListener.java, JsDocument.java
├── examples/
│   ├── todo/                         # namunaviy ilova (.jsxj), JVM'da HTML chiqaradi
│   └── todo-web/                     # shu ilova brauzerda: WebMain.jsxj, index.html, pom.xml
│       └── smoke/                    # jsdom smoke testi (node)
└── pom.xml                           # Maven: faqat brauzer build'i uchun, boshqa modullar ./build.sh bilan ham yig'iladi
```

## Hozirgi cheklovlar

- Brauzer build'i faqat jsdom'da sinalgan, haqiqiy brauzerda emas. `app.js` ~137 KB (gzip bilan ~45 KB, TeaVM `FULL` + minifikatsiya). Maven build'i `jsxj:compile` plagini o'rniga `exec-maven-plugin` bilan `jsxjc` ni chaqiradi.
- DOM: SVG (namespace), `onMouseEnter/Leave`, `dangerouslySetInnerHTML` yo'q. `onChange` React kabi: matn maydoni `input` da, checkbox/select `change` da.
- Ustuvorlik root bo'yicha, update bo'yicha emas (lane yo'q): bitta render barcha kutayotgan update'larni oladi. Bo'laklab render faqat `LOW`/`IDLE` (`startTransition`) uchun, uni boshqa update to'xtatib qayta boshlamaydi, faqat tezroq tugatadi.
- Error boundary yo'q: render xatosi `flush()` dan chiqadi, commit qilingan daraxt buzilmaydi.
- Brauzer uchun `app.js` build'i yo'q (TeaVM hali ulanmagan).
- `useContext` provider'ni `ret` zanjiri bo'ylab qidiradi (chuqurlikka mutanosib), alohida qiymatlar stekidan foydalanmaydi. `key` reconciler'da ishlaydi.
- `{...spread}` komponentlarda qo'llab-quvvatlanmaydi.
- Bir xil nomli komponentlar turli paketlarda bo'lsa indeks adashadi.
- IDE qo'llab-quvvatlashi (sintaksis bo'yash, autocomplete) JSX qismlari uchun yo'q.
- Litsenziya hali tanlanmagan.

## Yo'l xaritasi

1. ~~**`jreact-reconciler`:** Fiber daraxti, work loop, key'li diff, commit fazasi, hooks dispatcher'i~~ (tayyor)
2. ~~**`jreact-scheduler`:** bo'laklab render qilish, ustuvorlik~~ (tayyor, root darajasida). Qolgani: haqiqiy lane'lar (update bo'yicha ustuvorlik, render'ni to'xtatib qayta boshlash)
3. ~~**`jreact-dom`:** DOM host config, event delegation, TeaVM JSO orqali `jreact-web`~~ (tayyor)
4. **Build:** ~~TeaVM bilan `app.js`~~ (tayyor, `./build.sh web`). Qolgani: alohida Maven plagini (`jsxj:compile`)
5. ~~`Context`, `memo`~~ (tayyor). Bundle: ~137 KB, dev-mode ogohlantirishlar (key) tayyor. Qolgani: boshqa ogohlantirishlar (masalan `value` bor, `onInput` yo'q)
6. IDE plagini (JSX sintaksisini bo'yash va xatolarni ko'rsatish
