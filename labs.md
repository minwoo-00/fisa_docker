## 실습 🚀

### 1️⃣ Docker 베이스 이미지 최적화 실습

#### 실습 목적
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

> 💡 베이스 이미지 부분(`FROM`)은 공란으로 두었다. 원하는 베이스 이미지를 넣어서 사용

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

### 2️⃣ 멀티 스테이지 빌드 실습

#### 실험 목적

멀티 스테이지 빌드는 하나의 Dockerfile 안에서 여러 **FROM**을 사용해 **빌드 환경과 실행 환경을 분리**하는 방식이다.  
이를 통해 최종 이미지에는 실행에 필요한 산출물만 남기고, 빌드 도구와 중간 산출물은 제외할 수 있다. Docker 공식 문서도 멀티 스테이지 빌드를 통해 최종 이미지에서 “원하지 않는 것들을 남기지 않을 수 있다”고 설명한다.

이번 실험에서는 다음 두 가지를 비교하였다.

- single: 단일 스테이지 빌드
- multi: 멀티 스테이지 빌드

---

#### 실험 결과

#### single

<img width="668" height="477" alt="스크린샷 2026-03-26 151443" src="https://github.com/user-attachments/assets/ade34bc2-e56d-4dfa-98ce-7d968ad1f16b" />

- exporting: **7.1s**
- real: **36.544s**
- CONTENT SIZE: **594MB**

#### multi

<img width="654" height="405" alt="스크린샷 2026-03-26 151521" src="https://github.com/user-attachments/assets/38880b88-1550-4519-9c78-fa728d21f772" />

- exporting: **0.7s**
- real: **34.061s**
- CONTENT SIZE: **112MB**

---

#### 결과 분석
<img width="642" height="387" alt="image" src="https://github.com/user-attachments/assets/91911ebf-2dab-494f-b180-4c1535fbc7bb" />

멀티 스테이지 적용 후 전체 빌드 시간은 **36.544s → 34.061s**로 소폭 감소했다.  
하지만 가장 큰 차이는 **최종 이미지 크기와 exporting 시간**에서 나타났다.

- 이미지 크기: **594MB → 112MB**
- exporting 시간: **7.1s → 0.7s**

이 결과는 멀티 스테이지 빌드의 핵심 효과를 잘 보여준다.  
단일 스테이지에서는 Gradle, JDK, 빌드 결과물, 작업 디렉토리 상태 등이 최종 이미지에 함께 남을 수 있지만, 멀티 스테이지에서는 **최종 단계에서 JRE와 JAR만 남도록 구성**할 수 있다. Docker는 이 방식을 통해 최종 이미지 크기와 공격면을 줄일 수 있다고 설명한다.

특히 exporting 시간이 크게 감소한 이유는 최종 이미지에 포함되는 레이어와 데이터가 줄었기 때문이다.  
즉, 멀티 스테이지 빌드는 단순히 “빌드가 되는 Dockerfile”이 아니라, **운영 배포용 런타임 이미지에 맞게 결과물을 정제하는 방식**이라고 볼 수 있다.

---

### 3️⃣ .dockerignore 적용 비교

#### 실습 목적

**.dockerignore**는 build context에서 불필요한 파일을 제외하기 위한 설정 파일이다.  
Docker는 **.dockerignore**를 통해 필요 없는 파일을 빌더로 전송하지 않도록 하여, build context를 줄이고 빌드 효율을 높일 수 있다고 설명한다.

이번 실험에서는 멀티 스테이지 Dockerfile은 동일하게 유지하고,  
**.dockerignore** 사용 여부만 바꿔서 비교하였다.

---

#### 실습 결과

##### **.dockerignore** 미사용

위 Multi Dockerfile 이미지 사용

<img width="340" height="96" alt="스크린샷 2026-03-26 154815" src="https://github.com/user-attachments/assets/398534b5-5720-4835-9c7c-043d91f8b3b6" />

- build context: **0.1s**
- real: **34.061s**

##### **.dockerignore** 사용

<img width="348" height="74" alt="스크린샷 2026-03-26 154637" src="https://github.com/user-attachments/assets/f893e93d-84c2-4dfc-923c-c4d62995034c" />

<img width="608" height="75" alt="스크린샷 2026-03-26 154654" src="https://github.com/user-attachments/assets/8476df5f-55d1-417a-abfa-2a74ea1cfec2" />

- build context: **0.0s**
- real: **30.715s**

##### 공통 사항

<img width="660" height="54" alt="image" src="https://github.com/user-attachments/assets/3a94420e-2645-464b-9ac7-466157b995fa" />

- CONTENT SIZE: **동일**

---

#### 결과 분석

**.dockerignore** 적용 후 전체 빌드 시간은 **34.061s → 30.715s**로 감소했다.  
즉, **.dockerignore**는 실제로 빌드 시간을 줄이는 데 기여했다.

하지만 최종 이미지 크기는 **변하지 않았다.**

이 결과는 정상이다.  
왜냐하면 **.dockerignore**의 역할은 **최종 이미지 크기를 줄이는 것**이 아니라, **빌드 시 전달되는 입력 파일(build context)을 줄이는 것**이기 때문이다. Docker 공식 문서도 **.dockerignore**의 핵심 목적을 불필요한 파일을 빌드 컨텍스트에서 제외하는 것으로 설명한다.

즉 이번 실험에서 제외한 항목들(`build/`, `.gradle/`, `.git/`, 로그, 문서 파일 등)은:

- 빌드 입력에는 포함될 수 있었지만
- 최종 멀티 스테이지 이미지에는 어차피 포함되지 않거나
- 최종 런타임 이미지에서 의미가 없는 파일들이다

따라서 **.dockerignore** 적용 전후에 **최종 CONTENT SIZE가 동일한 것은 자연스러운 결과**다.

이번 결과는 다음처럼 해석할 수 있다.

- **.dockerignore** 미사용: 불필요한 파일까지 빌더로 전송
- **.dockerignore** 사용: 필요한 파일만 빌더로 전송
- 결과: 빌드 입력이 줄어 **build context 처리와 전체 빌드 시간 개선**
- 하지만 최종 이미지 구성은 동일하므로 **이미지 크기는 동일**

---

### 4️⃣ 레이어 구조 최적화 실습

#### 실습 목적
Docker는 위에서 아래로 빌드하며, 변경된 레이어부터 아래를 전부 다시 빌드한다. 레이어 **순서**가 빌드 속도에, 레이어 **합치기**가 이미지 크기에 어떤 영향을 주는지 확인한다.

#### 1. 순서 최적화 (빌드 속도)

```
잘 안 바뀌는 것 (의존성, 설정) → 위에 배치 → 캐시 활용
자주 바뀌는 것 (소스코드)     → 아래 배치 → 바뀐 부분만 재빌드
```

##### Dockerfile 비교

**나쁜 예: JAR(자주 바뀜)가 위, 의존성 설치(무거움)가 아래**
```dockerfile
FROM ubuntu
COPY build/libs/step06_buildGradleTest-0.0.1-SNAPSHOT.jar app.jar
RUN apt-get update && apt-get install -y openjdk-17-jre-headless && rm -rf /var/lib/apt/lists/*
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

**좋은 예: 의존성 설치(무거움)가 위, JAR(자주 바뀜)가 아래**
```dockerfile
FROM ubuntu
RUN apt-get update && apt-get install -y openjdk-17-jre-headless && rm -rf /var/lib/apt/lists/*
COPY build/libs/step06_buildGradleTest-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

##### 재빌드 결과 (JAR 변경 후)

| Dockerfile | 재빌드 시간 | apt-get 캐시 | 원인 |
|-----------|-----------|-------------|------|
| 나쁜 예 (JAR 위) | **2분 27초** | 다시 실행 | JAR가 바뀌면서 아래 apt-get도 재실행 |
| 좋은 예 (JAR 아래) | **8.7초** | CACHED ✅ | apt-get은 캐시, JAR만 다시 COPY |

> 순서만 바꿔도 재빌드 시간이 **약 17배** 차이난다.

---

#### 2. 레이어 합치기 (이미지 크기 vs 캐시 효율)

```
파일을 추가한 뒤 같은 레이어에서 삭제하지 않으면
이전 레이어에 이미 저장되어 있어 용량이 줄지 않는다.
→ RUN 명령어를 &&로 이어서 하나의 레이어로 합쳐야 실제로 용량이 줄어든다.
```

##### Dockerfile 비교

**RUN 분리 (split)**
```dockerfile
FROM ubuntu
RUN apt-get update
RUN apt-get install -y openjdk-17-jre-headless
RUN rm -rf /var/lib/apt/lists/*
COPY build/libs/step06_buildGradleTest-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

**RUN 합침 (merged)**
```dockerfile
FROM ubuntu
RUN apt-get update && apt-get install -y openjdk-17-jre-headless && rm -rf /var/lib/apt/lists/*
COPY build/libs/step06_buildGradleTest-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

##### 크기 비교

| Dockerfile | 이미지 크기 | 차이 |
|-----------|-----------|------|
| split (RUN 분리) | 627MB | +103MB |
| merged (RUN 합침) | 524MB | 기준 |

> RUN을 분리하면 `rm -rf`가 별도 레이어라 이전 레이어에 apt 캐시가 남아서 103MB 더 크다.

##### 캐시 효율 비교 (curl 패키지 추가 후 재빌드)

| Dockerfile | 재빌드 시간 | 캐시 상태 |
|-----------|-----------|----------|
| split-v2 (RUN 분리) | **24.9초** | apt-get update, openjdk CACHED ✅ → curl만 설치 |
| merged-v2 (RUN 합침) | **2분 24초** | RUN 한 줄이 바뀌어 전체 다시 실행 ❌ |

> 분리하면 재빌드가 **약 6배 빠르다.**

</br>

### 📌 실무 관점에서의 최적화
 
> **"최적화가 항상 정답은 아니다."**
> 
> 극단적인 최적화는 개발·운영 생산성을 저해할 수 있다. 실무에서는 최적화와 운영 편의성 사이의 균형이 중요하다.
 
#### ⚠️ 고려해야 할 트레이드오프
 
#### 1. 극단적 최적화 이미지 → 디버깅 및 장애 대응 어려움
 
`alpine`, `distroless` 같은 극단적으로 최적화된 베이스 이미지는 `ping`, `ls`, `curl`, `bash` 같은 기본 명령어조차 포함되어 있지 않다.
 
- 운영 환경에서 장애 발생 시 컨테이너 내부 진입 자체가 불가능하거나 매우 제한적
- 개발 속도보다 디버깅 비용이 더 커지는 상황 발생
- **→ 절충안**: `alpine` 대신 `debian:slim`(`python:3.x-slim`, `node:lts-slim`) 계열 사용
 
#### 2. 라이브러리 호환성 문제
 
`alpine`은 표준 `glibc` 대신 `musl libc`를 사용하기 때문에 일부 바이너리 의존 패키지(특히 Python 네이티브 확장, gRPC 등)에서 호환성 문제가 발생한다.
 
- 빌드는 성공했는데 런타임에 segfault나 import error 발생 가능
- **→ 절충안**: 호환성이 검증된 `slim` 이미지 사용
 
#### 3. 사내 보안 규정 — 표준 베이스 이미지 사용 의무
 
많은 기업에서는 보안 정책상 컨테이너에 **모니터링 에이전트, 취약점 스캐너, 로그 수집 에이전트** 탑재를 필수로 요구한다.
 
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
 
`RUN` 명령어를 `&&`로 하나로 묶으면 레이어 수는 줄지만, **코드 한 줄만 바꿔도 해당 레이어 전체를 재빌드**해야 한다.
 
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

### 5️⃣ BuildKit / 캐시 활용

#### 실습 목적

BuildKit은 Docker의 기본 빌더이며, 기존 빌더보다 더 나은 성능과 다양한 빌드 기능을 제공한다. Docker 문서에 따르면 BuildKit은 Docker Desktop과 Docker Engine 23.0+에서 기본 빌더다. 

이번 실험에서는 BuildKit의 **RUN --mount=type=cache** 기능을 활용하여,  
Gradle 캐시 디렉터리를 재사용했을 때 반복 빌드 속도가 얼마나 개선되는지 확인하였다.

Docker는 cache mount를 사용하면 레이어가 다시 실행되더라도 패키지나 의존성 캐시를 유지할 수 있어, 변경되지 않은 항목은 다시 다운로드하지 않아도 된다고 설명한다.

---

#### 실습 결과

#### 캐시 미사용
<img width="663" height="738" alt="스크린샷 2026-03-26 163316" src="https://github.com/user-attachments/assets/7ea8778d-00b5-4711-aa7f-e6562894a5a6" />

<img width="169" height="77" alt="스크린샷 2026-03-26 163256" src="https://github.com/user-attachments/assets/e6c0a813-e35d-4f19-bb74-f7fb77c79eb2" />

- 첫 빌드
  - builder: **28.5s**
  - real: **39.901s**

<img width="649" height="625" alt="스크린샷 2026-03-26 163143" src="https://github.com/user-attachments/assets/630dc851-3c7f-4ed0-bed2-50dbc07381ed" />

<img width="162" height="73" alt="스크린샷 2026-03-26 162945" src="https://github.com/user-attachments/assets/3c0217df-9a58-46e4-82ae-25d4ac16e553" />

- 두 번째 빌드
  - builder: **26.88s**
  - real: **30.770s**


#### 캐시 사용

<img width="661" height="734" alt="스크린샷 2026-03-26 163242" src="https://github.com/user-attachments/assets/1abcb25e-8330-49a1-9789-af7caf1ff5c8" />

<img width="174" height="80" alt="스크린샷 2026-03-26 163212" src="https://github.com/user-attachments/assets/5440a573-56ef-4748-9e5b-89c74500dc44" />

- 첫 빌드
  - builder: **27.6s**
  - real: **31.631s**

<img width="660" height="411" alt="스크린샷 2026-03-26 162815" src="https://github.com/user-attachments/assets/bb76617d-4929-4e19-8cc8-1e42bc28f63a" />

<img width="201" height="87" alt="스크린샷 2026-03-26 162843" src="https://github.com/user-attachments/assets/5db8ab81-3b77-448f-a3aa-2646ac7449bd" />

- 두 번째 빌드
  - builder: **8.5s**
  - real: **11.603s**

---

#### 결과 분석

BuildKit cache mount는 이번 실험에서 가장 뚜렷한 **반복 빌드 최적화 효과**를 보여주었다.

핵심 결과는 다음과 같다.

- 첫 빌드 차이는 크지 않을 수 있음
- 하지만 두 번째 빌드에서 큰 차이가 발생
- builder 시간 기준 **26.88s → 8.5s**
- real 시간 기준 **30.770s → 11.603s**

이를 통해 다음과 같이 정리할 수 있다.

- 일반 Docker 레이어 캐시만으로도 반복 빌드는 어느 정도 빨라질 수 있다.
   - 위 실습에서 캐시를 사용하지 않았음에도 빌드 시간이 줄어든 이유!!
- 그러나 **RUN --mount=type=cache**를 활용하면, **레이어 재실행 상황에서도 빌드 도구 캐시를 유지**할 수 있어 훨씬 큰 성능 개선이 가능하다
- 특히 Gradle, Maven, npm, apt 같은 의존성 기반 빌드 환경에서 cache mount는 실무적으로 매우 유용하다. Docker도 package manager나 language package cache에 cache mount를 사용하는 것을 권장한다.

