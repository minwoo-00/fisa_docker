## 실무 시나리오 — WAS
 
> Sping 기반의 간단한 웹 어플리케이션 서버를 예시로, 실무 관점에 맞춰서 단계별로 이미지를 개선해 나간다.
 
 
### Step 1. 최악의 초기 이미지
 
> **목표**: 최적화는 고려하지 않고 정상 실행 여부만 확인
>
> 로컬에서 미리 빌드한 `.jar` 파일을 그대로 복사해서 실행한다.
> 빌드 도구(Gradle), JDK 전체가 포함된 무거운 베이스 이미지를 사용한다.
 
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
 
> **목표**: 빌드 환경과 실행 환경을 분리
> Docker 안에서 직접 Gradle 빌드를 수행하고, 최종 이미지에는 실행에 필요한 JRE와 JAR 파일만 남긴다.
 
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
> Java 생태계에서 `alpine` 기반 JRE 이미지는 `musl libc`를 사용하기 때문에 일부 JVM 네이티브 라이브러리(JNI, 암호화 라이브러리 등)에서 호환성 문제가 발생할 수 있다.
> 또한 `alpine`에는 `bash`, `ps`, `curl` 같은 기본 명령어가 없어 컨테이너 진입 후 프로세스 확인조차 어렵다.
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
 
| 항목 | `eclipse-temurin:17-jre` | `alpine` |
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
 
> **목표**: 빌드 스테이지의 `RUN` 명령어를 `&&`로 묶어 레이어 수를 최소화
> Gradle 캐시, 임시 빌드 파일 등도 같은 레이어에서 제거한다.
 
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
 
> **문제 발생**: `src/` 코드 한 줄만 수정해도 `COPY . .` 레이어가 무효화되면서 Gradle 의존성 다운로드부터 전부 재실행되고 CI 빌드 시간이 크게 증가한다.
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
