# ⚙️ AI Interview Preparation Platform — Backend

<div align="center">

![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.x-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![Spring AI](https://img.shields.io/badge/Spring_AI-1.x-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![Java](https://img.shields.io/badge/Java-17-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=for-the-badge&logo=mysql&logoColor=white)
![JWT](https://img.shields.io/badge/JWT-Auth-000000?style=for-the-badge&logo=jsonwebtokens&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?style=for-the-badge&logo=docker&logoColor=white)

REST API backend for the AI Interview Preparation Platform — handling authentication, interview management, AI evaluation, technical screening, code analysis, and admin operations.

[Frontend Repo](https://github.com/ArunDev-07/AI-InterviewSystem-Frontend) 

</div>

---

## ✨ Features

### 🔐 Authentication & Security
- JWT token generation and validation via `JwtFilter`
- BCrypt password hashing
- Role-based authorization (`ROLE_USER`, `ROLE_ADMIN`)
- Token expiry handling with auto-invalidation
- Admin-only endpoint protection

### 🤖 4-Round Interview System
- Full interview lifecycle: create → answer → complete → result
- Round-wise scoring: Aptitude, Communication, DSA, HR
- AI feedback stored per round using Groq API
- Retake support and result history

### 🎙️ Voice Technical Interview
- PDF resume upload and text extraction
- AI-generated personalized technical questions from resume content
- Per-answer scoring (0–10) with feedback and optimal answer
- Follow-up question generation
- Final technical report generation

### 💻 Code Analysis
- AI-powered code review: correctness, time complexity, space complexity
- Mistake detection and optimization suggestions
- Submission history stored in MySQL

### 👨‍💼 Admin APIs
- Dashboard stats: total users, submissions, average scores
- Full user list with roles
- Paginated results with delete
- Candidate ranking by score
- Top 5 candidates endpoint

---

## 🛠️ Tech Stack

| Category | Technology |
|----------|------------|
| Framework | Spring Boot 3.x |
| Language | Java 17 |
| Database | MySQL 8.0 |
| ORM | Spring Data JPA + Hibernate |
| Security | Spring Security + JWT |
| AI Integration | Groq API (REST) / Spring AI |
| PDF Parsing | Apache PDFBox |
| Build Tool | Maven |
| Container | Docker |
| API Testing | Postman |

---

## 🚀 Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+
- MySQL 8.0+
- Groq API key ([get one free](https://console.groq.com))

### 1. Clone the Repository

```bash
git clone https://github.com/ArunDev-07/AI-InterviewSystem.git
cd backend
```

### 2. Configure MySQL

Create a database:

```sql
CREATE DATABASE ai_interview_db;
```

### 3. Configure `application.properties`

```properties
# Server
server.port=8080

# Database
spring.datasource.url=jdbc:mysql://localhost:3306/ai_interview_db
spring.datasource.username=your_mysql_username
spring.datasource.password=your_mysql_password
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false

# JWT
jwt.secret=your_jwt_secret_key_min_32_characters
jwt.expiration=86400000

# Groq API
groq.api.key=your_groq_api_key
groq.api.url=https://api.groq.com/openai/v1/chat/completions
groq.model=llama-3.3-70b-versatile
```

### 4. Run the Application

```bash
mvn spring-boot:run
```

API runs at → `http://localhost:8080`

### 5. Run with Docker

```bash
# Build image
docker build -t ai-interview-backend .

# Run with Docker Compose
docker-compose up --build
```

---

## 📁 Project Structure

```
backend/
├── src/
│   └── main/
│       ├── java/com/aiinterview/
│       │   ├── controller/
│       │   │   ├── AuthController.java
│       │   │   ├── InterviewController.java
│       │   │   ├── TechnicalInterviewController.java
│       │   │   ├── CodeAnalysisController.java
│       │   │   ├── ChatController.java
│       │   │   └── AdminDashboardController.java
│       │   ├── service/
│       │   │   ├── UserService.java
│       │   │   ├── InterviewService.java
│       │   │   ├── TechnicalInterviewService.java
│       │   │   ├── ChatService.java
│       │   │   ├── CodeAnalysisService.java
│       │   │   └── AdminDashboardService.java
│       │   ├── repository/
│       │   │   ├── UserRepository.java
│       │   │   ├── InterviewRepository.java
│       │   │   ├── TechnicalInterviewRepository.java
│       │   │   └── CodeAnalysisRepository.java
│       │   ├── model/
│       │   │   ├── User.java
│       │   │   ├── Interview.java
│       │   │   ├── InterviewRound.java
│       │   │   ├── TechnicalInterview.java
│       │   │   ├── TechnicalInterviewAnswer.java
│       │   │   └── CodeAnalysisResult.java
│       │   ├── security/
│       │   │   ├── JwtFilter.java
│       │   │   ├── JwtUtil.java
│       │   │   └── SecurityConfig.java
│       │   ├── dto/
│       │   └── AiInterviewApplication.java
│       └── resources/
│           └── application.properties
├── Dockerfile
├── docker-compose.yml
└── pom.xml
```

---

## 📡 API Reference

### Public Endpoints (No Auth Required)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/public/register` | Register a new user |
| `POST` | `/public/login` | Login and get JWT token |

**Login Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "username": "arun",
  "role": "USER"
}
```

---

### Interview Endpoints (USER — Bearer Token Required)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/interview/start` | Start a new interview session |
| `POST` | `/api/interview/submit-round` | Submit a round's answers |
| `GET` | `/api/interview/{id}/result` | Get full interview result |
| `GET` | `/api/interview/my` | Get current user's interviews |

---

### Technical Interview Endpoints (USER)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/technical-interview/upload-resume` | Upload PDF resume |
| `POST` | `/api/technical-interview/start` | Start technical interview |
| `POST` | `/api/technical-interview/answer` | Submit voice answer |
| `POST` | `/api/technical-interview/next-question` | Get next AI question |
| `POST` | `/api/technical-interview/{id}/complete` | Complete the interview |
| `GET` | `/api/technical-interview/{id}/result` | Get technical result |
| `GET` | `/api/technical-interview/my` | Get user's tech interviews |

---

### Code Analysis Endpoints (USER)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/code/analyze` | Submit code for AI analysis |
| `GET` | `/api/code/submissions` | Get user submission history |

---

### Admin Endpoints (ADMIN — Bearer Token Required)

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/admin/dashboard` | Platform stats overview |
| `GET` | `/api/admin/users` | All registered users |
| `GET` | `/api/admin/results` | All code submissions (paginated) |
| `DELETE` | `/api/admin/results/{id}` | Delete a submission |
| `GET` | `/api/admin/candidates/rankings` | Score-based ranking |
| `GET` | `/api/admin/candidates/top5` | Top 5 candidates |

---

## 🗄️ Database Schema

```
users
├── id (PK)
├── username
├── password (BCrypt)
└── role

interviews
├── id (PK)
├── user_id (FK → users)
├── total_score
├── percentage
├── status
└── created_at

interview_rounds
├── id (PK)
├── interview_id (FK → interviews)
├── round_type (APTITUDE / COMMUNICATION / DSA / HR)
├── score
└── ai_feedback

technical_interviews
├── id (PK)
├── user_id (FK → users)
├── resume_text
├── status
└── created_at

technical_interview_answers
├── id (PK)
├── technical_interview_id (FK)
├── question
├── user_answer
├── score
├── feedback
└── optimal_answer

code_analysis_results
├── id (PK)
├── user_id (FK → users)
├── problem_name
├── code
├── score
├── time_complexity
├── space_complexity
└── submitted_at
```

---

## 🔒 Security Flow

```
POST /public/login
        ↓
Spring Security authenticates credentials
        ↓
JwtUtil generates signed token
        ↓
Token returned to frontend
        ↓
Frontend stores in localStorage
        ↓
Every request → Authorization: Bearer <token>
        ↓
JwtFilter validates token on each request
        ↓
SecurityContext set → Controller accessed
```

---

## 🐳 Docker Setup

**Dockerfile:**
```dockerfile
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**docker-compose.yml:**
```yaml
version: '3.8'
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_DATABASE: ai_interview_db
      MYSQL_ROOT_PASSWORD: root
    ports:
      - "3306:3306"

  backend:
    build: .
    ports:
      - "8080:8080"
    depends_on:
      - mysql
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/ai_interview_db
      SPRING_DATASOURCE_USERNAME: root
      SPRING_DATASOURCE_PASSWORD: root
      JWT_SECRET: your_jwt_secret_here
      GROQ_API_KEY: your_groq_api_key_here
```

```bash
docker-compose up --build
```

---

## 🤝 Contributing

```bash
git checkout -b feature/your-feature
git commit -m "feat: describe your change"
git push origin feature/your-feature
# Open a Pull Request
```

---

## 📄 License

This project is licensed under the MIT License.

---

<div align="center">
Built with ❤️ by <a href="https://github.com/ArunDev-07">Arun G</a> · <a href="https://arun-g.vercel.app">Portfolio</a> · <a href="https://www.linkedin.com/in/arun-g-dev/">LinkedIn</a>
</div>
