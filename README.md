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
