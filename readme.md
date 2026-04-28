# OTP Service

Курсовая работа по Специализированным инструментам разработки на языке Java.
Backend-сервис для защиты операций одноразовыми OTP-кодами.

Приложение позволяет:
- регистрировать пользователей;
- выполнять вход и выход по токену;
- разграничивать роли `ADMIN` и `USER`;
- настраивать длину OTP-кода и время его жизни;
- генерировать OTP для `operationId`;
- валидировать OTP-коды;
- автоматически переводить просроченные коды в статус `EXPIRED`;
- доставлять OTP через несколько каналов:
  - файл;
  - email;
  - Telegram;
  - SMS через SMPP-эмулятор.

---

## Назначение проекта

Проект выполнен как учебный backend-сервис в рамках курсовой работы по Специализированным инструментам разработки на языке Java.

Основная задача сервиса — защищать операции пользователя с помощью временных OTP-кодов, которые можно получить по разным каналам доставки и затем использовать для подтверждения действия.

Проект предназначен для демонстрации навыков backend-разработки на Java:
- работы с PostgreSQL и JDBC;
- построения REST API;
- токенной аутентификации и авторизации;
- разграничения ролей;
- интеграции с внешними каналами доставки;
- автоматизации тестирования;
- базового security hardening для OTP-потока.
---

## Используемые технологии

- Java 17
- Spring Boot 3.5.13
- Spring Dependency Management 1.1.7
- PostgreSQL 17
- PostgreSQL JDBC Driver 42.7.10
- JDBC
- Gradle 9.3
- Spring Boot Starter Web
- Spring Boot Starter Test
- JUnit BOM 6.0.0
- JUnit Jupiter
- JUnit Platform Launcher
- jBCrypt 0.4
- JJWT 0.13.0
- Angus Mail 2.0.5
- jSMPP 3.0.1

Дополнительные версии, заданные в `build.gradle.kts` для устранения уязвимостей в зависимостях:
- Tomcat 10.1.54 — обновлён встроенный servlet-контейнер;
- SnakeYAML 2.6 — обновлена библиотека для работы с YAML.

Эти версии зафиксированы отдельно от базового dependency management, чтобы использовать безопасные версии библиотек.

Также в проекте используются:
- Checkstyle
- SpotBugs
- Spotless

## Структура проекта

```text
otpService/
├── build.gradle.kts                    # сборка проекта, зависимости, плагины, проверки качества
├── readme.md                           # документация проекта
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── org/example/
│   │   │       ├── OtpServiceApplication.java
│   │   │       │   # точка входа Spring Boot приложения
│   │   │       │
│   │   │       ├── config/
│   │   │       │   ├── DbConfig.java
│   │   │       │   └── RequestLoggingFilter.java
│   │   │       │   # конфигурация подключения к БД и логирование HTTP-запросов
│   │   │       │
│   │   │       ├── controller/
│   │   │       │   ├── AdminController.java
│   │   │       │   ├── AuthController.java
│   │   │       │   ├── GlobalExceptionHandler.java
│   │   │       │   ├── HealthController.java
│   │   │       │   ├── OtpController.java
│   │   │       │   └── TelegramController.java
│   │   │       │   # REST API приложения: auth, admin API, OTP API,
│   │   │       │   # health-check, Telegram binding
│   │   │       │
│   │   │       ├── dto/
│   │   │       │   ├── DeleteUserResponse.java
│   │   │       │   ├── GenerateOtpRequest.java
│   │   │       │   ├── LoggedInUserResponse.java
│   │   │       │   ├── LoginRequest.java
│   │   │       │   ├── LoginResponse.java
│   │   │       │   ├── LogoutResponse.java
│   │   │       │   ├── OtpGenerationResponse.java
│   │   │       │   ├── OtpValidationResponse.java
│   │   │       │   ├── RegisterRequest.java
│   │   │       │   ├── RegisterResponse.java
│   │   │       │   ├── UpdateOtpConfigRequest.java
│   │   │       │   ├── UpdateOtpConfigResponse.java
│   │   │       │   ├── UserResponse.java
│   │   │       │   └── ValidateOtpRequest.java
│   │   │       │   # DTO для входящих запросов и исходящих ответов API
│   │   │       │
│   │   │       ├── exception/
│   │   │       │   ├── NotFoundException.java
│   │   │       │   └── UnauthorizedException.java
│   │   │       │   └── RateLimitExceededException.java
│   │   │       │   # прикладные исключения
│   │   │       │
│   │   │       ├── model/
│   │   │       │   ├── DeliveryChannel.java
│   │   │       │   ├── OtpCode.java
│   │   │       │   ├── OtpConfig.java
│   │   │       │   ├── OtpStatus.java
│   │   │       │   ├── Role.java
│   │   │       │   └── User.java
│   │   │       │   # доменные модели приложения
│   │   │       │
│   │   │       ├── repository/
│   │   │       │   ├── ConnectionFactory.java
│   │   │       │   ├── OtpCodeRepository.java
│   │   │       │   ├── OtpConfigRepository.java
│   │   │       │   ├── UserRepository.java
│   │   │       │   └── UserSessionRepository.java
│   │   │       │   # JDBC-слой доступа к PostgreSQL
│   │   │       │
│   │   │       ├── security/
│   │   │       │   ├── AuthUtil.java
│   │   │       │   ├── PasswordHasher.java
│   │   │       │   └── RequestAuthService.java
│   │   │       │   # Bearer token, проверка роли и сессии, хеширование паролей
│   │   │       │
│   │   │       ├── service/
│   │   │       │   ├── AuthService.java
│   │   │       │   ├── EmailDeliveryService.java
│   │   │       │   ├── FileDeliveryService.java
│   │   │       │   ├── GenerateOtpRateLimitService.java
│   │   │       │   ├── OtpExpirationService.java
│   │   │       │   ├── OtpService.java
│   │   │       │   ├── SessionCleanupService.java
│   │   │       │   ├── SmsDeliveryService.java
│   │   │       │   ├── TelegramBindingService.java
│   │   │       │   ├── TelegramDeliveryService.java
│   │   │       │   └── TokenService.java
│   │   │       │   # бизнес-логика приложения, OTP-поток, доставка,
│   │   │       │   # JWT, user sessions, cleanup, rate limit, brute force protection
│   │   │       │
│   │   │       └── util/
│   │   │           └── AuthValidationUtil.java
│   │   │           # общая валидация входных данных для auth-запросов
│   │   │
│   │   └── resources/
│   │       ├── META-INF/
│   │       │   └──additional-spring-configurations-metadata.json 
│   │       ├── application.properties
│   │       ├── db.properties.example
│   │       └── db/
│   │           └── migration/
│   │               └── schema.sql
│   │               # SQL-схема PostgreSQL
│   │
│   └── test/
│       ├── java/
│       │   └── org/example/
│       │       ├── controller/
│       │       │   ├── AdminControllerTest.java
│       │       │   ├── AuthControllerTest.java
│       │       │   ├── HealthControllerTest.java
│       │       │   ├── OtpControllerTest.java
│       │       │   └── TelegramControllerTest.java
│       │       │   # controller-level tests
│       │       │
│       │       ├── integration/
│       │       │   ├── BaseIntegrationTest.java
│       │       │   # базовый класс integration tests
│       │       │   │
│       │       │   ├── support/
│       │       │   │   ├── TestDbHelper.java
│       │       │   │   ├── TestHttpAssertions.java
│       │       │   │   ├── TestRequests.java
│       │       │   │   └── TestUsers.java
│       │       │   │   # общие test helpers для интеграционных тестов
│       │       │   │
│       │       │   ├── AdminApiITTest.java
│       │       │   ├── AdminUsersApiITTest.java
│       │       │   ├── AuthApiITTest.java
│       │       │   ├── DeliveryValidationApiITTest.java
│       │       │   ├── HealthApiITTest.java
│       │       │   ├── OtpApiITRefactorTest.java
│       │       │   ├── OtpAuthApiITTest.java
│       │       │   ├── OtpConcurrentBruteforceApiITTest.java
│       │       │   ├── OtpConcurrentSameOperationGenerateApiITTest.java
│       │       │   ├── OtpConcurrentValidationApiITTest.java
│       │       │   ├── OtpConfigValidationApiITTest.java
│       │       │   ├── OtpGenerateConcurrentRateLimitApiITTest.java
│       │       │   ├── OtpGenerateRateLimitApiITTest.java
│       │       │   ├── OtpRequestValidationApiITTest.java
│       │       │   ├── RegisterApiITRefactorTest.java
│       │       │   ├── RegisterValidationApiITTest.java
│       │       │   ├── TelegramBindingApiITTest.java
│       │       │   ├── UserDeleteCascadeApiITTest.java
│       │       │   ├── UserSessionRepositoryActiveSessionITTest.java
│       │       │   └── UserSessionRepositoryCleanupITTest.java
│       │       │   # интеграционные тесты API, авторизации, OTP,
│       │       │   # конкурентных сценариев и JDBC-репозиториев
│       │       │
│       │       ├── security/
│       │       │   └── PasswordHasherTest.java
│       │       │   # тесты security-утилит
│       │       │
│       │       ├── service/
│       │       │   ├── AuthServiceTest.java
│       │       │   ├── GenerateOtpRateLimitServiceTest.java
│       │       │   ├── OtpServiceCleanupTest.java
│       │       │   ├── OtpServiceTest.java
│       │       │   ├── SessionCleanupServiceTest.java
│       │       │   ├── TelegramBindingServiceTest.java
│       │       │   └── TokenServiceTest.java
│       │       │   # unit-тесты сервисного слоя
│       │       │
│       │       └── util/
│       │           └── AuthValidationUtilTest.java
│       │           # тесты util-классов
│       │
│       └── resources/
│           ├── application-test.properties
│           ├── db.properties
│           └── testAPI.http
│           # test profile, test database и ручные HTTP-сценарии
```
### Описание пакетов проекта

#### Основной код (`src/main/java/org/example`)

- **`config`** — конфигурационные классы приложения. Здесь настраивается подключение к базе данных (`DbConfig`) и регистрируются веб-фильтры, например `RequestLoggingFilter` для логирования запросов.
- **`controller`** — REST-контроллеры, которые обрабатывают HTTP-запросы, выполняют базовую валидацию входных данных и передают управление в слой сервисов (`AuthController`, `OtpController`, `AdminController` и др.). Также включает `GlobalExceptionHandler` для централизованной обработки ошибок.
- **`dto`** — объекты передачи данных для запросов и ответов API. Используются для сериализации и десериализации JSON (`LoginRequest`, `GenerateOtpRequest`, `UserResponse` и др.).
- **`exception`** — пользовательские классы исключений, отражающие ошибки бизнес-логики (`NotFoundException`, `RateLimitExceededException`, `UnauthorizedException`).
- **`model`** — доменные сущности приложения и перечисления (`User`, `OtpCode`, `OtpConfig`, `Role`, `DeliveryChannel`), отражающие бизнес-модель и структуру данных.
- **`repository`** — слой доступа к данным. Содержит классы для прямого взаимодействия с PostgreSQL через JDBC (`UserRepository`, `OtpCodeRepository`, `UserSessionRepository`), а также фабрику подключений (`ConnectionFactory`).
- **`security`** — компоненты, связанные с безопасностью приложения: хеширование паролей (`PasswordHasher`), работа с токенами (`AuthUtil`) и авторизация запросов (`RequestAuthService`).
- **`service`** — слой бизнес-логики. Содержит основные сервисы (`OtpService`, `AuthService`, `TokenService`), фоновые задачи (`SessionCleanupService`, `OtpExpirationService`) и реализации каналов доставки OTP (`EmailDeliveryService`, `TelegramDeliveryService`, `SmsDeliveryService`, `FileDeliveryService`).
- **`util`** — вспомогательные утилиты общего назначения, например `AuthValidationUtil`.

#### Тестовый код (`src/test/java/org/example`)

- **`controller`** — тесты контроллеров, проверяющие обработку запросов и формирование HTTP-ответов с использованием моков.
- **`integration`** — интеграционные тесты приложения (`*ITTest`). Проверяют работу системы в сборе: взаимодействие с тестовой базой данных, сквозные сценарии (регистрация → логин → генерация OTP → валидация), каскадное удаление, работу rate limit, а также устойчивость к конкурентным запросам и перебору OTP.
- **`integration.support`** — вспомогательные классы для интеграционных тестов: проверки HTTP-ответов (`TestHttpAssertions`), хелперы для работы с тестовой БД (`TestDbHelper`) и фабрики тестовых данных (`TestUsers`, `TestRequests`).
- **`security`** — тесты для проверки хеширования паролей и security-утилит.
- **`service`** — тесты сервисного слоя. Изолированно проверяют бизнес-логику генерации и валидации OTP, работу ограничений, очистку сессий и другие сценарии с использованием моков репозиториев.
- **`util`** — тесты для вспомогательных утилит.

---
## Основные сущности

В проекте используются основные таблицы:
- `users` — пользователи системы;
- `otp_config` — конфигурация OTP-кодов;
- `otp_codes` — сгенерированные OTP-коды;
- `user_sessions` — активные пользовательские сессии для токенной аутентификации.

### Статусы OTP
- `ACTIVE` — код активен;
- `USED` — код успешно использован;
- `EXPIRED` — срок действия кода истёк.

---
## Роли пользователей

### ADMIN
Администратор может:
- изменять длину OTP-кода и срок его действия;
- получать список всех пользователей, кроме администраторов;
- удалять пользователей вместе с их OTP-кодами;
- просматривать пользователей с активными сессиями.

### USER
Обычный пользователь может:
- генерировать OTP для операции;
- валидировать OTP;
- привязывать Telegram для получения кодов;
- получать OTP по доступным каналам доставки.

---
## Каналы доставки OTP

Сервис поддерживает четыре канала доставки OTP.

### FILE
OTP сохраняется в указанный файл.  
Для канала `FILE` поле `deliveryTarget` обязательно и должно содержать путь к файлу.

### EMAIL
OTP отправляется на email пользователя, сохранённый в базе данных.  
Для канала `EMAIL` поле `deliveryTarget` передавать нельзя.

### TELEGRAM
OTP отправляется в Telegram после предварительной привязки пользователя к боту.  
Для канала `TELEGRAM` поле `deliveryTarget` передавать нельзя.

Сценарий привязки Telegram:
1. пользователь входит в систему;
2. запускает привязку Telegram;
3. получает письмо со ссылкой на Telegram-бота;
4. открывает ссылку и нажимает Start;
5. сервис сохраняет `telegram_chat_id`;
6. после этого OTP можно получать через Telegram.

### SMS
OTP отправляется через SMPP-эмулятор.  
Для локального тестирования использовался Auron SMPP simulator.  
Для канала `SMS` поле `deliveryTarget` передавать нельзя.

---
## База данных

Используется PostgreSQL 17.

Особенности схемы:
- у пользователей есть роли `ADMIN` и `USER`;
- в таблице `otp_config` допускается только одна запись;
- OTP-коды связаны с пользователем;
- при удалении пользователя связанные OTP-коды удаляются автоматически;
- статусы OTP фиксируются на уровне базы и приложения;
- при повторной генерации OTP для той же пары `userId + operationId` предыдущие активные OTP переводятся в `EXPIRED`;
- активные пользовательские сессии хранятся в таблице `user_sessions`;
- logout помечает сессию как отозванную, а истёкшие и отозванные сессии очищаются по расписанию.

SQL-схема находится в файле `src/main/resources/db/migration/schema.sql`.

---
## Конфигурация

Для запуска приложения необходимо настроить:
- подключение к PostgreSQL;
- `db.properties` для основной базы данных;
- отдельный `db.properties` для тестовой базы данных;
- JWT secret и время жизни токена;
- SMTP-настройки для email;
- параметры Telegram-бота;
- параметры SMPP simulator;
- настройки rate limiting для генерации OTP.

Основные параметры приложения находятся в `application.properties`. Для тестовой базы можно задавать отдельные параметры в файле `application-test.properties`.

---
## Подготовка к запуску

Перед запуском приложения необходимо:
1. создать базу данных PostgreSQL;
2. применить SQL-схему из файла `src/main/resources/db/migration/schema.sql`;
3. настроить конфигурационные файлы;
4. при необходимости подготовить SMTP, Telegram и SMPP;
5. убедиться, что тестовая база данных тоже создана и настроена отдельно от основной.

---

## Запуск приложения

После подготовки базы данных и конфигурации приложение можно запустить через Gradle.

```bash
./gradlew bootRun
```

После запуска сервис по умолчанию доступен по адресу:
```
http://localhost:8080
```

---
## Основные API endpoints

### Auth
- `POST /auth/register` — регистрация нового пользователя
- `POST /auth/login` — вход в систему и получение токена
- `POST /auth/logout` — выход из системы и отзыв активной сессии

### Health
- `GET /health` — проверка доступности приложения
- `GET /health/db` — проверка подключения к базе данных

### Admin API (`ADMIN`)
- `GET /admin/users` — получить список всех пользователей, кроме администраторов
- `DELETE /admin/users/{id}` — удалить пользователя и связанные с ним OTP-коды
- `GET /admin/logged-in-users` — получить список пользователей с активными сессиями
- `GET /admin/otp-config` — получить текущую конфигурацию OTP-кодов
- `PUT /admin/otp-config` — изменить длину OTP-кода и время его жизни

### User OTP API (требует авторизацию)
- `POST /otp/generate` — сгенерировать OTP для `operationId` и отправить его по выбранному каналу
- `POST /otp/validate` — проверить OTP-код для указанной операции

### Telegram API (требует авторизацию)
- `POST /telegram/bind/start` — запустить привязку Telegram
- `POST /telegram/bind/complete` — завершить привязку Telegram


---
## Основные сценарии использования

### Сценарий 1. Регистрация и вход
1. Пользователь регистрируется.
2. Выполняет вход.
3. Получает токен для дальнейшей работы с API.

### Сценарий 2. Генерация OTP
1. Пользователь отправляет запрос на генерацию OTP.
2. Передаёт `operationId`.
3. Указывает канал доставки.
4. Для канала `FILE` дополнительно передаёт `deliveryTarget` — путь к файлу.
5. Для каналов `EMAIL`, `SMS` и `TELEGRAM` адрес доставки берётся из данных пользователя в базе.
6. Получает OTP через выбранный канал.

### Сценарий 3. Проверка OTP
1. Пользователь отправляет `operationId` и код.
2. Сервис проверяет код.
3. При успехе код получает статус `USED`.

### Сценарий 4. Работа администратора
1. Администратор входит в систему.
2. Просматривает список пользователей.
3. Изменяет конфигурацию OTP.
4. При необходимости удаляет пользователя.
5. Администратор может просматривать список пользователей и список пользователей с активными сессиями.

### Сценарий 5. Привязка Telegram
1. Пользователь запускает привязку Telegram.
2. Получает письмо со ссылкой на бота.
3. Подтверждает привязку.
4. После этого может получать OTP через Telegram.

---
## Безопасность и ограничения

В проекте реализованы следующие защитные механизмы:
- пароли пользователей хранятся в виде bcrypt-хеша;
- доступ к API защищён токенами;
- обычные пользователи не имеют доступа к admin API;
- пользовательские сессии хранятся в базе данных в таблице `user_sessions`, поэтому logout и проверка активной сессии не зависят от перезапуска приложения;
- валидация OTP выполняется атомарно на уровне SQL, что уменьшает риск гонки состояний при повторной параллельной проверке одного и того же кода;
- генерация OTP для одной и той же пары `userId + operationId` выполняется атомарно на уровне БД через транзакционный repository flow с PostgreSQL advisory transaction lock;
- при повторной генерации OTP для одной и той же операции старые активные OTP переводятся в `EXPIRED`, а новый код становится единственным актуальным;
- при ошибке доставки OTP после создания запись удаляется, чтобы не оставлять активный код в базе без фактической доставки;
- для проверки OTP реализована защита от перебора: после нескольких неверных попыток валидация для пользователя и операции временно блокируется;
- для генерации OTP реализован мягкий настраиваемый rate limit;
- при превышении лимита генерации сервис возвращает `429 Too Many Requests`;
- in-memory структуры используются только для ограничения генерации OTP и для временного хранения счётчиков неверных попыток валидации OTP;
- in-memory структуры очищаются по расписанию, чтобы не накапливать устаревшие записи.

Ограничение на генерацию OTP настраивается через параметры:
- `otp.generate-rate-limit.enabled`
- `otp.generate-rate-limit.max-attempts`
- `otp.generate-rate-limit.window-seconds`

Для тестового профиля ограничение генерации OTP отключено, чтобы не влиять на выполнение автоматических тестов.

---
## Обработка ошибок

В проекте разделены основные типы ошибок:
- ошибки пользовательского ввода и незаполненных данных возвращаются как `400 Bad Request`;
- ошибки авторизации возвращаются как `401 Unauthorized`;
- ошибки доступа к admin API возвращаются как `403 Forbidden`;
- отсутствие данных возвращается как `404 Not Found`;
- конфликты бизнес-логики возвращаются как `409 Conflict`;
- превышение лимита генерации OTP возвращается как `429 Too Many Requests`;
- недоступность внешних сервисов доставки возвращается как `503 Service Unavailable`.

Примеры ошибок валидации для генерации OTP:
- для `FILE` без `deliveryTarget` возвращается `400 Bad Request`;
- для `EMAIL`, `SMS`, `TELEGRAM` при передаче `deliveryTarget` возвращается `400 Bad Request`.

## Тестирование

В проекте есть:
- unit-тесты;
- controller tests;
- интеграционные API-тесты.

Проверяются:
- health endpoints;
- регистрация и логин;
- logout и инвалидирование токена;
- генерация и валидация OTP;
- ошибки валидации;
- admin endpoints;
- разграничение ролей;
- каскадное удаление OTP при удалении пользователя;
- сценарии Telegram binding;
- работа с test database;
- очистка OTP при ошибке доставки;
- защита от brute force на валидацию OTP;
- атомарный сценарий использования OTP;
- настройка ограничения генерации OTP;
- cleanup stale-записей для validation attempts и generate rate limit;
- перевод старых активных OTP в `EXPIRED` при повторной генерации для той же операции;
- конкурентная валидация OTP;
- конкурентная генерация OTP для одной и той же операции;
- controller-level проверки `429` для rate limit;
- controller-level проверки `503` для недоступности внешних каналов доставки;
- хранение пользовательских сессий в базе данных;
- cleanup истёкших и отозванных пользовательских сессий;

### Запуск тестов

```bash
./gradlew test
```

---
## Ручная проверка API

Для ручной проверки запросов используется файл `testAPI.http`.

В этом файле собраны сценарии для проверки:
- регистрации;
- логина;
- logout;
- admin endpoints;
- генерации и валидации OTP;
- Telegram binding;
- работы каналов доставки.

---
## Логирование

В приложении настроено логирование:
- HTTP-запросов;
- ошибок валидации;
- операций аутентификации;
- генерации и проверки OTP;
- административных действий;
- отправки OTP по каналам доставки;
- ошибок внешних сервисов доставки;
- очистки OTP после неудачной доставки;
- срабатывания rate limit и защиты от brute force;
- очистки устаревших записей ограничений и попыток валидации;
- очистки истёкших и отозванных пользовательских сессий;
- перевода старых OTP в `EXPIRED` при повторной генерации.

Логи выводятся:
- в консоль;
- в файл.

---

## Дополнительные замечания

- Для тестов используется отдельная база данных.
- OTP-конфигурация хранится в одной записи.
- Второй администратор зарегистрирован быть не может.
- После logout токен считается недействительным.
- При удалении пользователя его OTP-коды удаляются автоматически.
- Просроченные OTP-коды помечаются в фоне как `EXPIRED`.
- Ограничение на генерацию OTP в тестовом профиле отключено.
- Активные и отозванные пользовательские сессии хранятся в базе данных.
- In-memory структуры используются только для rate limit генерации OTP и счётчиков неверных попыток валидации.

---
## Итог

В рамках курсовой работы реализованы:
- работа с PostgreSQL 17 через JDBC;
- токенная аутентификация и авторизация с хранением пользовательских сессий в базе данных;
- разграничение ролей `ADMIN` и `USER`;
- генерация OTP по `operationId` и проверка OTP по коду;
- доставка OTP по четырём каналам: `FILE`, `EMAIL`, `SMS`, `TELEGRAM`;
- отдельный сценарий привязки Telegram для отправки кодов через бота;
- атомарная генерация OTP для одной и той же пары `userId + operationId`;
- атомарная валидация OTP, исключающая повторное успешное использование одного и того же кода;
- автоматический перевод просроченных OTP в статус `EXPIRED`;
- автоматическая очистка истёкших и отозванных пользовательских сессий;
- защита от brute force при проверке OTP;
- настраиваемый rate limit на генерацию OTP;
- удаление созданного OTP из базы при ошибке доставки;
- деактивация старых активных OTP при повторной генерации для той же операции;
- покрытие бизнес-логики, контроллеров и API автоматическими тестами.
