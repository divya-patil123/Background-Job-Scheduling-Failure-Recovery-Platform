# JobFlow – Background Job Scheduling & Failure Recovery Platform

A Java-based job scheduling platform built with Spring Boot and MySQL to schedule background tasks, track execution history, automatically retry failed jobs, and alert administrators when repeated failures occur.

## 🚀 Key Features

- **Job Scheduling:** Schedule background tasks using configurable schedules.
- **Execution Tracking:** Maintain job execution history and status.
- **Automatic Retry:** Retry failed jobs with configurable retry attempts.
- **Failure Recovery:** Handle interrupted jobs and recover eligible tasks.
- **Admin Alerts:** Record alerts when jobs repeatedly fail.
- **REST APIs:** Manage scheduled jobs and monitor their execution.
- **Multithreading:** Execute background tasks using a managed thread pool.
- **Database Integration:** Persist jobs, execution attempts, and alerts in MySQL.
- **Unit Testing:** Test application functionality using JUnit and Mockito.

## 🛠️ Tech Stack

| Technology | Purpose |
|---|---|
| Java 17 | Core application development |
| Spring Boot | Backend framework and REST APIs |
| Spring Scheduler | Background job scheduling |
| MySQL | Persistent data storage |
| Multithreading | Concurrent task execution |
| Maven | Build and dependency management |
| JUnit & Mockito | Testing |
| React & TypeScript | Frontend dashboard |

## 🏗️ System Overview

1. Users schedule background jobs through the application.
2. The scheduler identifies jobs that are due for execution.
3. A managed worker pool executes the scheduled tasks.
4. The application records execution status and history.
5. Failed jobs are retried according to configured retry limits.
6. Repeated failures generate alerts for administrators.

## 📂 Project Structure

```text
JobFlow/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/jobflow/
│   │   └── resources/
│   └── test/
├── frontend/
├── docs/
│   ├── ARCHITECTURE.md
│   ├── DATABASE_DESIGN.md
│   ├── API_DOCUMENTATION.md
│   └── PROJECT_LEARNINGS.md
├── pom.xml
└── README.md
```

## ⚙️ Prerequisites

Install the following before running the application:

- JDK 17 or later
- Maven 3.6.3 or later
- MySQL 8
- Node.js and npm (for the frontend)

## ▶️ Getting Started

### 1. Clone the Repository

```bash

git clone https://github.com/divya-patil123/JobFlow.git
cd JobFlow

```



### 2. Configure the Database

Create a MySQL database for the application and configure the database connection using environment variables:

```text
DB_URL=jdbc:mysql://localhost:3306/jobflow
DB_USERNAME=your_username
DB_PASSWORD=your_password
```

Use the database scripts and configuration included in the project.

### 3. Run the Backend

Build and test the application:

```bash
mvn clean package
```

Run the application using your configured Spring profile:

```bash
mvn spring-boot:run
```

Ensure your database settings and required environment variables are configured before starting the application.

### 4. Run Tests

```bash
mvn test
```

## 📖 Documentation

Additional project documentation is available in the `docs/` directory:

- [Architecture](docs/ARCHITECTURE.md)
- [Database Design](docs/DATABASE_DESIGN.md)
- [API Documentation](docs/API_DOCUMENTATION.md)
- [Project Learnings](docs/PROJECT_LEARNINGS.md)

## 💡 What I Learned

- Designing REST APIs using Spring Boot.
- Scheduling and executing background tasks.
- Managing concurrent execution with multithreading.
- Implementing retry logic and failure recovery.
- Persisting execution history and status in MySQL.
- Writing automated tests using JUnit and Mockito.
- Structuring a backend application for maintainability.

## 🔮 Future Enhancements

- Add real-time execution monitoring.
- Introduce role-based access control.
- Add email or messaging notifications for administrators.
- Support distributed scheduling across multiple application instances.


---

*JobFlow demonstrates practical Java backend development through job scheduling, concurrent execution, failure handling, and automated testing.*
