# Лаба 2 — перевірка, що тести справді ловлять помилки (AC4)

Для кожного випадку логіку SUT навмисно зламано, запущено тест-клас, після чого зміну відкочено (`git checkout`)
і тести знову проходять. Нижче — diff мутації та ключові рядки виводу Maven.

Відтворити вручну (для скріншоту): внести diff, `./mvnw -Dtest=BookingServiceTest test`, повернути зміну.

## M1. Межа 30 ночей: `>` замінено на `>=`

Падає: `create_nightsWithinLimit_totalIsNightsTimesPrice[3]` (рівно 30 ночей) — бронювання тепер відхиляється.

```diff
@@ -50,7 +50,7 @@ public class BookingService {
-        if (nights > MAX_NIGHTS) {
+        if (nights >= MAX_NIGHTS) {
```

```
[ERROR] com.java.hotelbooking.service.BookingServiceTest.create_nightsWithinLimit_totalIsNightsTimesPrice(int, String)[3] -- Time elapsed: 0.006 s <<< ERROR!
com.java.hotelbooking.exception.BadRequestException: A booking cannot be longer than 30 nights
	at com.java.hotelbooking.service.BookingService.create(BookingService.java:54)
[ERROR] Tests run: 31, Failures: 0, Errors: 1, Skipped: 0
```

Після відкату: `BUILD SUCCESS, усі тести зелені`.

## M2. Прибрано виняток для адміна при перевірці дати заїзду

Падає: `cancel_adminOnCheckInDay_isAllowed` — адміну заборонено скасувати в день заїзду.

```diff
@@ -107,7 +107,7 @@ public class BookingService {
-        if (!isAdmin && !booking.getCheckIn().isAfter(LocalDate.now(clock))) {
+        if (!booking.getCheckIn().isAfter(LocalDate.now(clock))) {
```

```
[ERROR] com.java.hotelbooking.service.BookingServiceTest.cancel_adminOnCheckInDay_isAllowed -- Time elapsed: 0.005 s <<< ERROR!
com.java.hotelbooking.exception.ConflictException: Booking can be cancelled only before the check-in date
	at com.java.hotelbooking.service.BookingService.cancel(BookingService.java:111)
[ERROR] Tests run: 31, Failures: 0, Errors: 1, Skipped: 0
```

Після відкату: `BUILD SUCCESS, усі тести зелені`.

## M3. `confirm` залишає статус `PENDING`

Падає: `confirm_pendingBooking_setsStatusConfirmed` — очікувався `CONFIRMED`.

```diff
@@ -122,7 +122,7 @@ public class BookingService {
-        booking.setStatus(BookingStatus.CONFIRMED);
+        booking.setStatus(BookingStatus.PENDING);
```

```
[ERROR] com.java.hotelbooking.service.BookingServiceTest.confirm_pendingBooking_setsStatusConfirmed -- Time elapsed: 0.008 s <<< FAILURE!
org.opentest4j.AssertionFailedError: 

[ERROR] Tests run: 31, Failures: 1, Errors: 0, Skipped: 0
```

Після відкату: `BUILD SUCCESS, усі тести зелені`.

## M4. `HotelService.list` більше не обрізає пробіли в назві міста

Падає: `list_withCity_trimsCityAndFiltersByIt` — репозиторій викликано з `"  Kyiv "`.

```diff
@@ -24,7 +24,7 @@ public class HotelService {
-                ? hotelRepository.findByCityIgnoreCaseOrderByName(city.trim())
+                ? hotelRepository.findByCityIgnoreCaseOrderByName(city)
```

```
[ERROR] com.java.hotelbooking.service.HotelServiceTest.list_withCity_trimsCityAndFiltersByIt -- Time elapsed: 0.009 s <<< ERROR!
org.mockito.exceptions.misusing.PotentialStubbingProblem: 

[ERROR] Tests run: 15, Failures: 0, Errors: 1, Skipped: 0
```

Після відкату: `BUILD SUCCESS, усі тести зелені`.
