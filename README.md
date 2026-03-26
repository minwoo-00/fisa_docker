# 🐳 Docker 이미지 최적화

---

> 💬 **"목적에 맞게 최소화할수록 더 좋은 이미지"**

---

</br>

## 📌 이미지 최적화란?

Docker 이미지 최적화란 **불필요한 파일, 레이어, 패키지를 제거해 이미지를 최대한 가볍게 만드는 작업**입니다.

---

</br>

## ❓ 왜 하는가?

| 항목 | 문제 | 해결 |
| :--- | :--- | :--- |
| **배포 속도** | 이미지가 클수록 다운로드 시간 증가 | 이미지 축소 → 배포 시간 단축 |
| **보안** | 불필요한 툴이 많을수록 해킹 공격 표면 증가 | 필요한 것만 포함 → 위협 최소화 |
| **비용** | 큰 이미지 → 저장/트래픽 비용 증가 | 이미지 축소 → 비용 절감 |

---

</br>

## ⚙️ 어떻게 하는가?

### 1️⃣ 베이스 이미지 선택

처음부터 작고 목적에 맞는 베이스 이미지를 선택하는 것이 최적화의 출발점입니다.

| 이미지 | 크기 | 특징 |
| :--- | :--- | :--- |
| ubuntu | 약 77MB | 범용적이지만 무거움 |
| debian:slim | 약 75MB | 안정적이고 ubuntu보다 가벼움 |
| alpine | 약 7MB | 매우 가벼움, 패키지 호환성 주의 |
| eclipse-temurin:17-jre | 약 200MB | Java 실행 전용, JDK보다 가벼움 |
| distroless | 약 2MB | 쉘도 없음, 보안 최강 |

---

### 2️⃣ 멀티스테이지 빌드

빌드 단계와 실행 단계를 분리하여 **빌드 도구는 최종 이미지에 포함되지 않도록** 하는 방법입니다.
컴파일러, 빌드 툴 등 실행에 불필요한 것들을 제거해 이미지 크기를 대폭 줄일 수 있습니다.
````
1단계 (빌드용)   무거운 JDK로 JAR 파일 생성
2단계 (실행용)   가벼운 JRE에 JAR 파일만 복사
결과            JDK가 최종 이미지에서 사라짐 → 400MB → 200MB
````

---

### 3️⃣ .dockerignore 사용

이미지를 빌드할 때 **불필요한 파일이 포함되지 않도록** 제외 목록을 지정하는 방법입니다.
**.git**, 로그 파일, 환경변수 파일 등을 제외해 빌드 속도와 보안을 동시에 챙길 수 있습니다.

---

### 4️⃣ 레이어 구조 최적화

Docker는 위에서 아래로 빌드하며, **변경된 레이어부터 아래를 전부 다시 빌드**합니다.
따라서 레이어 순서와 구성 방식이 이미지 크기와 빌드 속도 모두에 영향을 줍니다.

**① 순서 최적화 (빌드 속도)**
```
잘 안 바뀌는 것 (의존성, 설정) → 위에 배치 → 캐시 활용
자주 바뀌는 것 (소스코드)     → 아래 배치 → 바뀐 부분만 재빌드
```

**② 레이어 합치기 (이미지 용량)**
```
파일을 추가한 뒤 같은 레이어에서 삭제하지 않으면
이전 레이어에 이미 저장되어 있어 용량이 줄지 않습니다.
→ RUN 명령어를 &&로 이어서 하나의 레이어로 합쳐야 실제로 용량이 줄어듭니다.
```
---

### 5️⃣ BuildKit / 캐시 활용

BuildKit은 Docker의 기본 빌더로, **빌드 성능 개선과 외부 캐시 공유**를 지원합니다.
로컬, CI 서버 등 여러 환경에서 빌드 캐시를 공유해 매번 처음부터 빌드하지 않아도 됩니다.

---
## 실습 🚀

### Docker 베이스 이미지 최적화 실습

### 실습 목적
동일한 Spring Boot JAR 파일을 서로 다른 베이스 이미지로 빌드하여, 베이스 이미지 선택이 **최종 이미지 크기**와 **빌드 특성**에 어떤 영향을 미치는지 비교한다.

---

#### 1. 베이스 이미지 Pull

```bash
# 베이스 이미지 pull 예시 명령어
docker pull ubuntu
```

##### 베이스 이미지 크기 비교

| 이미지 | 디스크 크기 | 다운로드 크기 | 특징 |
|--------|-----------|-------------|------|
| alpine | 13.1MB | 3.95MB | 매우 가벼움, musl libc 기반 |
| debian:bookworm-slim | 116MB | 30.6MB | 안정적, 불필요한 패키지 제거 |
| ubuntu | 119MB | 31.7MB | 범용적이지만 무거움 |
| distroless (java17) | 315MB | 82.5MB | 쉘 없음, Java 포함, 보안 최강 |
| eclipse-temurin:17-jre | 377MB | 97.5MB | Java 실행 전용, JDK보다 가벼움 |

---

#### 2. Dockerfile 작성

> 💡 베이스 이미지 부분(`FROM`)은 공란으로 두었습니다. 원하는 베이스 이미지를 넣어서 사용하세요.

##### 기본 템플릿

```dockerfile
FROM _______________
# 필요시 JDK 설치 명령어 추가
COPY build/libs/step06_buildGradleTest-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

##### 베이스 이미지 Dockerfile

```dockerfile
# dockerfile 예시
FROM ubuntu
RUN apt-get update && apt-get install -y openjdk-17-jre-headless && rm -rf /var/lib/apt/lists/*
COPY build/libs/step06_buildGradleTest-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

---

#### 3. 이미지 빌드

```bash
# 이미지 빌드 예시 명령어
docker build -f Dockerfile.ubuntu -t myapp:ubuntu .
```

---

#### 4. 빌드 결과 비교

| 이미지 | 베이스 크기 | 최종 크기 | 증가량 | 빌드 시간 | JDK 설치 방식 |
|--------|-----------|----------|--------|-----------|--------------|
| alpine | 13.1MB | 313MB | +300MB | 45.5s | apk add (9초) |
| debian | 116MB | 527MB | +411MB | 121.7s | apt-get (73초) |
| ubuntu | 119MB | 524MB | +405MB | 130.3s | apt-get (81초) |
| distroless | 315MB | 353MB | +38MB | 19.2s | 사전 포함 |
| temurin | 377MB | 411MB | +34MB | 8.2s | 사전 포함 |

---

#### 5. 결론

> **베이스 이미지가 작다고 최종 이미지가 작은 것이 아니다.** JDK를 빌드 중에 설치하면 +300~400MB가 추가되지만, JDK가 사전 포함된 이미지(temurin, distroless)는 +34~38MB만 증가하며 빌드도 최대 16배 빠르다. 실무에서는 목적에 맞는 전용 이미지를 선택하는 것이 크기·속도·보안 모두에서 유리하다.

---

## 실무 관점에서의 최적화
 
> **"최적화가 항상 정답은 아니다."**
> 
> 극단적인 최적화는 개발·운영 생산성을 저해할 수 있습니다. 실무에서는 최적화와 운영 편의성 사이의 균형이 중요합니다.
 
### ⚠️ 고려해야 할 트레이드오프
 
#### 1. 극단적 최적화 이미지 → 디버깅 및 장애 대응 어려움
 
`alpine`, `distroless` 같은 극단적으로 최적화된 베이스 이미지는 `ping`, `ls`, `curl`, `bash` 같은 기본 명령어조차 포함되어 있지 않습니다.
 
- 운영 환경에서 장애 발생 시 컨테이너 내부 진입 자체가 불가능하거나 매우 제한적
- 개발 속도보다 디버깅 비용이 더 커지는 상황 발생
- **→ 절충안**: `alpine` 대신 `debian:slim`(`python:3.x-slim`, `node:lts-slim`) 계열 사용
 
#### 2. 라이브러리 호환성 문제
 
`alpine`은 표준 `glibc` 대신 `musl libc`를 사용하기 때문에, 일부 바이너리 의존 패키지(특히 Python 네이티브 확장, gRPC 등)에서 호환성 문제가 발생합니다.
 
- 빌드는 성공했는데 런타임에 segfault나 import error 발생 가능
- **→ 절충안**: 호환성이 검증된 `slim` 이미지 사용
 
#### 3. 사내 보안 규정 — 표준 베이스 이미지 사용 의무
 
많은 기업에서는 보안 정책상 컨테이너에 **모니터링 에이전트, 취약점 스캐너, 로그 수집 에이전트** 탑재를 필수로 요구합니다.
 
```
[일반적인 사내 표준 이미지 관리 흐름]
 
보안팀이 공식 이미지에 필수 에이전트 및 취약점 조치 적용
          ↓
사내 프라이빗 레지스트리에 표준 베이스 이미지 배포
          ↓
각 개발팀은 이 표준 이미지를 FROM 절에 사용하여 서비스 이미지 빌드
```
 
- 퍼블릭 레지스트리의 `alpine`을 직접 사용하면 보안 심사 통과가 어려운 경우 있음
- **→ 절충안**: 보안팀이 관리하는 사내 표준 베이스 이미지 기반으로 구성
 
#### 4. 레이어 수 무조건 줄이기 → 빌드 캐시 무력화
 
`RUN` 명령어를 `&&`로 하나로 묶으면 레이어 수는 줄지만, **코드 한 줄만 바꿔도 해당 레이어 전체를 재빌드**해야 합니다.
 
```dockerfile
# 레이어 수는 적지만 캐시 활용 불가
RUN apt-get update && apt-get install -y curl vim && \
    npm install && \
    npm run build
 
# 변경 빈도에 따라 레이어를 분리하여 캐시 최대 활용
RUN apt-get update && apt-get install -y curl vim   # 거의 안 바뀜
RUN npm install                                      # package.json 바뀔 때만
RUN npm run build                                    # 코드 바뀔 때마다
```
 
- **→ 절충안**: 레이어를 무조건 줄이는 것이 아닌, **변경 빈도 기준으로 레이어를 분리**하여 캐시 히트율 극대화
 
---
 
## 3. 실습 시나리오 — WAS
 
> Sping 기반의 간단한 웹 어플리케이션 서버를 예시로, 실무 관점에 맞춰서 단계별로 이미지를 개선해 나갑니다.
 
 
### Step 1. 최악의 초기 이미지
 
> **목표**: 최적화는 고려하지 않고 정상 실행 여부만 확인합니다.
>
> 로컬에서 미리 빌드한 `.jar` 파일을 그대로 복사해서 실행합니다.
> 빌드 도구(Gradle), JDK 전체가 포함된 무거운 베이스 이미지를 사용합니다.
 
```dockerfile
FROM eclipse-temurin:17-jdk
 
WORKDIR /app
 
COPY build/libs/step06_buildGradleTest-0.0.1-SNAPSHOT.jar app.jar
 
EXPOSE 8080
 
ENTRYPOINT ["java", "-jar", "app.jar"]
```
 
**문제점**
 
- 런타임에 필요 없는 JDK 전체(컴파일러, 개발 도구 등)가 이미지에 포함됨
- 빌드를 로컬 환경에 의존 → 다른 환경에서 실행 시 문제 발생 가능
- 빌드 환경(Java 버전, Gradle 버전)이 달라지면 이미지 내용도 달라짐
 
**결과**
 
| 항목 | 결과 |
|------|------|
| 빌드 시간 | 5.7s |
| 이미지 크기 | 674MB |
 
<img width="1211" height="593" alt="image" src="https://github.com/user-attachments/assets/c819ff21-458c-4ba3-9172-b6cd877b9a29" />
<img width="731" height="224" alt="image" src="https://github.com/user-attachments/assets/5a7eafdf-b96d-4804-93ce-ec2c3b1212b4" />


 
---
 
### Step 2. 멀티 스테이지 빌드 적용
 
> **목표**: 빌드 환경과 실행 환경을 분리합니다.
> Docker 안에서 직접 Gradle 빌드를 수행하고, 최종 이미지에는 실행에 필요한 JRE와 JAR 파일만 남깁니다.
 
```dockerfile
# ---- Build Stage ----
FROM gradle:8.7-jdk17 AS builder
 
WORKDIR /build
 
COPY . .
 
RUN chmod +x gradlew && ./gradlew clean bootJar
 
# ---- Production Stage ----
FROM eclipse-temurin:17-jre
 
WORKDIR /app
 
COPY --from=builder /build/build/libs/step06_buildGradleTest-0.0.1-SNAPSHOT.jar app.jar
 
EXPOSE 8080
 
ENTRYPOINT ["java", "-jar", "app.jar"]
```
 
**개선 포인트**
 
- 빌드 스테이지(`gradle:8.7-jdk17`)에서 소스 빌드 → `.jar` 생성
- 실행 스테이지에는 `eclipse-temurin:17-jre`(JRE만 포함)만 사용 → JDK 제거로 이미지 크기 감소
- 로컬 환경과 무관하게 항상 동일한 환경에서 빌드 보장
 
**결과**
 
| 항목 | 결과 |
|------|------|
| 빌드 시간 | 217.6s |
| 이미지 크기 | 411MB |
 
<img width="1200" height="310" alt="image" src="https://github.com/user-attachments/assets/88a6acd0-4eb3-48a0-a563-8d08afa0388b" />
<img width="702" height="219" alt="image" src="https://github.com/user-attachments/assets/6e1508cc-7a65-4e16-8d6e-6de2316f1a21" />

 
---
 
### Step 3. [실무 관점] Alpine → Slim(JRE) 이미지 유지
 
> **Alpine으로 더 줄이면 안 될까?**
>
> Java 생태계에서 `alpine` 기반 JRE 이미지는 `musl libc`를 사용하기 때문에 일부 JVM 네이티브 라이브러리(JNI, 암호화 라이브러리 등)에서 호환성 문제가 발생할 수 있습니다.
> 또한 `alpine`에는 `bash`, `ps`, `curl` 같은 기본 명령어가 없어 컨테이너 진입 후 프로세스 확인조차 어렵습니다.
>
> **→ Debian 기반의 `eclipse-temurin:17-jre` 유지. 크기보다 안정성과 디버깅 가능성 우선.**
 
```dockerfile
# ---- Build Stage ----
FROM gradle:8.7-jdk17 AS builder
 
WORKDIR /build
 
COPY . .
 
RUN chmod +x gradlew && ./gradlew clean bootJar
 
# ---- Production Stage ----
# alpine 대신 Debian 기반 JRE 유지
#    → bash, curl 등 기본 명령어 사용 가능
#    → glibc 기반으로 JVM 라이브러리 호환성 확보
FROM alpine
 
WORKDIR /app
 
COPY --from=builder /build/build/libs/step06_buildGradleTest-0.0.1-SNAPSHOT.jar app.jar
 
EXPOSE 8080
 
ENTRYPOINT ["java", "-jar", "app.jar"]
```
 
**비교**
 
| 항목 | `eclipse-temurin:17-jre` (Debian) | `eclipse-temurin:17-alpine` |
|------|:---------------------------------:|:---------------------------:|
| 이미지 크기 | 다소 큼 | 작음 |
| 기본 명령어 (`bash`, `curl`) | ✅ 있음 | ❌ 없음 |
| JVM 라이브러리 호환성 | ✅ 안정적 | ⚠️ 일부 문제 가능 |
| 운영 환경 디버깅 | ✅ 용이 | ❌ 어려움 |
 
**결과**
 
| 항목 | 결과 |
|------|------|
| 빌드 시간 | 223.4s |
| 이미지 크기 | 50.7MB |

<img width="1207" height="352" alt="image" src="https://github.com/user-attachments/assets/64c9a12a-13f1-4398-86c8-c7a6cc42b99a" />
<img width="720" height="226" alt="image" src="https://github.com/user-attachments/assets/acbdcc87-e026-49b7-9c91-f2361f7f6db4" />

 
---
 
### Step 4. 레이어 수 최적화 (`&&`로 통합)
 
> **목표**: 빌드 스테이지의 `RUN` 명령어를 `&&`로 묶어 레이어 수를 최소화합니다.
> Gradle 캐시, 임시 빌드 파일 등도 같은 레이어에서 제거합니다.
 
```dockerfile
# ---- Build Stage ----
FROM gradle:8.7-jdk17 AS builder
 
WORKDIR /build
 
COPY . .
 
# 권한 부여 + 빌드 + Gradle 캐시 삭제를 하나의 레이어로 처리
RUN chmod +x gradlew \
    && ./gradlew clean bootJar \
    && rm -rf ~/.gradle/caches
 
# ---- Production Stage ----
FROM eclipse-temurin:17-jre
 
WORKDIR /app
 
COPY --from=builder /build/build/libs/step06_buildGradleTest-0.0.1-SNAPSHOT.jar app.jar
 
EXPOSE 8080
 
ENTRYPOINT ["java", "-jar", "app.jar"]
```
 
**개선 포인트**
 
- `chmod`, `gradlew`, `rm -rf` 를 하나의 `RUN` 레이어로 처리 → Gradle 캐시가 이미지 레이어에 남지 않음
- 레이어 수 감소로 이미지 메타데이터 경량화
- 멀티 스테이지이므로 빌드 스테이지의 레이어는 최종 이미지에 포함되지 않지만, 빌드 캐시 관점에서 명시적으로 정리하는 습관이 중요
 
**결과**
 
| 항목 | 결과 |
|------|------|
| 빌드 시간 | 215s |
| 이미지 크기 | 411MB |
 
<img width="1201" height="240" alt="image" src="https://github.com/user-attachments/assets/cccefd84-ee53-4778-8f67-e9e4bc1d43de" />
<img width="694" height="261" alt="image" src="https://github.com/user-attachments/assets/c6515a8b-5dba-48f0-9ea3-ba3b42fae41e" />

 
---
 
### Step 5. [실무 관점] 빌드 캐시 최대 활용을 위한 레이어 순서 최적화
 
> **문제 발생**: `src/` 코드 한 줄만 수정해도 `COPY . .` 레이어가 무효화되면서 Gradle 의존성 다운로드부터 전부 재실행됩니다. CI 빌드 시간이 크게 증가합니다.
>
> **→ 의존성 다운로드와 소스 빌드를 분리하여 캐시 히트율 극대화**
 
```dockerfile
# ---- Build Stage ----
FROM gradle:8.7-jdk17 AS builder
 
WORKDIR /build
 
# 1단계: 의존성 파일만 먼저 복사 (거의 안 바뀜)
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle
 
# 2단계: 의존성만 미리 다운로드 → 소스 변경 시 이 레이어 캐시 재사용
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon
 
# 3단계: 소스 복사는 마지막에 (자주 바뀌므로)
COPY src ./src
 
RUN ./gradlew clean bootJar --no-daemon
 
# ---- Production Stage ----
FROM eclipse-temurin:17-jre
 
WORKDIR /app
 
COPY --from=builder /build/build/libs/step06_buildGradleTest-0.0.1-SNAPSHOT.jar app.jar
 
EXPOSE 8080
 
ENTRYPOINT ["java", "-jar", "app.jar"]
```
 
**캐시 전략 원칙**
 
```
변경 빈도: 낮음 ──────────────────────────────────────────── 높음
베이스 이미지 → 빌드 도구 설정 → 의존성 다운로드 → 소스 코드 빌드
   (FROM)      (build.gradle)   (dependencies)      (COPY src/)
```
 
**개선 포인트**
 
- `src/` 코드만 변경 시 → `dependencies` 레이어 캐시 재사용, Gradle 의존성 다운로드 생략
- `build.gradle`이 변경될 때만 → 의존성 재다운로드
- CI/CD 환경에서 반복 빌드 시간 대폭 단축
 
**결과**
 
| 항목 | 결과 |
|------|------|
| 첫 빌드 시간 | 282.6s |
| 소스 변경 후 재빌드 시간 | 6.0s |
| 이미지 크기 | 411MB |
 
<img width="1205" height="289" alt="image" src="https://github.com/user-attachments/assets/1948a637-01a7-4630-8c5b-f1f9959d9a05" />
<img width="1204" height="287" alt="image" src="https://github.com/user-attachments/assets/269275f5-25f6-451c-9bdb-bc34be40eaf5" />

<img width="698" height="262" alt="image" src="https://github.com/user-attachments/assets/1ae04f1e-651e-4873-99a7-780c265fbf48" />


 

---
 
### 📊 단계별 결과 비교
 
| 단계 | 핵심 변경 | 이미지 크기 | 빌드 캐시 | 디버깅 | 비고 |
|------|-----------|:-----------:|:---------:|:------:|------|
| Step 1 | 초기 버전 (JDK + 로컬 빌드) | | ❌ | ✅ | 최악의 상태 |
| Step 2 | 멀티 스테이지 빌드 (JDK→JRE) | | 보통 | ✅ | 핵심 최적화 |
| Step 3 | Alpine 미적용, JRE(Debian) 유지 | | 보통 | ✅ | 실무 절충안 |
| Step 4 | RUN `&&` 레이어 통합 + 캐시 제거 | | 보통 | ✅ | 레이어 정리 |
| Step 5 | 의존성/소스 레이어 분리 | | ✅ | ✅ | CI 속도 향상 |
 
 
---

