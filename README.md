# Redis

## 캐싱 성능 조회
풀테이블 스캔 VS Redis 캐싱

    ✅ 이미 데이터가 충분합니다. (현재: 2000000건)
    1️⃣ DB 조회 소요 시간: 782ms
    2️⃣ 캐시 적용(첫 조회/Miss) 소요 시간: 749ms
    3️⃣ 캐시 적용(재 조회/Hit) 소요 시간: 6ms

인덱스 스캔 VS Redis 캐싱
    
    ✅ 이미 데이터가 충분합니다. (현재: 2000000건)
    1️⃣ DB 조회 소요 시간: 35ms
    2️⃣ 캐시 적용(첫 조회/Miss) 소요 시간: 31ms
    3️⃣ 캐시 적용(재 조회/Hit) 소요 시간: 6ms

##  그래서 얼마나 나아지는가?

서버의 스레드 풀 설정을 200개로 가정했을 때, 각 조회 방식에 따른 이론적 최대 처리량(TPS) 비교

| 구분 | 평균 응답 시간 (Latency) | 처리량 계산식 (Threads / Time) | 예상 처리량 (TPS) | 성능 향상 (vs 풀 스캔) |
| :--- | :--- | :--- | :--- | :--- |
| **DB 풀 스캔** | 782ms | `200 / 0.782` | **약 255** req/sec | - |
| **DB 인덱스 스캔** | 35ms | `200 / 0.035` | **약 5,714** req/sec | 약 22배 ↑ |
| **Redis 캐싱 (Hit)** | **6ms** | `200 / 0.006` | **약 33,333** req/sec | **약 130배 ↑** |

어떤 데이터를 캐싱하고 조회하느냐에 따라 충분히 달라지며 물리적인 요소들을 고려하면 정확하지는 않다.  
하지만 대략적으로 몇번의 동시요청이 발생해야 Redis 캐싱을 고려할만한가? 에 대한 대답이 됐으면 좋겠다.

---
## Spring과 Redis가 통신하는 법
이 문서는 Spring Boot(MVC) 환경에서 Redis 클라이언트인 **Lettuce**를 통해 Redis 서버와 통신하는 메커니즘을 정리한 문서입니다. 특히 **Java NIO**와 **Multiplexing** 기술을 활용하여 한정된 자원으로 높은 처리량을 내는 원리를 중점적으로 다룹니다.

### 1. 전체 아키텍처 및 통신 흐름

Spring Boot 애플리케이션이 Redis와 데이터를 주고받는 과정을 크게 **Tomcat(Servlet) 계층**과 **Redis Client(Lettuce) 계층**으로 나누어 보자

#### 1-1. 웹 요청 수신 (Tomcat/Servlet Layer)

1. **TCP 연결 및 버퍼링**: 클라이언트로부터 HTTP 요청이 들어오면 OS는 3-way handshake를 통해 연결을 맺고, 데이터를 소켓 버퍼(Socket Buffer)에 기록한다.
   2. Socket Buffer : Spring이 바인딩한 Port로 ServerSocket이 handshake 인증이 끝난 http 요청 전용 Socket을 accept 한다. 이 때 이 Socket이 사용하는 독립적인 메모리를 소켓 버퍼라고 하며, 사용자가 보낸 http 요청은 이 버퍼에 바이트로 저장된다.


2. **스레드 할당**: Selector가 Poller에게 소켓 버퍼에 바이트로 http 요청이 저장되면 스레드 풀에 있는 유후 스레드를 해당 요청에 할당시킨다. 이후 해당 스레드가 버퍼를 읽고 HttpServletRequest 객체를 만들어 DispatcherServlet를 지나 우리가 만든 비즈니스 로직에 도착한다. 

#### 1-2. Redis 연결 및 명령 전송 (Spring Data Redis Layer)

1. **RedisTemplate 호출**: 비즈니스 로직 수행 중 Redis 접근이 필요하면 `RedisTemplate`을 호출한다.
2. **Lettuce와 Netty**: 내부적으로 동작하는 **Lettuce** 는 **Netty(비동기 이벤트 기반 네트워크 프레임워크)** 를 기반으로 한다.
3. **Connection Multiplexing**:
* 수백 개의 Servlet 스레드가 동시에 Redis 요청을 보내더라도, Lettuce는 **단 하나의 물리적 TCP 연결** 을 공유하여 Redis 서버로 명령을 전송한다.
* 위에 스레드가 소켓 버퍼에 있는 데이터를 읽고 데이터를 처리하는 과정을 Blocking IO라고 소개했는데, Lettuce의 장점은 해당 IO를 Multiplexing을 통해 Non Blocking IO를 실현한다.


---

### 2. 왜 Lettuce인가? (Jedis vs Lettuce)

| 특성 | Jedis (Old) | Lettuce (Current) |
| --- | --- | --- |
| **연결 방식** | **Connection Pool** 방식 | **Multiplexing** (단일 연결 공유) |
| **스레드 모델** | Blocking I/O | Non-Blocking I/O (Netty) |
| **동시성 처리** | 스레드 당 하나의 독점 연결 필요 | 하나의 연결로 다수 스레드 처리 가능 |
| **문제점** | 트래픽 증가 시 스레드 풀 고갈 및 컨텍스트 스위칭 비용 발생 | 복잡한 비동기 로직 (하지만 Spring Data가 추상화해 줌) |

> **핵심 요약**: Lettuce는 **Event Loop** 기반의 단일 스레드가 Redis와의 통신을 전담하므로, 애플리케이션의 스레드와 소켓 자원을 획기적으로 절약하여 전체 시스템의 **처리량(Throughput)** 을 향상시킴

---

### 3. Deep Dive: Tomcat의 반쪽짜리 비동기처리
#### Tomcat도 Selector를 사용하는데 어째서 버퍼 소켓을 읽을 때 Blocking 한가?
Tomcat은 내부적으로 Java NIO의 Selector를 사용하여 여러 연결을 효율적으로 처리한다.  
하지만 실제 HTTP 요청을 처리하는 단계로 들어가면, 스레드가 소켓 버퍼의 데이터를 읽을 때 InputStream.read() 메서드에서 Blocking이 발생한다.

    /**
     * Reads the next byte of data from the input stream. The value byte is
     * returned as an {@code int} in the range {@code 0} to
     * {@code 255}. If no byte is available because the end of the stream
     * has been reached, the value {@code -1} is returned. This method
     * blocks until input data is available, the end of the stream is detected,
     * or an exception is thrown.
     *
     * <p> A subclass must provide an implementation of this method.
     *
     * @return     the next byte of data, or {@code -1} if the end of the
     *             stream is reached.
     * @throws     IOException  if an I/O error occurs.
     */
    public abstract int read() throws IOException;

#### 비동기 수신과 동기 처리
* Tomcat의 전체적인 처리 과정을 보면 비동기와 동기가 혼재된 구조를 띠고 있다.

* 비동기 수신 (NIO Connector): Selector를 통해 유입되는 HTTP 요청들을 감지하고 스레드에 배치하는 과정까지는 비동기적으로 처리됨

* 동기 처리 (Worker Thread): 배치된 워커 스레드가 실제 비즈니스 로직을 수행할 때, InputStream을 통해 소켓 버퍼를 읽는 순간부터는 해당 스레드가 Blocking 방식으로 동작  

#### 결론
   우리가 흔히 사용하는 Spring MVC 패턴은 Servlet API를 기반으로 하며, 이 API는 요청 처리를 완료할 때까지 스레드를 유지하는 구조를 전제로 한다.

결국 Selector는 비동기로 열려있지만, 내부 로직은 Blocking 방식으로 처리되기 때문에, I/O 대기 시간이 길어질 경우 스레드 모델의 효율성이 저하되는 '반쪽짜리 비동기'의 한계를 갖게 된다.