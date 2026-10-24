# Hotel Booking — Лабораторна робота №1 (Spring Security)

Сервіс бронювання номерів у готелях. Обрано з розрахунком на подальші лаби:
бронювання мають життєвий цикл (`PENDING → CONFIRMED / CANCELLED`), тож далі сюди природно лягають
AOP (аудит змін статусів), async (нотифікації гостям) та Quartz (автоскасування непідтверджених бронювань,
нагадування про заїзд).

## Стек

Java 25, Spring Boot 3.5, Spring Web, Spring Data JPA + Hibernate, PostgreSQL, Liquibase, MapStruct, Lombok,
Spring Security (OAuth2 Resource Server + OAuth2 Client), Keycloak 26.

## Запуск

```bash
./infra.sh up          # Postgres :5432 + Keycloak :8080 (podman; або `docker compose up -d`)
./mvnw spring-boot:run # застосунок на :8081
```

Keycloak імпортує realm `hotel-realm` з `keycloak/hotel-realm.json` (адмінка: http://localhost:8080, `admin/admin`).

| Користувач | Пароль     | Ролі        |
|------------|------------|-------------|
| alice      | alice123   | ADMIN, USER |
| bob        | bob123     | USER        |

`./infra.sh reset-db` видаляє базу, щоб показати створення схеми з нуля.

## Модель даних (Liquibase)

`src/main/resources/db/changelog/` — схема створюється тільки міграціями, `ddl-auto: validate`.

- `hotels` — назва, місто, адреса, зірки, опис (унікальність name+city)
- `rooms` — номер, тип (`SINGLE/DOUBLE/TWIN/SUITE/FAMILY`), місткість, ціна за ніч; FK на готель (`ON DELETE CASCADE`)
- `bookings` — гість (username з токена), дати заїзду/виїзду, кількість гостей, сума, статус, час створення

## API

| Метод  | Шлях                                   | Доступ                                    |
|--------|----------------------------------------|-------------------------------------------|
| GET    | `/api/hotels?city=`                    | всі                                       |
| GET    | `/api/hotels/{id}`                     | всі                                       |
| POST/PUT/DELETE | `/api/hotels[/{id}]`          | ADMIN — **SecurityFilterChain**           |
| GET    | `/api/hotels/{id}/rooms`               | всі                                       |
| POST   | `/api/hotels/{id}/rooms`               | ADMIN — **SecurityFilterChain**           |
| GET    | `/api/rooms/{id}`, `/api/rooms/available?checkIn&checkOut&guests` | всі            |
| PUT/DELETE | `/api/rooms/{id}`                  | ADMIN — **SecurityFilterChain**           |
| GET    | `/api/admin/stats`                     | ADMIN — **SecurityFilterChain**           |
| POST   | `/api/bookings`                        | USER — `@PreAuthorize`                    |
| GET    | `/api/bookings/my`                     | USER — `@PreAuthorize`                    |
| GET    | `/api/bookings`                        | ADMIN — `@PreAuthorize`                   |
| GET    | `/api/bookings/{id}`                   | власник або ADMIN — `@PostAuthorize`      |
| PATCH  | `/api/bookings/{id}/cancel`            | власник або ADMIN                         |
| PATCH  | `/api/bookings/{id}/confirm`           | ADMIN — `@PreAuthorize`                   |
| DELETE | `/api/bookings/{id}`                   | ADMIN — `@PreAuthorize`                   |

Бізнес-правила бронювання: `checkOut > checkIn`, не більше 30 ночей, гостей ≤ місткості номера,
немає перетину з активними (`PENDING/CONFIRMED`) бронюваннями того ж номера (409),
сума = ночі × ціна. Гість може скасувати лише своє бронювання і лише до дати заїзду.

## Безпека

`configuration/SecurityConfig.java` — два `SecurityFilterChain`:

1. **`/api/**` (Order 1)** — stateless, тільки `Authorization: Bearer <JWT>`. Підпис і `iss` перевіряються
   по JWKS Keycloak. Ролі беруться з `realm_access.roles` (`security/KeycloakRealmRoleConverter`) → `ROLE_ADMIN`,
   ім'я користувача — з `preferred_username`. Сесійна cookie тут не працює (401).
2. **решта (Order 2)** — OIDC login (`oauth2Login`): `/ui/**` редиректить на сторінку логіну Keycloak,
   після входу — сесія. `/logout` виходить і з застосунку, і з Keycloak.

UI (`static/ui/index.html`) бере access token поточного користувача з `/ui/token` і викликає API як звичайний
JWT-клієнт.

**Два способи обмеження доступу:**
- *SecurityFilterChain* — URL/метод: мутації `/api/hotels/**`, `/api/rooms/**` і весь `/api/admin/**` лише для ADMIN.
- *`@PreAuthorize` / `@PostAuthorize`* — у `service/BookingService.java`. Фільтр для `/api/bookings/**` вимагає лише
  автентифікації, тож bob проходить фільтр, але `GET /api/bookings` блокується на рівні методу
  (`AccessDeniedException` → 403).

## Захист роботи

- `http/hotel-booking.http` — колекція для IntelliJ HTTP Client (оточення `local`, `http/http-client.env.json`).
  Спочатку виконати запити `0.1` і `0.2` — вони зберігають токени alice і bob.
- `http/demo.sh` — ті самі сценарії через curl з перевіркою очікуваних статусів (`VERBOSE=1` показує тіла відповідей).
- OIDC: відкрити http://localhost:8081 → «Увійти через Keycloak».

Отримати токен вручну:

```bash
curl -s http://localhost:8080/realms/hotel-realm/protocol/openid-connect/token \
  -d grant_type=password -d client_id=hotel-app -d username=bob -d password=bob123
```

---

# Лабораторна робота №2 (Unit-тестування)

```bash
./mvnw test     # 108 unit-тестів + звіт JaCoCo: target/site/jacoco/index.html
./mvnw verify   # те саме + перевірка порогу покриття (≥ 60% інструкцій, інакше збірка падає)
```

Тести не потребують Postgres/Keycloak: JUnit 5 + Mockito (`@Mock` / `@InjectMocks`), без Spring-контексту.
Покриття: ~86% інструкцій, ~92% гілок; усі сервіси — 100%.

| Тест-клас | Що перевіряє |
|-----------|--------------|
| `service/BookingServiceTest` | створення (ціна = ночі × ціна, межі 1/30 ночей, місткість, перетин дат), скасування (власник/адмін/чужий, до/в день заїзду, повторне), підтвердження, видалення |
| `service/HotelServiceTest`, `RoomServiceTest` | фільтр за містом, унікальність назви в місті / номера в готелі, 404, пошук вільних номерів |
| `service/AdminServiceTest` | статистика по всіх статусах |
| `security/KeycloakRealmRoleConverterTest` | `realm_access.roles` → `ROLE_*`, відсутні/некоректні claims |
| `exception/GlobalExceptionHandlerTest` | статуси 400/403/404/409, тіло `ApiError`, перша помилка на поле |
| `domain/mapper/MapperImplTest` | згенеровані MapStruct-мапери (створюються через `new`) |
| `controller/*Test` | делегування в сервіси, username з `Authentication` |

- Детермінований час: `BookingService` бере «сьогодні» з біна `Clock` (`configuration/ClockConfig`), у тестах — замоканий годинник.
- Побічні ефекти перевіряються через `verify(...)` / `ArgumentCaptor` (`save`, `delete`, `updateEntity`, зміна статусу).
- Доказ, що тести ловлять зламану логіку (AC4): `docs/lab2/mutation-check.md`.

---

# Лабораторна робота №3 (Анотації)

Три кастомні анотації, кожна зі своїм механізмом.

### Тип 1. Bean Validation constraint: `@ValidStayPeriod`

`domain/annotation/ValidStayPeriod.java` + `domain/validation/StayPeriodValidator.java`

Анотація рівня класу на `BookingCreateRequest`. Дата виїзду має бути пізніше дати заїзду, а тривалість —
не більше `maxNights` ночей (атрибут, за замовчуванням 30). Валідатор підключається через
`@Constraint(validatedBy = ...)` і працює з будь-яким DTO, що реалізує інтерфейс `StayPeriod`.
Порушення прив'язане до поля `checkOut`, а повідомлення називає конкретні дати чи кількість ночей:

```json
{"status":400,"message":"Validation failed",
 "fieldErrors":{"checkOut":"stay of 61 nights (2027-03-01 - 2027-05-01) exceeds the maximum of 30 nights"}}
```

Значення передаються як параметри повідомлення (`addMessageParameter`), а не конкатенацією в шаблон,
щоб їх не можна було інтерпретувати як вирази.

### Тип 2. Композитна анотація: `@PostCreated`

`web/annotation/PostCreated.java` = `@RequestMapping(method = POST)` + `@ResponseStatus(HttpStatus.CREATED)`,
шлях пробрасується через `@AliasFor`. Замінила пару `@PostMapping` + `@ResponseStatus(CREATED)` у
`POST /api/hotels`, `POST /api/hotels/{id}/rooms`, `POST /api/bookings`.
`@PostMapping` має `@Target(METHOD)`, тому мета-анотацією бути не може. `@PostCreated` побудована так само,
як Spring будує сам `@PostMapping`.

### Тип 3. Резолвер аргументів MVC: `@CurrentUsername`

`web/annotation/CurrentUsername.java` + `web/CurrentUsernameArgumentResolver.java`, реєстрація у
`configuration/WebConfig.java`. Підставляє в `String`-параметр ім'я автентифікованого користувача
(`preferred_username` з JWT). `BookingController.create` і `myBookings` більше не приймають `Authentication`
і не витягують ім'я вручну.

### Тести

| Тест | AC |
|------|----|
| `domain/validation/StayPeriodValidatorTest` | 1–3: валідатор знаходить сам Bean Validation; валідні періоди без порушень; точні повідомлення для «виїзд ≤ заїзд» і «> 30 ночей»; кастомний `maxNights` |
| `controller/PostCreatedWebMvcTest` | 4–5: merged-атрибути `@PostCreated` = оригінальна пара; ендпоінт з оригінальними анотаціями й ендпоінт з `@PostCreated` однаково дають 201 на POST і 405 на GET; реальні `POST /api/hotels` і `/api/hotels/{id}/rooms` → 201 |
| `web/CurrentUsernameArgumentResolverTest` | 6–7: `supportsParameter` true/false (без анотації, не `String`); `resolveArgument` повертає ім'я або кидає виняток без користувача |
| `controller/BookingControllerWebMvcTest` | 8: реальний запит → резолвлене ім'я потрапляє в `bookingService.create(..., "bob")` / `listMine("bob")` і у відповідь; невалідний період → 400 з `fieldErrors.checkOut` |

---

# Лабораторна робота №4 (AOP)

Три пари «власна анотація + аспект» (`domain/annotation/*`, `aspect/*`), кожна з іншим типом advice:

| Анотація | Аспект / advice | Де застосована | Що робить |
|----------|-----------------|----------------|-----------|
| `@AuditBooking(action)` | `BookingAuditAspect`, `@AfterReturning` | `BookingService.create / confirm / cancel` | пише рядок у таблицю `booking_audit` (дія, новий статус, хто, коли); невдалі виклики не аудитуються |
| `@Idempotent` | `IdempotencyAspect`, `@Around` | `BookingController.create` | повтор запиту з тим самим заголовком `Idempotency-Key` повертає збережену відповідь **без** `proceed()`; ключі прив'язані до користувача, мають TTL, помилки не кешуються |
| `@RateLimited(permits, perSeconds)` | `RateLimitAspect`, `@Before` | `BookingService.create` (10 за хвилину) | ковзне вікно на користувача; перевищення → `RateLimitExceededException` → **429** + `Retry-After` |

Нове в API: `GET /api/admin/audit?bookingId=` (журнал, ADMIN), `PATCH /api/bookings/confirm-batch` (тіло — масив id, ADMIN).
Міграція `005-create-booking-audit.xml` (без FK, щоб журнал переживав видалення бронювання).

### Self-invocation

`BookingService.confirmBatch(ids)` викликає `this.confirm(id)` для кожного id. Виклик іде повз Spring-проксі, тому
`@AuditBooking` на `confirm` мовчки не спрацьовує: бронювання підтверджуються, але рядків `CONFIRM` у журналі немає,
і жодної помилки. Той самий `confirm`, викликаний ззовні (`PATCH /api/bookings/{id}/confirm`), аудитується.
Залишено свідомо, як того вимагає завдання.

### Перевірка

- Unit-тести advice без Spring-контексту, на моках `JoinPoint` / `ProceedingJoinPoint`: `aspect/BookingAuditAspectTest`,
  `IdempotencyAspectTest` (чи викликається `proceed()`: без ключа, повтор, інший користувач, TTL, помилка, задовгий ключ),
  `RateLimitAspectTest`.
- `aspect/SelfInvocationTest` — справжній AOP-проксі (`AspectJProxyFactory`, без контексту): зовнішній `confirm` → аудит є,
  `confirmBatch` → `verify(auditRepository, never()).save(...)`.
- Наживо: `http/lab4-aop-demo.sh` (застосунок має бути запущений). Ліміт — 10 бронювань за хвилину,
  тож між повторними запусками скрипта (або після `http/demo.sh`) треба зачекати хвилину.

---

# Лабораторна робота №5 (Асинхронна обробка)

Три повільні побічні ефекти (зовнішні системи симульовані, затримки в `hotel.simulation.*`):

| Що | Як | Де |
|----|----|----|
| E-mail гостю (~1 с) | **fire-and-forget**: `@Async void GuestNotifier.send` | `BookingService` публікує `BookingChangedEvent` → `BookingNotificationListener` (`@TransactionalEventListener`, після коміту) |
| Рендер рахунку (~1 с) | `@Async CompletableFuture<Invoice> InvoiceRenderer.render` | `GET /api/bookings/{id}/invoice?currency=USD` |
| Курс валют, зовнішній API (~0,7 с) | `@Async CompletableFuture<BigDecimal> ExchangeRateClient.convertFromUah` | той самий ендпоінт |

`BookingDocumentService.invoice` запускає обидва виклики одразу і об'єднує їх через `thenCombine`
(≈1 с замість ≈1,7 с послідовно).

### Виконавець (`configuration/AsyncConfig`)

`ThreadPoolTaskExecutor` з явними `core-pool-size`/`max-pool-size`/`queue-capacity` і префіксом потоків `hotel-async-`
(`application.yml` → `hotel.async`). Його повертає `AsyncConfigurer.getAsyncExecutor()`, тому всі `@Async` працюють на ньому,
а не на виконавці Boot за замовчуванням. `TaskDecorator` переносить `SecurityContext` у робочий потік.

### Збої, таймаути, перевантаження

| Ситуація | Рішення |
|----------|---------|
| Сервіс курсів упав або невідома валюта | `exceptionally` → рахунок повертається без конвертації, з поясненням у `note` |
| Сервіс курсів відповідає надто довго | `orTimeout(hotel.async.exchange-rate-timeout)` → той самий fallback |
| Рахунок рендериться надто довго | `get(hotel.async.documents-timeout)` → **503** + `Retry-After` |
| `@Async void` (e-mail) кинув виняток | `LoggingAsyncUncaughtExceptionHandler`: ERROR у лог (метод + аргументи) і метрика `hotel.async.failures` |
| Виконавець переповнений (`TaskRejectedException`) при рахунку | **503** «try again in 5 seconds» + `Retry-After`, а не 500 |
| Виконавець переповнений при e-mail | лист потрапляє в `PendingEmailOutbox`, `@Scheduled` повторює відправку (`hotel.async.outbox-retry-delay`) |

### Тести

- `async/AsyncExecutionTest`: легкий Spring-контекст (лише async-біни, без БД). Перевіряє, що виклик повертає керування
  до завершення повільного методу, потік має префікс `hotel-async-`, рендер і курс виконуються **одночасно**
  (обидва мають зустрітися на `CyclicBarrier`, послідовно це неможливо), fallback, таймаути і запис збою `void`-методу хендлером.
- `async/AsyncRejectionTest`: виконавець 1 потік / черга 1. Третя задача → `TaskRejectedException`, рахунок → `ServiceBusyException`,
  лист → outbox, після звільнення outbox доставляє лист.
- Unit-тести без Spring: `BookingDocumentServiceTest`, `BookingNotificationListenerTest`, `PendingEmailOutboxTest`,
  `LoggingAsyncUncaughtExceptionHandlerTest`, `IntegrationSimulationTest`.
- Затримки в тестах керуються через `ControllableSlowCalls` (блокування / відпускання / бар'єр), без `Thread.sleep`.
- Наживо: `http/lab5-async-demo.sh`.

---

# Лабораторна робота №6 (Quartz Scheduler)

### Сховище розкладу

- Схема `QRTZ_*` для PostgreSQL взята з `quartz.jar` (`org/quartz/impl/jdbcjobstore/tables_postgres.sql`) у
  `db/quartz/tables_postgres.sql` (без `DROP`/`COMMIT`) і створюється changeset'ом `006-create-quartz-tables.xml`.
- `spring.quartz.job-store-type: jdbc`, `jdbc.initialize-schema: never` (схемою володіє Liquibase),
  `driverDelegateClass: PostgreSQLDelegate`, `overwrite-existing-jobs: true`: при кожному старті визначення з `QuartzConfig`
  замінюють збережені, тож після деплою завдання не губляться і не дублюються (перевірено: після рестарту 3 тригери / 2 завдання).

### Завдання (`job/*`, усі визначення в `configuration/QuartzConfig`)

| Job | JobDataMap (за замовчуванням) | Тригери | Misfire |
|-----|-------------------------------|---------|---------|
| `ExpirePendingBookingsJob` — скасовує PENDING, які не підтвердили за `maxPendingMinutes` або в яких уже настав день заїзду; аудит від `system` + лист гостю | `maxPendingMinutes=1440`, `batchSize=100`, `maxRetries=2` | `expirePendingEvery5Minutes` (simple, 5 хв) | `NextWithRemainingCount`: пропущені запуски не наздоганяються, наступний обробить усе |
| `CheckInReminderJob` — нагадування гостям CONFIRMED-бронювань за `daysBefore` днів до заїзду | `daysBefore=1`, `batchSize=100`, `maxRetries=0` | `checkInReminderDayBefore` (cron `0 0 18 * * ?`, Europe/Kyiv, перевизначає `maxRetries=1`) і `checkInReminderWeekBefore` (cron `0 0 10 * * ?`, Europe/Kyiv, перевизначає `daysBefore=7`, `batchSize=500`) | day-before: `FireAndProceed` (гість приїжджає завтра, надіслати якнайшвидше); week-before: `DoNothing` (необов'язкове, нагадування за день однаково прийде) |

- Завдання «тонкі»: читають параметри (сетери `QuartzJobBean`) і роблять один виклик сервісу. Транзакція відкривається
  на рівні сервісу (`BookingMaintenanceService.expirePendingBookings`, `CheckInReminderService.sendReminders`).
- Обидва завдання мають `@DisallowConcurrentExecution`: запуск, що настав, поки попередній ще працює, чекає на нього.
- **Ідемпотентність:** прострочене бронювання стає CANCELLED, тож повторний запуск його не знайде. Відправлені нагадування
  записуються в `booking_reminders` (унікальність `booking_id + days_before`, changeset `007`).
- **Збої (`JobFailurePolicy`):** поки `refireCount < maxRetries`, `JobExecutionException(refireImmediately=true)`;
  потім ERROR у лог і очікування наступного запуску за розкладом. Кількість спроб обмежена, нескінченного циклу немає.
- **Параметри:** відсутній або з помилкою написаний ключ ігнорується Quartz'ом, і діє задокументоване значення за замовчуванням
  з полів job-класу. Значення неправильного типу (`batchSize="ten"`) валить запуск до виклику сервісу. Невалідне значення
  (`maxPendingMinutes=0`) сервіс відхиляє явним `IllegalArgumentException`.

Адмінські ендпоінти (ADMIN): `GET /api/admin/jobs` (розклад з БД, наступні запуски, дані тригерів),
`POST /api/admin/jobs/expire-pending?maxPendingMinutes=`, `POST /api/admin/jobs/check-in-reminders?daysBefore=`
(разовий запуск, параметри працюють як дані тригера).

### Тести

| Тест | Що перевіряє |
|------|--------------|
| `configuration/QuartzConfigTest` (без Spring) | імена/групи, тригер → `JobKey`, розклади, misfire-інструкції, часовий пояс `Europe/Kyiv` (і що невідомий id тихо стає GMT), злиття JobDataMap: тригер перемагає, решта з job |
| `job/ExpirePendingBookingsJobTest`, `CheckInReminderJobTest` | один виклик сервісу з параметрами, значення за замовчуванням, прив'язка через справжній `QuartzJobBean.execute` (ключ з помилкою → default, неправильний тип → виняток), `@DisallowConcurrentExecution`, рішення про повтор |
| `job/QuartzSchedulerBehaviourTest` | справжній in-memory планувальник Quartz: невдале завдання виконується рівно `maxRetries + 1` разів і зупиняється; другий запуск чекає, поки завершиться перший |
| `service/JobServicesIntegrationTest` (`@DataJpaTest`, H2, кожен виклик у власній транзакції) | повторний запуск нічого не змінює (статуси, аудит, події), `batchSize`, вибір лише потрібних бронювань, нагадування за 1 і 7 днів незалежні |

Наживо: `http/lab6-quartz-demo.sh`.
